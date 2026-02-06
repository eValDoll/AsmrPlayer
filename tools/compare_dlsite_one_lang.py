import json
import sys
import time
import urllib.parse
import urllib.request


def http_get(url: str, headers: dict[str, str] | None = None, timeout_sec: int = 60, retries: int = 2) -> str:
    last_err: Exception | None = None
    for i in range(retries + 1):
        try:
            req = urllib.request.Request(url, headers=headers or {}, method="GET")
            with urllib.request.urlopen(req, timeout=timeout_sec) as resp:
                raw = resp.read()
                charset = resp.headers.get_content_charset() or "utf-8"
                return raw.decode(charset, errors="replace")
        except Exception as e:
            last_err = e
            if i < retries:
                time.sleep(1.5 * (i + 1))
                continue
            raise
    raise RuntimeError(str(last_err))


def dlsite_product_info(rj: str) -> dict:
    rj = rj.strip().upper()
    url = "https://www.dlsite.com/maniax/product/info/ajax?" + urllib.parse.urlencode(
        {"product_id": rj, "cdn_cache_min": "1"}
    )
    text = http_get(
        url,
        headers={
            "User-Agent": "Mozilla/5.0",
            "Referer": "https://www.dlsite.com/",
            "Accept": "application/json, text/plain, */*",
            "Accept-Language": "ja-JP,ja;q=0.9,en;q=0.8,zh;q=0.7",
        },
    )
    return json.loads(text)


def pick_work_obj(root: dict, key: str) -> dict | None:
    if key in root and isinstance(root[key], dict):
        return root[key]
    for _, v in root.items():
        if isinstance(v, dict):
            return v
    return None


def dlsite_language_map(root: dict, base_rj: str) -> dict[str, dict]:
    work = pick_work_obj(root, base_rj)
    items = (work or {}).get("dl_count_items") or []
    out: dict[str, dict] = {}
    if not isinstance(items, list):
        return out
    for it in items:
        if not isinstance(it, dict):
            continue
        if (it.get("edition_type") or "").strip() != "language":
            continue
        lang = (it.get("lang") or "").strip()
        workno = (it.get("workno") or "").strip().upper()
        label = (it.get("display_label") or it.get("label") or "").strip()
        order = it.get("display_order") or 0
        if not lang or not workno:
            continue
        out[lang] = {"workno": workno, "label": label, "display_order": int(order) if isinstance(order, (int, float)) else 0}
    return out


def asmr_one_search(keyword: str) -> dict:
    kw = urllib.parse.quote(keyword.strip())
    url = f"https://api.asmr.one/api/search/{kw}?page=1&order=release&sort=desc"
    text = http_get(url, headers={"User-Agent": "Mozilla/5.0", "Accept": "application/json"})
    return json.loads(text)


def asmr_200_search(keyword: str) -> dict:
    kw = urllib.parse.quote(keyword)
    url = (
        f"https://api.asmr-200.com/api/search/{kw}"
        "?order=create_date&sort=desc&page=1&pageSize=20&subtitle=0&includeTranslationWorks=true"
    )
    text = http_get(url, headers={"User-Agent": "Mozilla/5.0", "Accept": "application/json"})
    return json.loads(text)


def asmr_one_tracks(work_id: str) -> list:
    wid = urllib.parse.quote(str(work_id).strip())
    url = f"https://api.asmr.one/api/tracks/{wid}"
    text = http_get(url, headers={"User-Agent": "Mozilla/5.0", "Accept": "application/json"})
    return json.loads(text)


def asmr_200_tracks(work_id: str) -> list:
    wid = urllib.parse.quote(str(work_id).strip())
    url = f"https://api.asmr-200.com/api/tracks/{wid}"
    text = http_get(url, headers={"User-Agent": "Mozilla/5.0", "Accept": "application/json"})
    return json.loads(text)


