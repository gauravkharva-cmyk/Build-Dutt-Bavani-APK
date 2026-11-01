# GK Sketch Studio

Original, offline Android sketching / digital painting application. Developed By : Gaurav Kharva.

## Features

- Brush engine: ink, pencil, marker, airbrush, watercolor, neon, charcoal, pixel, calligraphy and eraser.
- Stylus pressure variation, optional stylus-only palm rejection, multi-touch zoom/pan.
- Editable layers with renaming, visibility, duplicate, opacity, ordering and blend modes (normal / multiply / screen / add).
- Symmetry: vertical, horizontal, both and radial 6 / 8 / 12; optional grid.
- Line, rectangle, ellipse, arrow, flood fill, text and color eyedropper.
- Undo / redo for brush strokes, fills, text and geometric shapes.
- Local gallery and automatic save via IndexedDB; editable `.gkdraw` project export and import.
- Import PNG/JPG/etc. as a layer; export PNG or JPEG into Downloads/GK Sketch Studio.
- Adaptive mobile and tablet interface; no network permissions, no online account, no tracking SDK.

## Build

Open with Android Studio (JDK 17, Android SDK 35). Run `gradle :app:assembleDebug` with Gradle 8.10.2. GitHub Actions workflow builds the APK and uploads it as an artifact. `app/src/main/assets/index.html` can also be opened in a desktop browser for testing the drawing interface.

## Notes / limitations

This is a new app with original implementation, not an authorized copy of the commercial Sketchbook app. Brush behaviors imitate familiar physical effects, not physics-grade paint simulation. Undo/redo captures the active layer for each painting action and is bounded to 12 steps / 30 MB; layer stack modifications are not undoable. Local browser storage can be cleared by Android app-data reset or uninstall. Export `.gkdraw` files for backup. Image export composites onto an opaque white background. Image gallery persistence uses device WebView IndexedDB; the export bridge writes only to public Downloads via MediaStore. Android 10+ is required.
