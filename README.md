# Orbita Launcher

**Based on [Evolve Launcher v2](https://github.com/JarJarBlinkz/Evolve_Launcher_v2) by JarJarBlinkz.**

A custom home launcher for Meta Quest with a look inspired by the Quest library: a rounded gray panel, a slim sidebar and a grid of app thumbnails.

Organize your apps, track your playtime and set up the launcher your way.

---

## Features

### Launcher
- App grid with adjustable icon size
- Search by app name or package
- Category menu ("All Apps" and your own categories)
- Hide apps you do not want to see
- Quick settings panel (volume and more)
- Clock, IP address, Wi-Fi signal and battery in the status bar

### Categories
- Create, rename and delete categories
- Move several apps at once in Edit mode
- Optional category badge on app icons

### Playtime
- Today, this week, this month and all time
- Leaderboard of your most played games
- Indicator for the app currently in use

### Settings
- **Launcher:** edit mode, categories, focus trigger
- **Appearance:** icon size and background opacity
- **Startup:** start with system, reopen on close, hide Meta Store
- **System:** native Quest settings, device information and IP address
- **Backup and restore:** export and import categories and settings
- **Quest utilities:** classic Quest interface, VR Shell and storage manager (use Shizuku)
- **Applications:** bundled apps and app manager

### Languages
The app follows the language of the Quest. It is available in English and Portuguese (Brazil). Any other system language falls back to English.

### Reopen on close
When enabled, the launcher opens again when you tap X. During a game it stays quiet and comes back when you leave the game. It requires the accessibility trigger to be enabled in the Quest accessibility settings.

---

## Installation

### Requirements
- Meta Quest 1, 2, 3 or Pro
- Developer mode enabled
- SideQuest, ADB or any other APK installer

### Steps
1. Download `Orbita_Launcher.apk` from the Releases page.
2. Install it with ADB or any APK installer:
   - open the installer on your computer
   - drag and drop the APK
   - click install
3. Open Orbita. The usage access permission is requested from inside the app Settings.

---

## Credits

Orbita is derived from **Evolve Launcher v2** by [JarJarBlinkz](https://github.com/JarJarBlinkz/Evolve_Launcher_v2). The interface was fully redesigned, the theme system and favorites were removed, and the screens were translated to Portuguese.

Game cover art is loaded from the [LauncherIcons](https://github.com/JarJarBlinkz/LauncherIcons) repository by JarJarBlinkz.

---

## Updates

Orbita checks the latest release of this repository on GitHub. When a newer version is available, Settings > Updates shows what is new and lets you download and install it.

- Tap **Check for updates** to check right away.
- Turn on **Check automatically** to let Orbita check by itself from time to time.
- Android asks you to allow installing apps from this source the first time.

To publish an update: create a new Release with a version tag (for example `v1.0.1`, higher than the installed one) and attach the APK. Always sign the APK with the same key, otherwise Android refuses to install it over the old version.
