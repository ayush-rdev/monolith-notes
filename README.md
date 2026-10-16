<div align="center">

# 📝 Monolith Notes

### A Tactile, Distraction-Free Markdown Journal for Android with Pure Local-First File Sovereignty.

[![Android](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose_BOM-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Markdown](https://img.shields.io/badge/Storage-Pure_CommonMark_Vault-000000?style=for-the-badge&logo=markdown&logoColor=white)](https://commonmark.org)
[![Obsidian Compatible](https://img.shields.io/badge/Interop-Obsidian_100%25-7C3AED?style=for-the-badge&logo=obsidian&logoColor=white)](https://obsidian.md)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)
[![Author](https://img.shields.io/badge/Crafted_By-Ayush_(@ayush--rdev)-FFD60A?style=for-the-badge&logo=github&logoColor=black)](https://github.com/ayush-rdev)

<br/>

> *"The world moves too fast, but what you write down... that stays."*

[Download Latest APK](https://github.com/ayush-rdev/monolith-notes/releases) • [Features](#-the-four-pillars) • [Architecture](#%EF%B8%8F-technology-stack) • [Building](#-building--installation) • [Author](#-creator--author)

</div>

---

## 🌟 The Manifesto

We built **Monolith Notes** because modern note-taking apps have lost their way. They demand monthly subscriptions just to read your own words, store your intimate memories on distant corporate servers, track your every keystroke, and lock you out when you don't have internet.

**Monolith is different.**
- **File Over App**: Every note is an honest, plain-text CommonMark `.md` file stored directly on your phone in `Documents/MonolithNotes/`.
- **Zero Cloud Lock-in**: If this app vanishes tomorrow, your words stay safe. Open them in Obsidian, Logseq, VS Code, or whatever tools exist decades from now. Plain text is forever.
- **Tactile Craftsmanship**: Built natively with Jetpack Compose, physics-based motion, OLED true-black hierarchy, and instant 0ms typing response.

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
│  FILE OVER APP   │    │  TACTILE CRAFT   │            │ BIOMETRIC SAFETY │    │ VAULT HIERARCHY  │
│  Pure .md Vault  │    │  Fluid Springs   │            │ Hardware Lock &  │    │ Subdirectories & │
│  Zero Lock-in    │    │  OLED Cards      │            │ 30-Day Trash     │    │ Inline Media     │
└──────────────────┘    └──────────────────┘            └──────────────────┘    └──────────────────┘
```

### 1. 🗂️ File Over App (Obsidian Interoperability)
- **Local Vault Storage**: Stored directly at `/sdcard/Documents/MonolithNotes/`.
- **Standard CommonMark**: 100% interoperable with Obsidian desktop and mobile, Logseq, and standard text editors.
- **Multi-Threaded Disk Pipeline**: Asynchronous background I/O on `Dispatchers.IO` with parallel file parsing.
- **Live Search & Tags**: Instant filter by content, title, or `#hashtags`.

### 2. 🎨 Tactile Motion & Typography
- **Clean Inset Cards**: OLED true-black background (`#000000`), card slate (`#1C1C1E`), elevated sheets (`#2C2C2E`), and warm amber accents (`#FFD60A`).
- **Fluid Physics Springs**: Natural gesture motion using custom cubic bezier curves with 30% parallax depth.
- **Interactive Checklists**: Responsive checklist bubbles with subtle spring bounce and automatic continuation on Enter.
- **Zero Latency**: Native Jetpack Compose text engine synchronized with 120Hz display refresh rates.

### 3. 🔒 Biometric Security & 30-Day Recovery
- **Hardware-Backed Lock**: Integrated AndroidX `BiometricPrompt` supporting Face Unlock, Fingerprint, and device fallback credentials.
- **Instant Auto-Shield**: Automatically locks and conceals notes the moment you switch apps or turn off your screen.
- **Recently Deleted Safety Net**: Soft-delete safety net storing removed notes in `.trash/` with a 30-day countdown before permanent deletion.

### 4. 📁 Subdirectory Notebooks & Inline Media
- **Folder Navigation**: Subdirectories inside your vault map directly to discrete notebooks (e.g., `Work/`, `Personal/`, `Journal/`).
- **Inline Image Attachments**: Photos inserted via the camera or gallery are saved locally to `attachments/` and rendered with pinch/tap-to-expand full-screen dialogs.
- **Home Screen Quick Capture**: High-performance Android App Widget for 1-tap capture of thoughts and checklists.

---

## 📊 Comparison Against Other Apps

| Feature | Monolith Notes | Apple Notes | Google Keep | Obsidian Mobile |
| :--- | :---: | :---: | :---: | :---: |
| **Data Sovereignty** | **Local `.md` Files** | Proprietary Cloud DB | Cloud Proprietary | Local `.md` Files |
| **Cloud Independence** | **100% Offline** | Requires iCloud | Requires Google Sync | 100% Offline |
| **Typing Latency** | **0ms (Native Compose)** | Native Swift | Native Views | ~30ms (Web DOM) |
| **Tactile Motion & UI** | **Bespoke Inset Grouped** | Inset Grouped | Material Design | Markdown Theme |
| **Biometric Lock** | **Hardware Biometric** | FaceID / TouchID | ❌ None | Community Plugin |
| **Recently Deleted** | **30-Day Recovery** | 30-Day Recovery | 7-Day Bin | ❌ Manual File Trashing |
| **Home Screen Widget** | **Quick Capture** | Smart Widget | Note Grid | Basic Shortcuts |
| **Telemetry & Ads** | **ZERO (100% Free)** | Telemetry | Google Analytics | Zero |

---

## 🛠️ Technology Stack

- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (BOM `2024.04.00`)
- **Language**: Kotlin 1.9.22 (Coroutines, StateFlow, Flow)
- **Image Pipeline**: [Coil](https://coil-kt.github.io/coil/) 2.6.0
- **Security**: AndroidX Biometric `1.2.0-alpha05`
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
