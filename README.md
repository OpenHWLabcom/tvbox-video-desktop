# 机顶盒改 · 视频追剧版桌面小电脑 · Video & Office Desktop Mini PC

> 把闲置安卓机顶盒（电视盒子）改造成一台 **专门用来「追剧 / 看视频」+「办公」** 的小电脑。
> Turn an idle Android TV box into a **mini PC for binge-watching video + office work**.

> **两大重点功能 · Two key features**
> 1. 🎬 **看很多视频** —— 浏览器在线看 + 一键装各大视频 App + 投屏 + 电视直播，桌面点开即用。
> 2. 📄 **办公软件** —— 内置 **WPS Office**，新建/编辑/保存 Word、Excel、PPT。
>
> 1. 🎬 **Watch lots of video** — stream in the browser, install major video apps in one tap, cast, live TV.
> 2. 📄 **Office software** — built-in **WPS Office** for Word / Excel / PPT.

适用机型：**任意已 root 的安卓机顶盒**（Android 4.4+，armv7 / arm64）。
实测机型：**魔百盒 HG680-R**（MStar 平台 / Android 4.4.4）。
Tested on: **China Mobile HG680-R** (MStar, Android 4.4.4). Works on any rooted Android TV box.

---

## 🇨🇳 中文

### 两大亮点

#### 🎬 一、看很多视频
- **浏览器看剧**：桌面自带 **Firefox**（桌面版 UA，网页视频/在线站点流畅）；也可看各类在线影视站。
- **一键装视频 App**：桌面「**当贝市场**」→ 搜「腾讯视频 / 爱奇艺 / 优酷 / 哔哩哔哩 / 芒果TV…」安装即用。
- **投屏**：桌面「**当贝投屏**」，手机视频投到大屏。
- **电视直播**：桌面「**电视直播**」。
- 桌面**双击图标**即可打开（鼠标操作最顺）。

#### 📄 二、办公软件
- 内置 **WPS Office 11.4.1**（兼容 Android 4.4 / armv7），可**新建 / 编辑 / 保存** Word、Excel、PPT。
- 桌面「**WPS文档**」图标直达 WPS 主页；也支持打开本地 .doc/.xls/.ppt 文件。
- 配合**中文输入法**（搜狗拼音），中文打字正常。

### 其它功能
- **原生桌面**：开机即进，图标双击打开，可拖动整理。
- **中文输入法**：搜狗拼音（可切中/英）。
- **局域网互传**：手机/电脑浏览器开 `http://<盒子IP>:8890/` 上传下载。
- **接 U 盘**：自动挂载 NTFS / exFAT。
- **存储当内存**：多层 swap（zram + /cache + 存储），缓解小内存卡顿。
- **开机自愈**：桌面/服务挂了自动拉起。

### 环境要求
- 已 **root** 的安卓机顶盒（Android 4.4+，armv7/arm64），能开 **ADB**。
- 电脑已装 `adb`；**不刷机**，随时可还原。
- ⚠️ 存储较紧的盒子（如 `/data` <150MB）安装 WPS 前请先腾空间（清理缓存 / 删无用应用）。

### 安装
```bash
cd install
./一键安装.sh <盒子IP>
```
脚本会：装桌面 App → 系统瘦身 → 内存扩容 → 配置开机自启/兜底 → **下载并安装 WPS Office（办公）**。
详细图文：**[`docs/安装教程.md`](docs/安装教程.md)**

### 操作说明
- **桌面**：鼠标双击图标打开；长按弹菜单；图标可拖动整理。
- **看视频**：桌面「浏览器」/「当贝市场」/「当贝投屏」/「电视直播」。
- **办公**：桌面「WPS文档」→ WPS 主页（新建/打开文档）。
- **传文件**：`http://<盒子IP>:8890/`。

---

## 🇬🇧 English

### Feature 1 — Watch lots of video
- **Stream in the browser** (Firefox, desktop UA) or install major video apps in one tap via **Dangbei Market** (Tencent Video / iQIYI / Youku / Bilibili / Mango TV …).
- **Cast** from your phone (Dangbei Cast) and watch **live TV**.

### Feature 2 — Office software
- Built-in **WPS Office 11.4.1** (Android 4.4 / armv7) — create, edit and save **Word / Excel / PPT**.
- Desktop shortcut **"WPS文档"** opens WPS directly.

### Also
Native desktop (auto-boots in), Chinese IME, LAN file transfer (`:8890`), USB drive (NTFS/exFAT),
multi-layer swap, self-healing autostart.

### Requirements & Install
Rooted Android TV box (Android 4.4+, armv7/arm64) with ADB; `adb` on your PC; **no flashing**.
```bash
cd install
./一键安装.sh <box-ip>
```

---

## 与「机顶盒改电脑主机」通用版的差异 · vs. the generic desktop

| | 通用版(desktop-pc) | **视频追剧版(本仓库)** |
|---|---|---|
| 定位 | 通用轻办公小主机 | **追剧 / 看视频 + 办公** 为主 |
| 办公 | Firefox + 可选装 | **预装 WPS Office** + 快捷图标 |
| 视频 | 浏览器/自装 | 浏览器 + 当贝市场/投屏/直播，**突出「看很多视频」** |
| 安装 | 装桌面 + 瘦身扩容 | 同上 **+ 自动装 WPS** |

> 同一套桌面/瘦身/扩容/开机自愈底座，本版把「**看视频 + 办公**」两个场景打磨成开箱即用。

---

## 相关链接 · Links
- 官网：https://www.openhwlab.com/
- 通用版桌面：https://github.com/OpenHWLabcom/tvbox-desktop-pc
- 其它项目：游戏机 / 监控录像 / 打印机服务器 / 下载机NAS / 去广告DNS

—— OpenHW Lab · hi@openhwlab.com
