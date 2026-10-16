<div align="center">

# 📝 Monolith Notes

### The Pure Apple Notes Experience on Android. Built on Jetpack Compose with Local Markdown Sovereignty.

[![Android](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose_BOM-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Markdown](https://img.shields.io/badge/Storage-Pure_CommonMark_Vault-000000?style=for-the-badge&logo=markdown&logoColor=white)](https://commonmark.org)
[![Obsidian Compatible](https://img.shields.io/badge/Interop-Obsidian_100%25-7C3AED?style=for-the-badge&logo=obsidian&logoColor=white)](https://obsidian.md)
[![Privacy](https://img.shields.io/badge/Telemetry-ZERO_Cloud_Lock--in-10B981?style=for-the-badge&logo=signal&logoColor=white)](https://en.wikipedia.org/wiki/Local-first_software)

<br/>

> *"Your thoughts belong to you — not a closed cloud silo, and not an ad-tracking algorithm."*

</div>

---

## 🌟 Overview

**Monolith Notes** brings the iconic tactile elegance of **Apple Notes (iOS 17/18)** to Android flagships, powered by the architectural freedom of **Obsidian local vaults**.

Every single note created in Monolith is an honest, plain-text `.md` CommonMark file stored directly in your device’s `Documents/MonolithNotes/` directory. No proprietary binary databases, no accounts, and zero cloud lock-in. Plug your phone into your PC, or open your vault in Obsidian, Logseq, or VS Code — everything syncs and renders effortlessly.

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

### 1. 🗂️ File Over App (True Data Sovereignty)
- **Local Vault Storage**: Stored at `/sdcard/Documents/MonolithNotes/`.
- **Obsidian Interoperability**: Compatible out-of-the-box with Obsidian folders, tags (`#ideas`, `#todo`), and `attachments/`.
- **Pure Markdown**: Notes maintain human-readable CommonMark standard syntax with YAML-safe frontmatter.

### 2. 🎨 Pixel-Perfect Cupertino UI & Motion
- **Apple Typography**: Rendered with official SF Pro Display, SF Pro Text, SF Pro Rounded, and SF Mono font families.
- **Fluid UIKit Springs**: Physics-based screen transitions using Apple’s custom `CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)` with a 30% parallax background slide.
- **OLED Inset Grouped Hierarchy**: True black background (`#000000`), Cupertino card slate (`#1C1C1E`), elevated sheets (`#2C2C2E`), and signature yellow accents (`#FFD60A`).
- **Interactive Checklists**: Circular checklist bubbles with spring-animated bounce and auto-continuation on Enter.

### 3. 🔒 Biometric Security & 30-Day Safety Net
- **Hardware-Backed Lock**: Integrated AndroidX `BiometricPrompt` supporting Face Unlock, Fingerprint, and device fallback credentials.
- **Auto-Lock on Background**: Automatically conceals notes when navigating away or switching apps.
- **Recently Deleted Recovery**: Soft-delete safety net storing removed notes in `.trash/` with a 30-day countdown before permanent cleanup.

### 4. 📁 Subdirectory Notebooks & Inline Media
- **Folder Navigation**: Subdirectories inside your vault map directly to Apple Notes notebooks (e.g., `Work/`, `Personal/`, `Projects/`).
- **Inline Image Attachments**: Photos inserted via the camera or gallery are saved to `attachments/` and rendered natively with tap-to-expand full-screen dialogs.
- **Home Screen Quick Capture**: High-performance Android App Widget for 1-tap capture of notes and checklists.

---

## 📊 Market Comparison

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

---

## 🛠️ Technology Stack

- **UI Toolkit**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (BOM `2024.04.00`)
- **Language**: Kotlin 1.9.22 (Coroutines, StateFlow, Flow)
- **Image Pipeline**: [Coil](https://coil-kt.github.io/coil/) 2.6.0
- **Security**: AndroidX Biometric `1.2.0-alpha05`
- **Typography**: Apple San Francisco Typography suite
- **Target Architecture**: Android 8.0 (API 26) through Android 14+ (API 34)

---

## 🚀 Building & Installation

### Prerequisites
- JDK 17 (`JAVA_HOME`)
- Android SDK (`ANDROID_HOME`)

### Build Release APK
```bash
# Clone the repository
git clone https://github.com/your-username/monolith-notes.git
cd monolith-notes

# Build Release APK with ProGuard/R8
./gradlew assembleRelease

# The APK will be generated at:
# app/build/outputs/apk/release/app-release.apk
```

---

## 📄 License
Crafted with passion for typography, local-first computing, and engineering discipline. Released under the **MIT License**.
