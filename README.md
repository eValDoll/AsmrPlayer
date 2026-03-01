# Eara (Android) 🎧

> **THIS REPOSITORY AND ITS CONTENT WERE GENERATED 100% BY AI.**

**中文宣传页 / Landing Page (ZH)**: [docs/landing_zh.md](docs/landing_zh.md)

## 中文简介

**Eara（Android）** 是一款面向 ASMR 内容的本地播放器：以“专辑/曲目”的库管理体验为核心，提供同步歌词、后台下载、耳机向音效（均衡器/声道平衡/空间化）、左右声道频谱可视化与深度主题定制等“播放器级增强能力”。

## 📖 Overview

**Eara (Android)** is a modern, feature-rich audio player specifically designed for ASMR content, built with **Jetpack Compose** and **Media3**. It offers a premium local library experience combined with powerful app-level features like playlist management, synchronized lyrics, background downloads, and deep customization.

*This repository is provided as-is and may be incomplete or experimental.*

---

## ✨ Features

- 🎧 高保真播放：基于 Media3（ExoPlayer）提供稳定流畅的本地/流媒体播放
- 🎨 现代化界面：Jetpack Compose + Material 3，沉浸动效与自适应布局
- 📚 本地媒体库：按专辑/曲目浏览，网格/列表视图可切换，支持快速筛选与搜索
- 📑 列表与收藏：播放列表管理、系统收藏视图、最近播放与快捷操作
- 🎤 同步歌词：LRC/VTT/SRT 自动解析与时间同步，支持悬浮歌词
- 🎛️ 耳机向音效：均衡器、混响、增益、虚拟化、左右声道平衡与空间化
- 📊 双声道频谱：为双耳音频设计的 L/R 频谱与可视化
- ✂️ 片段标记与循环：进度条上可标注切片，AB 循环、拖拽精调与片段预览
- 📥 后台下载：下载队列与任务管理，结合本地数据库实现离线可用
- 🌐 在线来源整合：DLsite（含 Play 已购）、asmr.one 等内容入口
- 🎞️ 视频支持：支持常见视频格式与 m3u8 资源的预览/播放
- ⏰ 定时与通知：睡眠定时、系统通知控制与后台播放

---

## 📦 Downloads

- Download from **GitHub Releases** (tag `v*`, latest is `v0.2.1`).

---

## 🔐 Permissions (Brief)

- **Media / Storage access**: scan and play your local audio files.
- **Notifications**: playback controls and foreground service notification.
- **Overlay (optional)**: required only when enabling floating lyrics.

---

## 🌐 Content Sources (Built-in)

- **DLsite (scraping)**
- **DLsite Play library**
- **asmr.one API**

Use responsibly and comply with the laws and terms of service that apply to you.

---

## 📱 App Preview

### 📚 Immersive Library
Explore your audio collection with our versatile library views. Choose between a visual-rich grid layout or a detailed list view to suit your browsing style.

| **Album Grid** | **Album List** |
|:---:|:---:|
| <img src="example_screen/main_screen_album-card.png" width="50%"/> | <img src="example_screen/main_screen_album-list.png" width="50%"/> |

### 🔍 Search & Navigation
Find exactly what you're looking for. The track list provides quick access to your files, while the search screen helps you locate content instantly.

| **Track List** | **Smart Search** |
|:---:|:---:|
| <img src="example_screen/main_screen_track-list.png" width="50%"/> | <img src="example_screen/search_screen.png" width="50%"/> |

### 🎵 Player & Focus Mode
Immerse yourself in the sound (or video). The player interface features a calming visualizer, landscape mode for dedicated listening, synchronized lyrics, and **MP4 video playback** support.

| **Now Playing** | **Landscape Mode** |
|:---:|:---:|
| <img src="example_screen/now_playing_screen.png" width="70%"/> | <img src="example_screen/now_playing_landscape-mode.png" width="70%"/> |

| **Lyrics View** | **Video Playback** |
|:---:|:---:|
| <img src="example_screen/lyric_screen.png" width="50%"/> | <img src="example_screen/now_playing_mp4-supported.png" width="50%"/> |

