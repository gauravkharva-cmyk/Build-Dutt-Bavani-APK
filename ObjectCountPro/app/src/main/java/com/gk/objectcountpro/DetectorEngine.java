package com.gk.objectcountpro;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Higher-accuracy general COCO object detection. Large images are scanned at
 * overlapping scales; predictions from adjacent tiles are de-duplicated.
 */
public final class DetectorEngine implements AutoCloseable {
    public static class Hit {
        public final RectF box;
        public final String label;
        public final float score;
        public boolean excluded = false;
        public Hit(RectF box, String label, float score) {
            this.box = box; this.label = label; this.score = score;
        }
    }

    private final ObjectDetector detector;

    public DetectorEngine(Context context) throws IOException {
        ObjectDetector.ObjectDetectorOptions options =
                ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setNumThreads(4).build())
                    .setMaxResults(250)
                    .setScoreThreshold(0.10f)
                    .build();
        detector = ObjectDetector.createFromFileAndOptions(
                context, "efficientdet-lite2.tflite", options);
    }

    public List<Hit> analyze(Bitmap image, boolean enhanced) {
        List<Hit> candidates = new ArrayList<>();
        scan(image, 0, 0, image.getWidth(), image.getHeight(), candidates);
        if (enhanced) {
            int shortSide = Math.min(image.getWidth(), image.getHeight());
            if (shortSide >= 600) {
                scanGrid(image, shortSide >= 950 ? 3 : 2, candidates);
            }
        }
        candidates.sort((a,b) -> Float.compare(b.score, a.score));
        List<Hit> result = new ArrayList<>();
        for (Hit hit : candidates) {
            if (hit.box.width() < 3 || hit.box.height() < 3) continue;
            boolean duplicate = false;
            for (Hit prior : result) {
                if (!hit.label.equalsIgnoreCase(prior.label)) continue;
                if (iou(hit.box,prior.box) > 0.34f ||
                        intersectionOverSmaller(hit.box, prior.box) > 0.74f) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) result.add(hit);
            if (result.size() >= 500) break;
        }
        return result;
    }

    private void scanGrid(Bitmap image, int grid, List<Hit> items) {
        int w = image.getWidth(), h = image.getHeight();
        int tileW = Math.min(w, Math.max(300, Math.round(w * (grid == 3 ? 0.47f : 0.60f))));
        int tileH = Math.min(h, Math.max(300, Math.round(h * (grid == 3 ? 0.47f : 0.60f))));
        for (int r=0;r<grid;r++) {
            for (int c=0;c<grid;c++) {
                int x = Math.round((w-tileW) * (c/(float)(grid-1)));
                int y = Math.round((h-tileH) * (r/(float)(grid-1)));
                Bitmap crop = Bitmap.createBitmap(image,x,y,tileW,tileH);
                try { scan(crop,x,y,w,h,items); }
                finally { if (crop != image) crop.recycle(); }
            }
        }
    }

    private void scan(Bitmap image, int xOffset, int yOffset,
                      int originalWidth, int originalHeight, List<Hit> hits) {
        List<Detection> detections = detector.detect(TensorImage.fromBitmap(image));
        int cropW=image.getWidth(),cropH=image.getHeight();
        for (Detection d : detections) {
            if (d.getCategories().isEmpty()) continue;
            Category c=d.getCategories().get(0);
            String label=c.getLabel();
            if (label==null || label.trim().isEmpty()) label="Object";
            RectF rect = new RectF(d.getBoundingBox());
            // Avoid partial boxes on INTERNAL tile edges, which would otherwise
            // double count objects present more fully in overlapping neighbor tiles.
            if ((xOffset>0 && rect.left < cropW*0.035f) ||
                (yOffset>0 && rect.top < cropH*0.035f) ||
                (xOffset+cropW<originalWidth && rect.right > cropW*0.965f) ||
                (yOffset+cropH<originalHeight && rect.bottom > cropH*0.965f)) {
                continue;
            }
            rect.offset(xOffset,yOffset);
            rect.left = clamp(rect.left,0,originalWidth);
            rect.top = clamp(rect.top,0,originalHeight);
            rect.right = clamp(rect.right,0,originalWidth);
            rect.bottom = clamp(rect.bottom,0,originalHeight);
            hits.add(new Hit(rect, label.substring(0,1).toUpperCase(Locale.ROOT)+label.substring(1),c.getScore()));
        }
    }

    private static float clamp(float n,float min,float max) {
        return Math.max(min,Math.min(max,n));
    }
    private static float iou(RectF a,RectF b) {
        float l=Math.max(a.left,b.left),t=Math.max(a.top,b.top);
        float r=Math.min(a.right,b.right),bottom=Math.min(a.bottom,b.bottom);
        float intersection=Math.max(0,r-l)*Math.max(0,bottom-t);
        float union=a.width()*a.height()+b.width()*b.height()-intersection;
        return union>0?intersection/union:0;
    }
    private static float intersectionOverSmaller(RectF a,RectF b) {
        float l=Math.max(a.left,b.left),t=Math.max(a.top,b.top);
        float r=Math.min(a.right,b.right),bottom=Math.min(a.bottom,b.bottom);
        float intersection=Math.max(0,r-l)*Math.max(0,bottom-t);
        float smaller=Math.min(a.width()*a.height(),b.width()*b.height());
        return smaller>0?intersection/smaller:0;
    }
    @Override public void close(){ detector.close(); }
}
