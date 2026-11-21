# ObjectCount PRO v2 — offline precision counting

Standalone Android APK (Android 10+, ARM64), supports Android 16 tablets.

## Two counting algorithms
1. **AI Common Objects** uses EfficientDet-Lite2 trained on the 80 COCO classes, with user-controlled confidence and overlapping tiled scans for small items. This replaces the less accurate Lite0 used in v1.
2. **Repeated Items** uses OpenCV image segmentation and external contours, for *separated* repeated foreground shapes on a *relatively plain, uniform* background such as coins, nuts, screws, fruits, pills, and parts. Choose foreground/background contrast, color sensitivity and minimum item area. Works for items not trained into the AI model, but cannot identify what an item actually is. An ambiguous blob can represent multiple touching objects.

The displayed count is an **estimate**, not ground truth. Boxes show exactly what was counted. Use Mark Missed to add a marker, tap counted boxes to exclude wrong items, tap an existing manual marker to remove it. Select object classes to filter the count.

## Features
- Full-size camera image via Android image capture and gallery document picker
- Category tally and visual boxes
- High-precision multi-tile AI inference (optional)
- Repeated-item detection for high-contrast, plain-background separated items
- Adjustable filters and corrections, export annotated JPEG
- No internet permissions; models and OpenCV natives bundled
- Dark premium tablet-friendly UI
- Distinct app id from v1 so both builds can be installed side-by-side without signing-key conflicts

## Known limitations
- The AI recognizes *only* its training categories, and can miss tiny or overlapping objects.
- Contour mode may count background texture, shadows and reflections. Several touching items can merge into one contour, and partial/hidden items cannot be reliably reconstructed.
- Verification against representative test images is needed for any accuracy-critical inventory or measurements.

## Build
GitHub Actions bundles the official EfficientDet-Lite2 TFLite model from Google's TensorFlow download storage. Android compiles with TFLite Task Vision and official OpenCV 4.10 AAR, then publishes **ObjectCountPRO-v2.apk** as a public GitHub Release.

Developed By : Gaurav Kharva
