<div align="center">

# 📝 Monolith Notes

### The Pure Apple Notes Experience on Android. Built on Jetpack Compose with Local Markdown Sovereignty.

[![Android](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose_BOM-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Markdown](https://img.shields.io/badge/Storage-Pure_CommonMark_Vault-000000?style=for-the-badge&logo=markdown&logoColor=white)](https://commonmark.org)
[![Obsidian Compatible](https://img.shields.io/badge/Interop-Obsidian_100%25-7C3AED?style=for-the-badge&logo=obsidian&logoColor=white)](https://obsidian.md)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)
[![Author](https://img.shields.io/badge/Crafted_By-Ayush_(@ayush--rdev)-FFD60A?style=for-the-badge&logo=github&logoColor=black)](https://github.com/ayush-rdev)

<br/>

> *"Your thoughts belong to you — not a closed cloud silo, and not an ad-tracking algorithm."*

[Download Latest APK](https://github.com/ayush-rdev/monolith-notes/releases) • [Features](#-the-four-pillars) • [Architecture](#%EF%B8%8F-technology-stack) • [Building](#-building--installation) • [Author](#-creator--author)

</div>

---

## 🌟 The Manifesto

We built **Monolith Notes** because modern note apps have lost their way. They charge subscriptions to view your own ideas, store your intimate thoughts in opaque proprietary clouds, track your every tap, and hold your data hostage when you don't have internet.

**Monolith is different.**
- **File Over App**: Every note is an honest, human-readable CommonMark `.md` file stored directly on your device in `Documents/MonolithNotes/`.
- **Zero Cloud Lock-in**: If this app vanishes tomorrow, your thoughts stay safe. Open them in Obsidian, Logseq, VS Code, or whatever software exists fifty years from now. Plain text is forever.
- **Pure Apple Craftsmanship**: Designed with obsessive attention to the tactile elegance, typography, and spring physics of iOS 17/18, running natively on Android at 120 FPS.

---

## ⚡ The Four Pillars

```
                                  ┌────────────────────────────────┐
                                  │         Monolith Notes         │
                                  └───────────────┬────────────────┘
                                                  │
         ┌────────────────────────┬───────────────┴───────────────┬────────────────────────┐
         │                        │                               │                        │
         ▼                        ▼                               ▼                        ▼
┌──────────────────┐    ┌──────────────────┐            ┌──────────────────┐    ┌──────────────────┐
│  FILE OVER APP   │    │  APPLE UX CRAFT  │            │ BIOMETRIC SAFETY │    │ VAULT HIERARCHY  │
│  Pure .md Vault  │    │  Fluid Springs   │            │ Hardware Lock &  │    │ 1:1 Subfolders & │
│  Zero Lock-in    │    │  30% Parallax    │            │ 30-Day Trash     │    │ Inline Media     │
└──────────────────┘    └──────────────────┘            └──────────────────┘    └──────────────────┘
```

### 1. 🗂️ File Over App (Obsidian Interoperability)
- **Local Vault**: Direct disk I/O to `/sdcard/Documents/MonolithNotes/`.
- **Standard CommonMark**: 100% interoperable with Obsidian desktop and mobile, Logseq, and text editors.
- **Multi-Threaded Disk Pipeline**: Asynchronous background I/O on `Dispatchers.IO` with parallel file loading.
- **Live Search & Tags**: Instant filter by content, title, or `#hashtags`.

### 2. 🎨 Pixel-Perfect Cupertino UI & Motion
- **Apple Typography**: Rendered with official San Francisco font families (SF Pro Display, SF Pro Text, SF Pro Rounded, SF Mono).
- **Fluid UIKit Springs**: Physics-based screen transitions using Apple’s custom `CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)` with a 30% parallax background slide.
- **OLED Inset Grouped Hierarchy**: True black background (`#000000`), Cupertino card slate (`#1C1C1E`), elevated sheets (`#2C2C2E`), and signature yellow accents (`#FFD60A`).
- **Interactive Checklists**: Circular checklist bubbles with spring bounce and auto-continuation on Enter.

### 3. 🔒 Biometric Security & 30-Day Safety Net
- **Hardware-Backed Lock**: Integrated AndroidX `BiometricPrompt` supporting Face Unlock, Fingerprint, and device fallback credentials.
- **Instant Auto-Lock**: Automatically shields notes the moment you leave the app, turn off the screen, or open the app switcher.
- **Recently Deleted Recovery**: Soft-delete safety net storing removed notes in `.trash/` with a 30-day countdown before permanent deletion.

### 4. 📁 Subdirectory Notebooks & Inline Media
- **Folder Navigation**: Subdirectories inside your vault map directly to Apple Notes notebooks (e.g., `Work/`, `Personal/`, `Ideas/`).
- **Inline Image Attachments**: Photos inserted via the camera or gallery are saved to `attachments/` and rendered with pinch/tap-to-expand full-screen dialogs.
- **Home Screen Quick Capture**: High-performance Android App Widget for 1-tap capture of notes and checklists.

---

## 📊 Comparison Against Other Apps

| Feature | Monolith Notes | Apple Notes (iOS) | Google Keep | Obsidian Mobile |
| :--- | :---: | :---: | :---: | :---: |
| **Data Format** | **Local `.md` Markdown** | Proprietary Cloud DB | Cloud Proprietary | Local `.md` Markdown |
| **Cloud Independence** | **100% Offline** | Requires iCloud | Requires Google Sync | 100% Offline |
| **Android Availability** | **Native Android** | ❌ None | Native Android | Hybrid Web/Electron |
| **Typing Latency** | **0ms (Native Compose)** | Native Swift | Native Views | ~30ms (Web DOM) |
| **Apple UIKit Aesthetic** | **Yes (1:1 Inset Grouped)** | Yes | ❌ Material 3 | ❌ Markdown Theme |
| **Biometric Lock** | **Hardware Biometric** | FaceID / TouchID | ❌ None | Community Plugin |
| **Recently Deleted** | **30-Day Recovery** | 30-Day Recovery | 7-Day Bin | ❌ Manual File Trashing |
| **Home Screen Widget** | **Quick Capture** | Smart Widget | Note Grid | Basic Shortcuts |
| **Telemetry & Ads** | **ZERO (100% Free)** | Apple Analytics | Google Analytics | Zero |

---

## 🛠️ Technology Stack

- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (BOM `2024.04.00`)
- **Language**: Kotlin 1.9.22 (Coroutines, StateFlow, Flow)
- **Image Pipeline**: [Coil](https://coil-kt.github.io/coil/) 2.6.0
- **Security**: AndroidX Biometric `1.2.0-alpha05`
- **Typography**: Apple San Francisco Typography suite
- **Target SDK**: Android 14+ (API 34) • Minimum SDK: Android 8.0 (API 26)

---

## 🚀 Building & Installation

### Prerequisites
- JDK 17 (`JAVA_HOME`)
- Android SDK (`ANDROID_HOME`)

### Build Release APK
```bash
# Clone the repository
git clone https://github.com/ayush-rdev/monolith-notes.git
cd monolith-notes

# Build Release APK with ProGuard/R8
./gradlew assembleRelease

# The APK will be generated at:
# app/build/outputs/apk/release/app-release.apk
```

### Run Unit Tests
```bash
./gradlew testReleaseUnitTest
```

---

## 👤 Creator & Author

**Ayush**
- **GitHub**: [@ayush-rdev](https://github.com/ayush-rdev)
- **Repository**: [ayush-rdev/monolith-notes](https://github.com/ayush-rdev/monolith-notes)

If you find Monolith Notes helpful, please give the repository a ⭐️ star on GitHub!

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
Crafted with passion for typography, local-first computing, and engineering discipline.
