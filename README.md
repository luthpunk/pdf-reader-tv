# 📺 TV Reader (PDF Reader for Android TV)

Minimalist, distraction-free PDF e-reader built specifically for your couch and remote control.

[![Build Android TV PDF Reader APK](https://github.com/luthpunk/pdf-reader-tv/actions/workflows/build-apk.yml/badge.svg)](https://github.com/luthpunk/pdf-reader-tv/actions/workflows/build-apk.yml)
[![GitHub release](https://img.shields.io/github/v/release/luthpunk/pdf-reader-tv?color=38BDF8)](https://github.com/luthpunk/pdf-reader-tv/releases/latest)

---

## 🛋️ Why TV Reader?

Most PDF viewers are designed for phones and tablets — requiring touch gestures, containing tiny buttons you can't reach with a remote, or failing completely on Android TV.

**TV Reader** was built from scratch for the remote control:
- **100% D-Pad Navigation:** Every screen, button, and menu works smoothly with TV remote controls.
- **Continuous Scroll & Page-by-Page Modes:** Switch seamlessly between horizontal book flipping and smooth vertical scrolling.
- **Focus Mode:** Press `[OK]` to hide all toolbars for an immersive, distraction-free reading experience on your TV screen.
- **TOC & Bookmarks Side Drawer:** Quick chapter jump and multi-page bookmarking.
- **Home Screen & Continue Reading:** Resume your last read document instantly with a progress bar and auto-generated cover art.
- **Internal Storage & USB OTG:** Direct file browser to read documents from internal storage or plugged-in USB flash drives.
- **"Open With" Support:** Register as the default PDF viewer from any Android TV file manager.

---

## 🎮 Remote Control Shortcuts

| Remote Button | Action in Reader |
|---|---|
| **D-Pad Left / Right** | Turn Page Backward / Forward (in Page-by-Page mode) |
| **D-Pad Up / Down** | Smooth vertical scroll (in Continuous mode) |
| **D-Pad OK / Center** | **Toggle Focus Mode** (hides / shows toolbars) |
| **Menu / Info Button** | Open Table of Contents & Bookmarks Side Drawer |
| **Back Button** | Exit Focus Mode / Close Drawer / Back to Library |

---

## 🛠️ Automated CI/CD Build

Every push to `main` automatically triggers the GitHub Actions workflow to:
1. Compile the project with Android Gradle Plugin & JDK 17.
2. Produce signed `pdf-reader-tv.apk`.
3. Publish a new **GitHub Release** with direct APK download.

---

## 📦 Installation

Download the latest APK from [Releases](https://github.com/luthpunk/pdf-reader-tv/releases/latest) and install it on your Android TV via ADB or a USB drive:

```bash
adb install -r pdf-reader-tv.apk
```

---

## 📄 License
MIT License