def flatten_track_titles(tree: list) -> list[str]:
    out: list[str] = []

    def walk(nodes: list, prefix: str) -> None:
        for n in nodes:
            if not isinstance(n, dict):
                continue
            title = (n.get("title") or n.get("name") or n.get("fileName") or "").strip()
            children = n.get("children") or n.get("child") or n.get("items") or n.get("tracks") or []
            path = f"{prefix}/{title}".strip("/")
            url = n.get("mediaDownloadUrl") or n.get("streamUrl") or n.get("mediaStreamUrl") or n.get("mediaUrl") or n.get("url")
            if url and not children:
                out.append(path or title or str(url))
            if isinstance(children, list) and children:
                walk(children, path)

    if isinstance(tree, list):
        walk(tree, "")
    return out


def summarize_asmr_one(keyword: str) -> dict:
    normalized = keyword.strip()
    data = asmr_one_search(normalized)
    works = data.get("works") or []
    if (not isinstance(works, list) or not works) and normalized.upper().startswith("RJ"):
        data = asmr_200_search(f" {normalized}")
        works = data.get("works") or []
    if not isinstance(works, list) or not works:
        return {"keyword": keyword, "hit": False}
    w = works[0] if isinstance(works[0], dict) else None
    if not w:
        return {"keyword": keyword, "hit": False}
    wid = str(w.get("id") or "").strip()
    source_id = str(w.get("source_id") or "").strip()
    title = str(w.get("title") or "").strip()
    tags = [t.get("name") for t in (w.get("tags") or []) if isinstance(t, dict) and t.get("name")]
    tree_titles: list[str] = []
    if wid:
        try:
            try:
                tree = asmr_one_tracks(wid)
            except Exception:
                tree = asmr_200_tracks(wid)
            tree_titles = flatten_track_titles(tree)
        except Exception:
            tree_titles = []
    return {
        "keyword": keyword,
        "hit": True,
        "id": wid,
        "source_id": source_id,
        "title": title,
        "tags": tags,
        "leaf_count": len(tree_titles),
        "leaf_samples": tree_titles[:10],
    }


def main() -> int:
    base_rj = (sys.argv[1] if len(sys.argv) > 1 else "RJ01348345").strip().upper()
    print(f"BASE_RJ={base_rj}")

    base_info = dlsite_product_info(base_rj)
    lang_map = dlsite_language_map(base_info, base_rj)
    print("\nDLsite dl_count_items:")
    for lang in sorted(lang_map.keys(), key=lambda k: (lang_map[k].get("display_order", 0), k)):
        print(f"  {lang}: {lang_map[lang]['workno']} ({lang_map[lang].get('label','')})")

    cn_rj = (lang_map.get("CHI_HANS") or {}).get("workno") or ""
    if cn_rj:
        cn_info = dlsite_product_info(cn_rj)
        base_work = pick_work_obj(base_info, base_rj) or {}
        cn_work = pick_work_obj(cn_info, cn_rj) or {}
        print("\nDLsite key fields:")
        for k in ["work_name", "work_name_masked", "work_image", "dl_count", "price", "rate_average_2dp", "rate_count"]:
            print(f"  {k}:")
            print(f"    base={base_work.get(k)}")
            print(f"    hans={cn_work.get(k)}")
    else:
        print("\nDLsite: no CHI_HANS edition found")

    print("\nASMR ONE search+tracks:")
    s_base = summarize_asmr_one(base_rj)
    print(f"  keyword={base_rj} hit={s_base.get('hit')} id={s_base.get('id')} source_id={s_base.get('source_id')}")
    if s_base.get("hit"):
        print(f"    title={s_base.get('title')}")
        print(f"    leaf_count={s_base.get('leaf_count')} samples={s_base.get('leaf_samples')}")

    if cn_rj:
        s_cn = summarize_asmr_one(cn_rj)
        print(f"  keyword={cn_rj} hit={s_cn.get('hit')} id={s_cn.get('id')} source_id={s_cn.get('source_id')}")
        if s_cn.get("hit"):
            print(f"    title={s_cn.get('title')}")
            print(f"    leaf_count={s_cn.get('leaf_count')} samples={s_cn.get('leaf_samples')}")

        if s_base.get("hit") and s_cn.get("hit"):
            print("\nASMR ONE: base vs hans summary:")
            print(f"  base_source_id={s_base.get('source_id')} hans_source_id={s_cn.get('source_id')}")
            print(f"  base_leaf_count={s_base.get('leaf_count')} hans_leaf_count={s_cn.get('leaf_count')}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