### ⚙️ Details & Settings
Deep dive into album metadata or customize the app to your liking. The settings screen puts you in control of the experience.

| **Album Details** | **Settings** |
|:---:|:---:|
| <img src="example_screen/album_detail_DL-tab.png" width="50%"/> | <img src="example_screen/settings_screen.png" width="50%"/> |

---

## 🧭 使用指南

- 首次使用（本地库）
  - 进入“本地库”，点击“添加文件夹”，选择你的专辑根目录（支持文档树/外置存储）
  - 扫描完成后，将自动生成专辑/曲目视图，可按标签、分组或关键字筛选
- 播放与歌词
  - 在专辑详情或曲目列表点击播放；横屏进入专注模式
  - 歌词页支持 LRC/VTT/SRT，同步显示；可在设置中开启“悬浮歌词”，需授予悬浮窗权限
- 音效面板
  - 在“正在播放”页打开音效面板：均衡器、混响、增益、声道平衡与虚拟化等一键启用
- 片段标记与循环
  - 在进度条上长按或进入“切片模式”标记片段；支持拖拽微调与 AB 循环
  - 可对当前曲目管理多个片段并快速预览
- 下载与来源
  - 在搜索页或专辑详情（DLsite 页签）查看资源；登录后可使用已购播放/下载
  - 下载任务在“下载”页查看进度与状态，支持后台进行
- 列表与收藏
  - 新建/管理播放列表，将曲目加入收藏；支持系统收藏与分组管理

---

## 🛠️ 简要技术说明

Kotlin + Jetpack Compose + Media3。更多依赖与版本见 [app/build.gradle.kts](app/build.gradle.kts)。

---

## 🚀 本地编译与安装（含 Profile）

### Prerequisites

- **Android Studio**: Recent stable version recommended.
- **JDK 17**: Required by Android Gradle Plugin 8.x.
- **Android SDK**:
  - `compileSdk` / `targetSdk`: **34**
  - `minSdk`: **24**

### 🏃 打开与运行

1.  **Clone/Open** this project folder in Android Studio.
2.  Wait for **Gradle Sync** to complete.
3.  Select the `app` configuration and hit **Run** ▶️ on your device or emulator.

### 📦 命令行构建与安装

```bash
./gradlew :app:installDebug         # 安装 Debug 到连接设备
./gradlew :app:assembleRelease      # 仅构建 Release APK
```

### ⚡ Baseline/Startup Profile

- 已包含的文件：
  - Baseline Profile: [app/src/main/baseline-prof.txt](app/src/main/baseline-prof.txt)
  - Startup Profile: [app/src/main/startup-prof.txt](app/src/main/startup-prof.txt)
- 重新采集 Baseline Profile（可选，需要真机/模拟器已连接）：

```bash
./gradlew :app:assembleBenchmark
./gradlew :baselineprofile:connectedBenchmarkAndroidTest
./gradlew :app:assembleRelease
```

Profile 采集完成后会参与后续 Release 构建以优化启动与滚动性能。

### 📂 构建产物位置

To keep your project root clean, build outputs are redirected:
- **Default**: `<repo>/.build_asmr_player_android/`
- **Override**: Set `TRAE_BUILD_ROOT` environment variable.

---

## 📝 Configuration Notes

- `local.properties` is **excluded** from version control (auto-generated by Android Studio).
- ⚠️ **Security**: Never commit keystores (`*.jks`, `*.keystore`) or signing secrets.
 - **Networking headers**: This project separates image-loading headers from API networking to avoid cross-impact.

---

## ⚖️ Disclaimer

- This project is **not an official product** and is not affiliated with any platform, store, or brand referenced.
- The code may contain **bugs, incomplete implementations, or security issues**. Please review carefully before production use.
- You are responsible for complying with all applicable laws and terms of service for any third-party services accessed.
- **No warranties provided.** Use at your own risk.

---

## 🤖 AI Generation Notice

This repository (including documentation and code changes) is marked as **100% AI-generated**. Human review is strongly recommended.
