package com.gk.objectcountpro;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class DetectorEngine implements AutoCloseable {
    public static class Hit {
        public final RectF box;
        public final String label;
        public final float score;
        public boolean excluded = false;

        public Hit(RectF box, String label, float score) {
            this.box = box;
            this.label = label;
            this.score = score;
        }
    }

    private final ObjectDetector detector;

    public DetectorEngine(Context context) throws java.io.IOException {
        ObjectDetector.ObjectDetectorOptions options =
                ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setNumThreads(4).build())
                    .setMaxResults(100)
                    .setScoreThreshold(0.15f)
                    .build();
        detector = ObjectDetector.createFromFileAndOptions(
                context, "efficientdet-lite0.tflite", options);
    }

    public List<Hit> analyze(Bitmap image, boolean enhanced) {
        List<Hit> candidates = new ArrayList<>();
        scan(image, 0, 0, candidates);

        // Optional overlap-tiled inference improves sensitivity to small items.
        // Each tile is inspected separately; overlapping boxes are merged below.
        if (enhanced && image.getWidth() > 500 && image.getHeight() > 500) {
            int tileW = Math.min(image.getWidth(), Math.max(400, Math.round(image.getWidth() * 0.57f)));
            int tileH = Math.min(image.getHeight(), Math.max(400, Math.round(image.getHeight() * 0.57f)));
            int[] lefts = {0, image.getWidth() - tileW};
            int[] tops  = {0, image.getHeight() - tileH};
            for (int top : tops) for (int left : lefts) {
                Bitmap tile = Bitmap.createBitmap(image, left, top, tileW, tileH);
                try { scan(tile, left, top, candidates); }
                finally { if (tile != image) tile.recycle(); }
            }
        }

        candidates.sort((a, b) -> Float.compare(b.score, a.score));
        List<Hit> result = new ArrayList<>();
        for (Hit hit : candidates) {
            if (hit.box.width() < 2 || hit.box.height() < 2) continue;
            boolean duplicate = false;
            for (Hit accepted : result) {
                if (!accepted.label.equalsIgnoreCase(hit.label)) continue;
                if (iou(accepted.box, hit.box) > 0.38f ||
                    intersectionOverSmaller(accepted.box, hit.box) > 0.76f) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) result.add(hit);
        }
        return result;
    }

    private void scan(Bitmap bitmap, int xOffset, int yOffset, List<Hit> hits) {
        TensorImage input = TensorImage.fromBitmap(bitmap);
        List<Detection> detections = detector.detect(input);
        for (Detection detection : detections) {
            if (detection.getCategories().isEmpty()) continue;
            Category c = detection.getCategories().get(0);
            String label = c.getLabel();
            if (label == null || label.trim().isEmpty()) label = "Object";
            RectF rect = new RectF(detection.getBoundingBox());
            // Task Vision returns box coordinates in the supplied bitmap's coordinate system.
            rect.left = clamp(rect.left + xOffset, 0, Integer.MAX_VALUE);
            rect.top = clamp(rect.top + yOffset, 0, Integer.MAX_VALUE);
            rect.right = clamp(rect.right + xOffset, 0, Integer.MAX_VALUE);
            rect.bottom = clamp(rect.bottom + yOffset, 0, Integer.MAX_VALUE);
            hits.add(new Hit(rect, titleCase(label), c.getScore()));
        }
    }

    private static String titleCase(String label) {
        if (label.isEmpty()) return "Object";
        return label.substring(0, 1).toUpperCase(Locale.ROOT) + label.substring(1);
    }

    private static float clamp(float n, float min, float max) {
        return Math.max(min, Math.min(max, n));
    }

    private static float iou(RectF a, RectF b) {
        float left = Math.max(a.left, b.left);
        float top = Math.max(a.top, b.top);
        float right = Math.min(a.right, b.right);
        float bottom = Math.min(a.bottom, b.bottom);
        float area = Math.max(0, right - left) * Math.max(0, bottom - top);
        float union = a.width() * a.height() + b.width() * b.height() - area;
        return union > 0 ? area / union : 0;
    }

    private static float intersectionOverSmaller(RectF a, RectF b) {
        float left = Math.max(a.left, b.left);
        float top = Math.max(a.top, b.top);
        float right = Math.min(a.right, b.right);
        float bottom = Math.min(a.bottom, b.bottom);
        float area = Math.max(0, right - left) * Math.max(0, bottom - top);
        float smaller = Math.min(a.width() * a.height(), b.width() * b.height());
        return smaller > 0 ? area / smaller : 0;
    }

    @Override public void close() { detector.close(); }
}
