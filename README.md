# JoynCon

Merges two independently-paired Nintendo Switch Joy-Con controllers (Bluetooth Classic HID) into
one virtual gamepad, system-wide, on a non-rooted Android phone — using [Shizuku](https://github.com/RikkaApps/Shizuku)
for elevated (shell-UID) access instead of root.

**Read "How this actually works" before you rely on this for anything.** Android's normal input
pipeline only delivers joystick input to whichever app currently has window focus. Getting around
that without root turns out to be possible for part of this project and not possible for another
part, and which is which matters a lot for what you should expect in a real game.

## What you get

| Path | What it merges | Works while a game has focus? | Requirement |
|---|---|---|---|
| **uinput/evdev bridge** (primary) | Buttons **and** analog sticks | Yes | Shizuku; device's SELinux policy must allow shell to open `/dev/input/eventX` and `/dev/uinput` |
| **Accessibility fallback** | Buttons only | Yes | Shizuku (for injection) + the JoynCon Accessibility Service enabled |
| **Passthrough** | Nothing — the single connected Joy-Con reaches the game unmodified | Yes (it's just Android's normal dispatch) | Nothing extra |

Only one Joy-Con connected → JoynCon does nothing at all (by design, see below); the phone just
sees a normal, single, half-a-controller joystick, same as if JoynCon weren't installed.

Both connected → JoynCon grabs both, presents one merged `/dev/uinput` gamepad (Xbox/Pro-Controller
style: `ABS_X/Y` left stick, `ABS_RX/RY` right stick, `ABS_HAT0X/Y` d-pad, standard `BTN_*` face/shoulder
buttons), and that's what the currently-focused app — your sideloaded Unity game included — sees.

## How this actually works

Android dispatches `MotionEvent`/`KeyEvent` from any `InputDevice` to whichever window currently has
input focus, full stop. A background service, an Accessibility Service, even a process running as
root — none of that changes *that* rule; it's how `InputDispatcher` routes events, not a permission
check. So a naive "foreground service listens for Joy-Con input and re-injects it" design silently
fails the moment a game (not JoynCon) is the focused, foreground app — the service gets nothing.

There is exactly one way around this without root: **don't go through Android's input dispatch for
the read side at all.** JoynCon's Shizuku `UserService` (`UInputBridgeService` /
`app/src/main/cpp/joyncon_native.cpp`) runs as UID 2000 (`shell`) and:

1. Opens `/dev/input/eventX` directly for each Joy-Con it identifies by vendor/product ID
   (`0x057e` / `0x2006` and `0x2007`), reading raw `input_event` structs — this is a kernel-level
   character device read, not Android's `InputManager`/`InputDispatcher`, so it is **not** gated by
   window focus.
2. Calls `ioctl(fd, EVIOCGRAB, 1)` on each, which makes JoynCon's fd the *exclusive* recipient of
   that device's events — Android's own `InputReader` (and therefore any game) stops seeing the raw,
   unmerged Joy-Con the instant it's grabbed.
3. Merges both streams per your remap table and writes the result to a persistent virtual gamepad
   created via `/dev/uinput` (`UI_DEV_SETUP`/`UI_ABS_SETUP`/`UI_DEV_CREATE`). This *is* dispatched
   through the normal focus-gated path — which is exactly what you want, since it means it reaches
   whichever app the user is actually looking at.

