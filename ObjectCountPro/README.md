# ObjectCount PRO — offline Android app

A standalone offline Android image object counter for Android 10+ (ARM64). Open any image from the Android file/photo picker or take a photo using the device's camera app.

Features:
- On-device COCO category recognition using EfficientDet-Lite0 + TensorFlow Lite Task Vision.
- Bounding boxes, class names, confidence scores and total/per-category count.
- Adjustable detection confidence, optional 2×2 tile-enhanced scanning.
- Manually mark missed items, or tap a detection to exclude it; reset corrections.
- Save annotated JPEGs to Pictures/ObjectCountPRO or share with Android apps.
- No network permission, no user account, no photo uploads.
- Camera works through an Android camera intent + FileProvider.

### Accuracy limits
This is a pretrained COCO 80-category object detector, not a universal counter. Dense items, tiny objects, ambiguous/untrained categories, overlaps, occlusion, and perspective can cause undercounts/overcounts. Displayed totals are estimates until manually verified. Tile scan may introduce additional false positives (duplicate suppression is heuristic). Test counting-critical uses with known examples.

### Build
The GitHub Actions workflow downloads the pretrained TFLite model from the official TensorFlow bucket, places it in app/src/main/assets, runs Gradle assembleDebug, and uploads ObjectCountPRO.apk as a downloadable build artifact. Debug APK is Android-signed by the build tools and installable for testing.

Model URL: https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/object_detection/android/lite-model_efficientdet_lite0_detection_metadata_1.tflite
Model origin: TensorFlow Lite examples (Apache-2.0).

Developed by Gaurav Kharva.
