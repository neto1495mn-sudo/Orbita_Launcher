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
- Sidebar: tap the apps icon to drop down your categories, each with an icon picked from its name (games, video, music, tools, fitness, social; any other name gets a plain folder); tap again to fold it up
- Sidebar clock button opens playtime stats
- "Sort by" menu: personal (your own saved arrangement), custom, name, recently installed or most played; the launcher remembers the last one you picked
- Select several apps (selection circles appear) and drag them into a category together
- Long-press an app and drag it onto a category in the sidebar to move it there (the app shrinks and the folder lights up)
- Drag apps around the grid to change their order, like on Android; each category keeps its own order
- Comes with default categories (Games, Media, Social, Tools) already filled with common apps
- "All Apps" shows every installed app except Meta system apps (unless you put one in a category)
- Clock, IP address, Wi-Fi signal and battery in the status bar

### Categories
- Create, rename and delete categories
- Move several apps at once in Edit mode

### Playtime
- Today, this week, this month and all time
- Leaderboard of your most played games
- Indicator for the app currently in use

### Settings
- **Launcher:** edit mode, categories, focus trigger
- **Appearance:** picture source (Evolve collection or Meta store), icon size and background opacity
- **Startup:** start with system, reopen on close, hide Meta Store
- **System:** native Quest settings, device information and IP address
- **Backup and restore:** export and import categories and settings
- **Updates:** check now or automatically; you are only offered a download when a newer version exists
- **Quest utilities:** VR Shell and storage manager (use Shizuku)
- **Applications:** app manager

### App settings shortcut
Long-press an app and tap **App settings** to open its native Android settings screen (permissions, storage, force stop).

### Languages
The app follows the language of the Quest. It is available in English and Portuguese (Brazil). Any other system language falls back to English.

### Reopen on close
When enabled, the launcher opens again when you tap X. During a game it stays quiet and comes back when you leave the game. Turning it on also turns on the focus trigger: with Shizuku running this happens on its own, otherwise the Quest accessibility settings open so you can switch on 'Orbita Launcher hover trigger'.

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

Game cover art is loaded from the [LauncherIcons](https://github.com/JarJarBlinkz/LauncherIcons) repository by JarJarBlinkz. With the Meta store option, pictures come from Meta store data mirrored daily by [MetaMetadata](https://github.com/threethan/MetaMetadata) (threethan); they are saved on the headset and checked again each time it connects to the internet.