Whether step 1/2 work at all depends entirely on the phone's SELinux policy granting the `shell`
domain read access to `/dev/input/*` and write access to `/dev/uinput`. This is not part of the
public Android API surface Shizuku documents — it's an emergent property of `shell`'s SELinux
context, which is why prior art like [K2Pad](https://github.com/aditya26260-dev/K2Pad) and
[SteamController-Android](https://github.com/SonicDX12/SteamController-Android) both rely on it and
both note it can vary by device/ROM. **On a Knox-locked Samsung phone specifically, this is the one
real unknown in this whole design** — Samsung's SELinux policy is generally stricter than AOSP's,
and we have no way to confirm from here whether it still permits this for `shell`. Use the
in-app **Setup → Capability self-check** (or `adb shell su 0 true; adb shell cat /dev/input/event0`
style manual probing) to find out on your actual unit. If it's blocked:

- Nothing crashes. `nativeStartMerge` fails cleanly, JoynCon falls back to buttons-only mode via
  the Accessibility Service, and the Status tab reports which mode is active.
- **Root-only fallback**: with root, you could bind-read `/dev/input` regardless of SELinux (or
  patch the policy), which is the only way to restore full analog-stick merging on such a device.
  JoynCon does not implement a rooted code path — it isn't needed on a device where the Shizuku
  path already works, and it's out of scope for a "no root" project otherwise.

### Why analog sticks specifically need the evdev path (and can't use Accessibility)

`AccessibilityService` has a genuine, documented exception to the focus rule: with
`FLAG_REQUEST_FILTER_KEY_EVENTS`, `onKeyEvent()` receives (and can consume) **KeyEvents system-wide
regardless of focus** — this is how existing no-root remappers like Button Mapper work, and it's
what JoynCon's fallback path (`JoyConAccessibilityService`) uses for buttons. There is no
equivalent public API for generic `MotionEvent`s (joystick axes) — `AccessibilityService` simply
has no `onGenericMotionEvent` hook. So in fallback mode, buttons merge correctly system-wide, but
the analog sticks do not merge at all; the Status/Setup tabs say so explicitly rather than silently
half-working.

### Bluetooth battery level

Not available. Joy-Cons report battery inside Nintendo's proprietary HID input report — by the time
a report reaches `/dev/input` it's already been flattened into abstract button/axis events by the
kernel HID driver (`hid-nintendo`), with the battery bits discarded. `BluetoothDevice.getBatteryLevel()`
is wired up as a best-effort fallback in case a given Android build happens to populate it, but for
Joy-Cons specifically it will very likely just report "unknown." Getting real battery percentage
would mean talking to the Joy-Con over a raw Bluetooth channel using Nintendo's own protocol, which
Android does not expose to third-party apps without root either.

### Default button/axis mapping is a best guess

The mainline Linux `hid-nintendo` driver's exact keycode assignments for Joy-Cons (which `BTN_*`
code means which physical button) aren't a stable, documented public contract, and can differ across
kernel/Android versions. `DefaultMapping.kt` ships a reasonable starting point, but **use the Remap
tab's "Detect raw codes" button** (backed by `IUInputBridge.scanRawDevices()`) to see what your
phone's driver actually reports, and fix any entries that don't match before relying on it.

## One-time Shizuku setup

1. Install [Shizuku](https://github.com/RikkaApps/Shizuku) (F-Droid, Play Store, or sideload the
   APK from its GitHub releases).
2. On the phone: **Settings → Developer options → Wireless debugging** (enable Developer options
   first if needed: Settings → About phone → tap Build number 7 times).
3. In Wireless debugging, tap **Pair device with pairing code** — note the IP, port, and 6-digit code.
4. Open the Shizuku app → **Start via wireless debugging** → enter that pairing info when prompted
   (Shizuku walks you through the `adb pair`/`adb connect` equivalent itself; no PC or cable needed
   on Android 11+).
5. Shizuku should now show "Running". Open JoynCon → **Status tab → Grant Shizuku permission**,
   approve the dialog Shizuku shows.
6. **You'll need to restart Shizuku after every phone reboot** — this is a Shizuku limitation (it
   isn't a persistent root-equivalent daemon), not something JoynCon can work around.
7. Only needed if the capability check says the uinput/evdev path is blocked: enable
   **Setup tab → Open Accessibility settings → JoynCon Button Fallback**.

## Using it

1. Pair both Joy-Cons via normal Android Bluetooth settings.
2. Open JoynCon, confirm Shizuku shows "Ready" on the Status tab.
3. Tap **Start merge service**. With both Joy-Cons connected you'll see "full merge active"; with
   only one, "passthrough."
4. Launch your game. If the uinput path worked, it sees one gamepad with both sticks and every
   button, merged per whatever's set on the Remap tab.
5. Disconnecting/reconnecting either Joy-Con is handled independently — a supervisor thread inside
   the Shizuku service keeps rescanning `/dev/input` for whichever role is currently missing and
   re-grabs it the moment it reappears, without tearing down the other Joy-Con's session or
   restarting the whole merge.

## Project structure

```
app/src/main/
  kotlin/dev/eastonseidel/joyncon/
    MainActivity.kt, JoynConApp.kt        — Compose UI shell, app entry point
    ui/                                     — Status / Remap / Setup screens
    model/                                  — RemapConfig, LogicalOutput, kernel code name tables
    data/                                   — DataStore-backed remap persistence, mapping JSON codec
    joycon/                                 — Vendor/product IDs, default best-guess mapping
    service/                                — Foreground service; zero-permission connection status
      via InputManager.InputDeviceListener
    shizuku/                                — Shizuku permission plumbing, UserService client/impl,
      hidden-API InputInjector for the Accessibility fallback path
    accessibility/                          — Buttons-only fallback (JoyConAccessibilityService)
  aidl/.../shizuku/                         — IUInputBridge / IBridgeCallback (app <-> Shizuku UserService)
  cpp/joyncon_native.cpp                   — evdev read + grab, merge logic, uinput virtual gamepad
```

## Building

Requires Android Studio (or the command line with `ANDROID_HOME`/`JAVA_HOME` set to a full JDK 17+
— not a JRE-only or jlink-less JBR install, which `core-for-system-modules` transform needs), NDK
`30.0.16138531` (or update `ndkVersion` in `app/build.gradle.kts` to whatever you have), and CMake
3.22.1.

```bash
./gradlew assembleDebug
```

On a **Linux ARM64 host**, the Android SDK's bundled CMake/ninja package may not ship a native
`ninja` for your CPU. If `buildCMakeDebug` fails with something like `ninja: error: unknown target`,
install `ninja` from your distro's package manager and add to `app/build.gradle.kts`:

```kotlin
externalNativeBuild {
    cmake {
        arguments += "-DCMAKE_MAKE_PROGRAM=/usr/bin/ninja" // or wherever your ninja lives
    }
}
```

This isn't needed on a typical x86_64 or Apple Silicon dev machine using Android Studio's bundled SDK.
