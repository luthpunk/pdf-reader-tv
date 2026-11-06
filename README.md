# 📺 TV Reader (PDF Reader for Android TV)

Minimalist, distraction-free PDF reader designed specifically for Android TV, your couch, and remote control.

[![Build Android TV PDF Reader APK](https://github.com/luthpunk/pdf-reader-tv/actions/workflows/build-apk.yml/badge.svg)](https://github.com/luthpunk/pdf-reader-tv/actions/workflows/build-apk.yml)
[![GitHub release](https://img.shields.io/github/v/release/luthpunk/pdf-reader-tv?color=white)](https://github.com/luthpunk/pdf-reader-tv/releases/latest)

---

## ✨ Features & Design

- 🖤 **Matte Dark Grey Aesthetic:** Soft, premium `#131417` dark background with 100% crisp pure white typography (`#FFFFFF`).
- 🔘 **Transparent Outlined Pill Buttons:** Ultra-minimalist 34dp buttons with subtle stroke outlines, clean vector line icons, and zero neon bloat.
- 📐 **Square Thin-Frame Document Banners:** Thin 1dp rectangular frames that sit flush with the natural rectangular shape of PDF paper sheets.
- 📄 **Harmonious "All Documents" View:** Uniform line-art PDF document banners for all scanned files to maintain a consistent, cohesive grid.
- 🛋️ **Netflix-Style Horizontal Swimlanes:**
  - **Continue Reading:** Quick-resume row with reading progress bars for active books.
  - **All Documents:** Automatically lists all discovered PDFs from internal storage and USB drives.
- 🔍 **Live Title Search:** Quickly filter your document collection from the TV remote.
- 📖 **Distraction-Free Reading:** All buttons are hidden while reading — only the clean document title and page indicator appear, leaving the screen 100% dedicated to your document.

---

## 🎮 Remote Control Shortcuts

### Main Screen
| Remote Button | Action |
|---|---|
| **D-Pad Left / Right** | Browse documents within a category row |
| **D-Pad Up / Down** | Switch between rows and top action buttons |
| **D-Pad OK / Center** | Open selected document / click button |

### Reading Mode (PDF Viewer)
| Remote Button | Action |
|---|---|
| **D-Pad Left / Page Up** | Turn to previous page |
| **D-Pad Right / Page Down** | Turn to next page |
| **D-Pad OK / Center** | Toggle document title and bottom progress bar visibility |
| **Back Button** | Exit reader directly back to Home screen |

---

## 📦 Download & Installation

Download the latest APK from [Releases](https://github.com/luthpunk/pdf-reader-tv/releases/latest) and install via ADB or USB flash drive:

```bash
adb install -r pdf-reader-tv.apk
```

---

## 🛠️ Automated CI/CD
Every commit to `main` automatically triggers GitHub Actions to build a signed release APK and publish a new tag to GitHub Releases.

## 📄 License
MIT License
