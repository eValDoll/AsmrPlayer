# Eara（Android）

<p align="center">
  <img src="../asmr_logo.svg" width="160" alt="Eara logo" />
</p>

<p align="center">
  <strong>为 ASMR 而生的本地播放器：专辑管理、沉浸播放、耳机向音效与内容检索下载，一站式完成。</strong>
</p>

---

## 一句话介绍

Eara 是一款面向 ASMR 内容的 Android 播放器：既拥有顺滑的本地媒体库体验，又提供“播放器级增强能力”，包括同步歌词、后台下载、左右声道可视化与声道控制、深度主题定制等。

## 亮点功能

- **本地库体验**：以“专辑 / 曲目”为核心组织方式，浏览、筛选与搜索更高效
- **沉浸式播放**：播放器内置歌词页、横屏专注模式、封面背景氛围与频谱可视化
- **耳机向音效**：均衡器、混响、增益、Virtualizer、左右声道平衡与 3D 立体环绕（耳机更佳）
- **内容检索聚合**：支持从多个来源检索内容并快速进入播放/整理流程
- **后台下载与离线**：WorkManager 管理下载任务，结合本地数据库实现离线浏览与播放
- **悬浮歌词**：在系统悬浮窗中显示歌词（需要悬浮窗权限），适合边做事边听
- **视频支持**：支持常见视频格式与 m3u8 资源的预览/播放（用于补充内容呈现）

## 截图预览

| 专辑网格 | 专辑列表 |
|:---:|:---:|
| <img src="../example_screen/main_screen_album-card.png" width="95%"/> | <img src="../example_screen/main_screen_album-list.png" width="95%"/> |

| 曲目列表 | 搜索聚合 |
|:---:|:---:|
| <img src="../example_screen/main_screen_track-list.png" width="95%"/> | <img src="../example_screen/search_screen.png" width="95%"/> |

| 播放器 | 横屏专注 |
|:---:|:---:|
| <img src="../example_screen/now_playing_screen.png" width="95%"/> | <img src="../example_screen/now_playing_landscape-mode.png" width="95%"/> |

| 歌词页 | 视频播放 |
|:---:|:---:|
| <img src="../example_screen/lyric_screen.png" width="95%"/> | <img src="../example_screen/now_playing_mp4-supported.png" width="95%"/> |

| 专辑详情 | 设置 |
|:---:|:---:|
| <img src="../example_screen/album_detail_DL-tab.png" width="95%"/> | <img src="../example_screen/settings_screen.png" width="95%"/> |

## 适合谁

- 有较大本地 ASMR 音频库，希望以“专辑”为中心统一整理与播放
- 重视左右声道细节、喜欢频谱可视化、需要声道平衡/空间化效果的耳机党
- 想要歌词沉浸（甚至跨应用悬浮显示）的用户
- 希望把“检索 → 下载 → 入库 → 离线播放”串成一个工作流的整理型用户

## 下载体验

- 从 **GitHub Releases** 下载（tag `v*`，最新为 `v0.1.3`）
- 或在本地自行构建：

```bash
./gradlew :app:assembleDebug
```

## 权限说明（简要）

- **媒体/存储访问**：用于扫描并播放你的本地音频文件
- **通知权限**：用于播放控制与播放服务的前台通知
- **悬浮窗权限（可选）**：仅在开启“悬浮歌词”功能时需要

## 免责声明

本项目为非官方作品，不隶属于任何第三方平台或品牌。请遵守所在地区法律法规与第三方服务条款，自行承担使用风险。
