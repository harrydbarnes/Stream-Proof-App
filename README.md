# Stream Proof App

A private Android utility for capturing visible Stats for Spotify pages and preparing the resulting images for manual sending in an Instagram Web group DM.

## Build and run

1. Clone this repository and open it in Android Studio, or run `./gradlew :app:assembleDebug` from the project root.
2. Let Gradle sync, using JDK 17 and an Android SDK with API 35 installed.
3. Run the `app` configuration on an Android 10 or newer device or emulator.

The repository includes the Gradle wrapper, so a separate Gradle installation is not required. GitHub Actions also builds the debug APK and runs the unit-test task on pushes and pull requests.

The project uses Kotlin, Jetpack Compose, Material 3, DataStore Preferences, Android WebView, and scoped MediaStore storage. It does not request broad storage permission.

## First-time setup

1. On first launch, choose whether one or two playlist groups should receive proofs, and give each group a name.
2. Open **Spotify Stats** and log into Stats for Spotify in the embedded WebView if prompted. The normal WebView cookie/session store is reused.
3. Open **Instagram** and log into Instagram Web if prompted.
4. Open each Instagram group chat manually. Confirm the address contains an Instagram `/direct/t/...` thread URL, then tap **Save URL for _group name_**. Each URL is stored for that group and used when preparing its proof.
5. If the mobile Instagram layout is awkward, try the desktop user-agent option for Instagram in **Proofs / Settings**. Force mobile takes precedence unless a custom user agent is set.

## Capture and send

1. In **Spotify Stats**, navigate to the proof view you want to document.
2. Tap **Capture _group name_ proof**. The app captures the visible WebView viewport after the configured delay.
3. The image is saved in `Pictures/SpotifyProof` with a timestamped filename and a reference is stored locally.
4. Tap **Prepare _group name_ proof**, or open the Instagram tab and tap the relevant **Prepare _group name_ proof** button.
5. In the instruction panel, tap **Find image** if you want to locate the image first. Instagram Web will still open its own file chooser when its image button is tapped.
6. In Instagram Web, tap its photo/image button, select the matching latest `spotify-proof-playlist-...` image, and manually confirm/send it.
7. Tap **Mark sent** after the message has actually been sent.

The native **Share via Android** action is included as a fallback. It grants read access to the MediaStore URI and opens the standard chooser.

## Settings

The settings screen supports one or two named playlist groups, per-group Instagram chat URLs, custom Stats and Instagram URLs, PNG or JPG output, JPEG quality, capture delay, separate desktop user-agent switches, a force-mobile switch, custom user agents, third-party cookies, domain cookie clearing, and the experimental helper-click option.

The experimental helper only runs best-effort JavaScript that looks for an obvious image/attachment button. It never injects a file, presses Send, or uses private APIs.

## Known limitations

- Instagram group chats often do not appear in the Android share sheet.
- The official Instagram API does not support this personal group DM screenshot workflow.
- Instagram Web may still require manual attachment and send confirmation.
- Web automation selectors may break if Instagram changes its site.
- The app captures the visible WebView viewport. Full-page capture is intentionally not required because WebView full-page drawing is unreliable for dynamic pages.
- Android controls the file chooser. The app requests an image-focused picker, but it cannot force a particular folder or inject a chosen file into Instagram Web.
- Clearing cookies is best effort by domain because Android WebView uses a shared cookie store.
