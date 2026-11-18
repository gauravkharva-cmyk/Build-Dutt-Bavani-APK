# OSHO World Premium — Android

Private personal-use Android downloader and offline MP3 player.

## Features
- Hindi, English and unclassified OSHO audio entries.
- Refresh the catalog directly from OSHO World's official audio API (English and Hindi series, subseries and MP3 episodes).
- Import direct MP3 links if the online catalog is unavailable or incomplete.
- Tap a recording to download it; queue the full catalog with Download all.
- Persistent queue, retry of failed files, byte-range resume when host supports HTTP Range.
- Saves finished MP3 files under `Music/OSHO World/<language>/<series>/` using Android MediaStore.
- Background playback with media controls and playback position resume.
- Android 16 compatible; Android 8+ minimum.

## Important limitations
- The official OSHO World API was verified from GitHub Actions, but completing all series and every actual MP3 download remains untested on-device; some links may change or be unavailable.
- The website advertises approximately 5,640 recordings; the available API inventory can differ and may change over time.
- Android 15+ foreground data-sync jobs have OS time limits. Resume manually if the bulk queue is stopped.
- Full-library downloads need large amounts of free storage.
- Download only recordings publicly offered for free on OSHO World; this app is not affiliated with OSHO World.
- Android bulk-download restrictions mean playback and downloads use separate foreground services.
- Debug builds are signed by the GitHub Actions Android debug keystore, so Android updates may need the same signing key.

## Build
From `oshoworld/`: `gradle assembleDebug`.
GitHub Actions artifact: `OSHO-World-Premium-APK`.

Built for personal use by Gaurav Kharva.

## Version 1.2.0 background player fixes
- Foreground media playback continues when the screen is locked or you switch to ordinary apps.
- Per-recording saved position, updated every 2 seconds and immediately on pause.
- Play/Resume for a currently selected recording no longer resets playback to the beginning.
- Playback history is restored after restarting the application.
- Explicit Resume Audio button and saved position in My Downloads.
- Partial wake-lock keeps the CPU active while MediaPlayer plays with the screen off.
- Battery/background settings shortcut for Lenovo and other Android devices.
- New v3 application ID: installs alongside v2 because each GitHub Actions debug APK may have a different signing certificate. In v3, open My Downloads and scan the public Music/OSHO World folder to find your previously saved MP3s. Saved playback progress from v2 cannot automatically migrate to v3.
- As required by Android audio-focus rules, another app that starts playing audio may pause or duck OSHO playback; switching to apps that don't play audio should not stop it.
