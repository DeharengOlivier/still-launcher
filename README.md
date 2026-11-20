# Still

A quiet, text-only Android launcher. No ads, no account, no paid tier, no tracking.

## Features

- **Home screen made of text.** As many apps as you want, plus named groups (tap a group to open it).
- **Two pages.** Home on the left; swipe right-to-left to reach the full app list on the right, and back.
- **Search that only searches.** Typing filters the list; nothing launches until you tap an app.
- **Fixed clock.** The clock stays put at the top; the home list scrolls under it only when it overflows the screen.
- **Discreet corners.** Phone bottom-left, camera bottom-right.
- **Separate sizes** for the clock and for the app names.
- **Protected apps.** Some banking apps refuse to run while any accessibility service (for example a gesture app) is on. Mark such an app as protected: Still switches accessibility services off while it runs, shows the system button bar so you always have a way back, and restores everything when you return home.

## Usage

- Swipe right-to-left on home: app list. Left-to-right in the list: back home.
- In the app list, long-press an app, then **More**: add to home, add to a group, protect.
- Long-press empty space on home: new group, settings.

## Setup over adb (optional)

Protected apps need permission to change accessibility settings:

```sh
adb shell pm grant app.stilllauncher android.permission.WRITE_SECURE_SETTINGS
```

The home screen can also be written from a computer. The receiver is guarded by `android.permission.DUMP`, which only the adb shell holds:

```sh
SPEC='{"entries":[{"app":"com.android.chrome"},{"group":"msg","apps":["org.telegram.messenger"]}]}'
adb shell am broadcast -a app.stilllauncher.IMPORT_HOME_LAYOUT \
  -n app.stilllauncher/app.olauncher.helper.HomeLayoutImportReceiver \
  --es spec_b64 "$(printf '%s' "$SPEC" | base64)"
# Undo the last import:
adb shell am broadcast -a app.stilllauncher.IMPORT_HOME_LAYOUT \
  -n app.stilllauncher/app.olauncher.helper.HomeLayoutImportReceiver --ez restore_backup true
```

## Build

```sh
./gradlew testDebugUnitTest assembleRelease
```

## License and origin

Still is free software under the GNU General Public License v3.0 (see `LICENSE`).

Still is derived from [Olauncher](https://github.com/tanujnotes/Olauncher) by Tanuj, also GPL-3.0. Original code remains copyright of its authors; changes since the fork are by Olivier Dehareng. Still is an independent project and is not affiliated with or endorsed by Olauncher.
