# OSHO World Premium — Android

Private personal-use Android downloader and offline MP3 player.

## Features
- Hindi, English and unclassified OSHO audio entries.
- Refresh catalog from public `aagrawal207/OshoDiscourses` JSON sources.
- Import direct MP3 links if the online catalog is unavailable or incomplete.
- Tap a recording to download it; queue the full catalog with Download all.
- Persistent queue, retry of failed files, byte-range resume when host supports HTTP Range.
- Saves finished MP3 files under `Music/OSHO World/<language>/<series>/` using Android MediaStore.
- Background playback with media controls and playback position resume.
- Android 16 compatible; Android 8+ minimum.

## Important limitations
- The third-party catalog is not an official OSHO World API and may omit some links or move; its availability and the ability to download each URL require testing on-device.
- The site advertises around 5,640 recordings; the public third-party catalog may contain a different number.
- Android 15+ foreground data-sync jobs have OS time limits. Resume manually if the bulk queue is stopped.
- Full-library downloads need large amounts of free storage.
- Download only recordings publicly offered for free on OSHO World; this app is not affiliated with OSHO World.
- Android bulk-download restrictions mean playback and downloads use separate foreground services.
- Debug builds are signed by the GitHub Actions Android debug keystore, so Android updates may need the same signing key.

## Build
From `oshoworld/`: `gradle assembleDebug`.
GitHub Actions artifact: `OSHO-World-Premium-APK`.

Built for personal use by Gaurav Kharva.
