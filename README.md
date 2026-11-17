# Handoff: use a Galaxy Z Flip 5 from its cover screen

Android app to use a **folded Galaxy Z Flip 5** from its cover screen (Flex Window): it opens the
One UI home screen and any app on the cover screen, with a navigation bar, gestures, Recents and
a wallpaper.

> **Main purpose:** it was built to give a **second life** to a Z Flip 5 whose inner screen had
> become unusable, and to **recover important data** from the phone without having to repair it.

## Features

| Component | What it does |
|---|---|
| **Launcher** (`LauncherActivity`) | Grid of all apps with a search box. Opens each app on the display the launcher is on; if the app was open on the inner screen, it is brought to the cover screen. Buttons for **One UI**, **wallpaper** and **move to inner / cover**. |
| **Accessibility service** (`CoverRedirectService`) | While folded: (1) if an app opens on the inner display (e.g. from the One UI home screen), it is moved to the cover display; (2) draws the navigation bar and gestures on the cover screen. Starts automatically at boot. |
| **Recents** | The three-line button opens One UI's own **"Recent apps"** view on the cover screen. If that is not available, it falls back to an internal list of recently used apps. |
| **Wallpaper** (`WallpaperActivity`) | Sets an image as the cover screen wallpaper (shown behind One UI). From the launcher's wallpaper button, via **Share** from the Gallery, or by copying the inner screen wallpaper. |
| **Widget** (`CoverWidgetProvider`) | Flex Window widget with two buttons, **One UI** and **Apps**, to get in without ADB. Added from the cover screen widget settings (which ask you to open the phone). |

### Navigation bar (cover screen)

`⋮` drag · `|||` Recents · `▢` Home (One UI) · `<` Back · `⠿` All apps

- Same order and style as One UI: thin vector icons, dark with a light halo, no background.
- Placed at the bottom-left, outside the camera module: the app reads the display cutout reported
  by the system. It can be dragged by `⋮` and remembers its position.
- Gestures: swipe in from the left or right edge = **Back**; swipe up from the bottom edge =
  **Home**.
- Only shown while folded with an app in the foreground. Hidden over Samsung's cover home screen
  and cover lock screen, so it cannot be used to bypass the lock.

## Installation

Requires ADB (USB debugging enabled).

```sh
adb install -r app-release.apk

# Enable the accessibility service (keeps any services already enabled)
SVC=com.handoff.cover/com.handoff.cover.CoverRedirectService
CUR=$(adb shell settings get secure enabled_accessibility_services)
[ "$CUR" = "null" ] && CUR="" || CUR="$CUR:"
adb shell settings put secure enabled_accessibility_services "$CUR$SVC"
adb shell settings put secure accessibility_enabled 1

# Optional: allow "use the same wallpaper as the inner screen"
adb shell appops set com.handoff.cover MANAGE_EXTERNAL_STORAGE allow

# Open the launcher on the cover screen (with the phone folded)
adb shell am start --display 1 -n com.handoff.cover/.LauncherActivity
```

Android does not allow enabling, from Settings, the accessibility service of an app installed
outside an app store ("restricted setting"), which is why it is enabled with ADB. If the app data
is cleared, it has to be enabled again.

## Building

With Android Studio (open the folder) or from a terminal:

```sh
export ANDROID_HOME=/path/to/Android/Sdk      # or create local.properties with sdk.dir=...
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk (signed with the debug key)
```

Requirements: JDK 17 or newer, Android Gradle Plugin 9.4.1, Gradle 9.8.2, SDK `android-37`,
`minSdk` 30. No external libraries: only Java and the Android API.

The user interface text is in Spanish (`app/src/main/res/values/strings.xml`).

## Limitations

- **It is not a mirror of the inner screen.** With the phone open, the cover screen is off. The
  dual-screen mode (the one the camera uses) is cancelled as soon as the app requesting it leaves
  the foreground. With the phone folded, the inner screen is off. That is why apps run directly
  on the cover screen.
- **One UI icon size:** on the cover screen One UI uses a fixed icon size; it does not change with
  the home screen grid, the "app size" setting or the display density.
- Apps are shown at 748×720; some are not designed for that size.
- Tested on a Galaxy Z Flip 5 (SM-F731B) with Android 16 / One UI 8.5. Display ids: inner = 0,
  cover = 1. The Samsung wallpaper flags (4 = main, 16 = cover) and the One UI components used may
  change in other versions.

## License

MIT, see [LICENSE](LICENSE).
