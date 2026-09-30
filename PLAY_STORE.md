# Play Store release checklist

## Before the first upload

1. **Icon** — done. Launcher layers in `app/src/main/res/mipmap-*/ic_launcher_{foreground,monochrome}.png`
   were generated from `icon.jpg`; the 512×512 Play Console hi-res icon is `play/icon-512.png`.
2. **Upload key** — generate one and fill in `keystore.properties` (see
   `keystore.properties.example`; both the `.properties` file and `*.jks` are git-ignored).
   Enroll in Play App Signing when creating the app.
3. **Build** — bump `versionCode` in `app/build.gradle.kts` for every upload, then
   `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
4. **Privacy policy** — host `PRIVACY.md` somewhere public (e.g. GitHub Pages) and paste the URL
   into Console → App content → Privacy policy.

## Play Console → App content declarations

- **Accessibility API** — required because of `JoyConAccessibilityService`. Declare it is *not* an
  accessibility tool (`isAccessibilityTool` is unset). Core functionality: "Remaps game controller
  buttons from two Joy-Cons into one merged controller while a game is focused." A short video of
  the in-app disclosure dialog (Setup → Open Accessibility settings) followed by enabling the
  service is required.
- **Foreground service types** — `specialUse`. Describe: "Keeps the controller-merge session alive
  while the user plays a game in another app; user starts/stops it explicitly." Video of tapping
  *Start merge service* and the ongoing notification.
- **Data safety** — no data collected, no data shared.
- **Content rating** — questionnaire; utility app, no user content.
- **Target audience** — 13+ is simplest (avoids Families policy).
- **Ads** — no.

## Listing notes

- Nintendo / Joy-Con are trademarks: don't use "Joy-Con" or Nintendo artwork in the app name or
  icon. Describing compatibility in the description ("works with Nintendo Joy-Con controllers")
  is generally fine; add "Not affiliated with Nintendo."
- State up front that the app **requires Shizuku** and that full stick merging depends on the
  device's SELinux policy — reviewers and users will otherwise report it as "doesn't work."
