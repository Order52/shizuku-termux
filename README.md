# shizuku-termux

Start [Shizuku](https://shizuku.rikka.app/) from your phone with one command in
[Termux](https://github.com/termux/termux-app), or one tap in a 17 KB app. No computer needed
whenever your phone allows it.

```sh
curl -fsSL https://raw.githubusercontent.com/Order52/shizuku-termux/main/install.sh | bash
```

Then type `sz` in Termux, or tap **Start** in the app.

## What you need

- **Termux** from [GitHub](https://github.com/termux/termux-app/releases/latest) or
  [F-Droid](https://f-droid.org/packages/com.termux/). The Google Play version is outdated and
  will not work with the app.
- **Shizuku** from [shizuku.rikka.app](https://shizuku.rikka.app/download/), or any fork that
  keeps the package name `moe.shizuku.privileged.api`.
- **One way** for Termux to get adb access (see below).

## How Termux gets access

Shizuku needs adb-level (or root) access to start. The `shizuku` command tries these in order
and uses the first one that works:

| Method | Phones | Computer needed? |
|---|---|---|
| **Wireless debugging** | Android 11+ with *Wireless debugging* in Developer options | No |
| **adb over TCP (port 5555)** | Any Android | Once after every reboot |
| **Root** | Rooted phones | No |

### Android 11+ (Wireless debugging)

1. Turn on **Developer options** (Settings > About phone > tap *Build number* 7 times).
2. Connect to **Wi-Fi**, then turn on **Developer options > Wireless debugging**.
3. Tap **Pair device with pairing code**. Keep that dialog open: use split screen with Termux,
   or Termux's floating window.
4. In Termux run `shizuku pair` and type the port and 6-digit code it shows.

You only pair once. After that `sz` finds Wireless debugging by itself. It still needs to be
switched on (Android turns it off when Wi-Fi changes or after a reboot).

### Any Android (adb over TCP)

Some phones have no Wireless debugging (for example Huawei EMUI). Plug the phone into a computer
with [platform-tools](https://developer.android.com/tools/releases/platform-tools) and run once:

```sh
adb tcpip 5555
```

Unplug, then run `sz` in Termux. This keeps working until the phone restarts.

### Rooted phones

Allow Termux in your root manager (Magisk, KernelSU, APatch). `sz` uses `su` when adb is not
available.

## Commands

| Command | What it does |
|---|---|
| `sz` | Start Shizuku |
| `szoff` | Stop Shizuku |
| `szs` | Show whether Shizuku is running |
| `shizuku pair` | Pair with Wireless debugging (one time) |
| `shizuku connect PORT` | Use a specific Wireless debugging port |
| `shizuku autostart on/off` | Start Shizuku whenever Termux opens (on by default) |
| `shizuku fix-termux` | Stop Android 12+ from killing Termux's background processes |
| `shizuku update` | Update to the latest version |
| `shizuku uninstall` | Remove everything this installed |

Shortcuts work in bash, zsh and fish.

## The app

Download `ShizukuTermux.apk` from [Releases](https://github.com/Order52/shizuku-termux/releases).

- **Start / Stop / Status** buttons, plus a setup checklist that tells you what is missing.
- Long-press the app icon for **Start Shizuku** and **Stop Shizuku** shortcuts; drag them to
  your home screen for one-tap start.
- 17 KB, no internet permission, no tracking. It only sends the `shizuku` command to Termux
  through Termux's official
  [RUN_COMMAND](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent) interface.
- Android 7.0 and newer.

The first time, tap **Start** and allow *"Run commands in Termux environment"*. The installer
already enables `allow-external-apps` in Termux, which the app needs.

## Troubleshooting

**"CANNOT LINK EXECUTABLE adb"**: your Termux packages are out of date. Run `pkg upgrade`, then
the installer again. The installer tries to fix this by itself.

**Termux commands get killed / "Process completed (signal 9)"** (Android 12+): Android's
phantom process limit. Once `sz` has adb access it turns this limit off automatically. If the
installer itself gets killed, run this from a computer once, then retry:

```sh
adb shell "device_config set_sync_disabled_for_tests persistent; device_config put activity_manager max_phantom_processes 2147483647; settings put global settings_enable_monitor_phantom_procs false"
```

**"Port 5555 is closed" / "Could not get adb access"**: the phone was restarted, or Wireless
debugging is off. Turn Wireless debugging back on, or run `adb tcpip 5555` from a computer again.

**App says "No answer from Termux"**: open Termux once, then retry. Make sure Termux is not the
Google Play version, and allow Termux to run in the background in your phone's battery settings.

**Shizuku stops after a while**: allow Shizuku and Termux to run in the background
(Settings > Battery / App launch on Huawei, Xiaomi, Samsung, Oppo, Vivo).

## What it changes on your phone

- Installs `android-tools` (adb) in Termux, the `shizuku` command, and shell shortcuts.
- Sets `allow-external-apps = true` in `~/.termux/termux.properties` so the app can talk to Termux.
- On Android 12+, the first time it gets adb access, it turns off the phantom process limit
  (`max_phantom_processes`, `settings_enable_monitor_phantom_procs`).

`shizuku uninstall` removes the command and shortcuts. Turn the phantom process limit back on
with `adb shell settings put global settings_enable_monitor_phantom_procs true`.

## Building the app

```sh
cd app
./gradlew assembleRelease
```

Without a `keystore.properties` file the release APK is signed with your debug key.

## License

MIT
