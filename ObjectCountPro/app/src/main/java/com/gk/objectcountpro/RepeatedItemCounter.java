package com.gk.objectcountpro;

import android.graphics.Bitmap;
import android.graphics.RectF;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Counts separated foreground blobs on a relatively uniform background.
 *
 * A transparent, deterministic alternative to a COCO-trained object detector for
 * non-COCO items (nuts, bolts, coins, pills, seeds, packaged parts, etc.).
 * It deliberately makes no claims of semantic recognition. Touching objects
 * can merge; background texture and shadows can become false positives.
 */
public final class RepeatedItemCounter {
    public static class Result {
        public final List<DetectorEngine.Hit> hits;
        public final String message;
        Result(List<DetectorEngine.Hit> hits, String message) {
            this.hits = hits; this.message = message;
        }
    }

    private static boolean initialized = false;

    private static synchronized void ensureReady() {
        if (!initialized) {
            if (!OpenCVLoader.initLocal()) {
                throw new IllegalStateException("OpenCV native library is not available");
            }
            initialized = true;
        }
    }

    /** style: 0 = background-color difference, 1 = dark, 2 = light */
    public Result analyze(Bitmap image, int style, int sensitivity, int sizeSetting) {
        ensureReady();

        final int iw = image.getWidth(), ih = image.getHeight();
        final float fraction = Math.min(1.0f, 1400f / Math.max(iw, ih));
        final int width = Math.max(1, Math.round(iw * fraction));
        final int height = Math.max(1, Math.round(ih * fraction));
        Bitmap scaled = width == iw && height == ih
                ? image : Bitmap.createScaledBitmap(image, width, height, true);
        Mat rgba = new Mat(), rgb = new Mat(), gray = new Mat(),
            lab = new Mat(), mask = new Mat(), smooth = new Mat();
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Mat kernel = null;
        try {
            Utils.bitmapToMat(scaled, rgba);
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB);
            if (style == 0) {
                Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab);
                produceBackgroundMask(lab, width, height, sensitivity, mask);
            } else {
                Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY);
                Imgproc.GaussianBlur(gray, smooth, new Size(5,5), 0);
                int type = (style == 1 ? Imgproc.THRESH_BINARY_INV : Imgproc.THRESH_BINARY)
                        | Imgproc.THRESH_OTSU;
                Imgproc.threshold(smooth, mask, 0, 255, type);
            }

            int morphRadius = width > 1000 ? 3 : 2;
            kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,
                                        new Size(morphRadius,morphRadius));
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel);
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel);

            // RETR_EXTERNAL prevents counting a hole inside a single object twice.
            Mat contourInput = mask.clone();
            try {
                Imgproc.findContours(contourInput, contours, hierarchy,
                                     Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            } finally {
                contourInput.release();
            }

            final double pictureArea = (double)width * height;
            final double minFraction = 0.000025 * Math.pow(350, sizeSetting / 100.0);
            final double minimumArea = Math.max(12, pictureArea * minFraction);
            final double maximumArea = pictureArea * 0.85;
            final float toX = (float)iw / width;
            final float toY = (float)ih / height;

            List<DetectorEngine.Hit> items = new ArrayList<>();
            int skipped = 0;
            for (MatOfPoint shape : contours) {
                double area = Imgproc.contourArea(shape);
                if (area < minimumArea || area > maximumArea) { skipped++; continue; }
                Rect r = Imgproc.boundingRect(shape);
                if (r.width < 3 || r.height < 3) { skipped++; continue; }
                double rectangularFill = area / (r.width * (double)r.height);
                if (rectangularFill < 0.12) { skipped++; continue; }
                // A contour that takes up almost the entire frame is likely background.
                if (r.width > width * 0.99 && r.height > height * 0.99) {
                    skipped++; continue;
                }
                RectF box = new RectF(r.x * toX, r.y * toY,
                                   (r.x+r.width) * toX, (r.y+r.height) * toY);
                items.add(new DetectorEngine.Hit(box, "Item", 1.0f));
            }
            items.sort((a,b) -> a.box.top == b.box.top
                    ? Float.compare(a.box.left,b.box.left)
                    : Float.compare(a.box.top,b.box.top));

            if (items.size() > 900) {
                // An excessively fragmented image should not be presented as a confident count.
                return new Result(new ArrayList<>(),
                     "Too many small regions; increase Minimum item size or use a plain background.");
            }
            if (items.isEmpty()) {
                return new Result(items, "No separated items found. Try another contrast option, " +
                        "increase sensitivity, or reduce Minimum item size.");
            }
            return new Result(items, "Counting separated shapes (not semantic AI). " +
                    "Check each box; touching objects may merge and shadows may be counted.");
        } finally {
            for (MatOfPoint c : contours) c.release();
            if (kernel != null) kernel.release();
            hierarchy.release(); smooth.release(); mask.release();
            lab.release(); gray.release(); rgb.release(); rgba.release();
            if (scaled != image) scaled.recycle();
        }
    }

    private static void produceBackgroundMask(Mat lab, int w, int h,
                                               int sensitivity, Mat result) {
        byte[] pixels = new byte[w*h*3];
        lab.get(0,0,pixels);
        // Robust median sampling along all four image edges.
        final int count = 240;
        int[] l = new int[count*4], a = new int[count*4], b = new int[count*4];
        for (int i=0; i<count; i++) {
            int x = Math.min(w-1, Math.round((w-1) * (i+0.5f) / count));
            int y = Math.min(h-1, Math.round((h-1) * (i+0.5f) / count));
            int[] offsets = { (0*w+x)*3, ((h-1)*w+x)*3,
                              (y*w+0)*3, (y*w+(w-1))*3 };
            for (int j=0;j<4;j++) {
                int index = i*4+j, offset = offsets[j];
                l[index] = pixels[offset] & 255;
                a[index] = pixels[offset+1] & 255;
                b[index] = pixels[offset+2] & 255;
            }
        }
        int[] sortedL=l.clone(), sortedA=a.clone(), sortedB=b.clone();
        Arrays.sort(sortedL); Arrays.sort(sortedA); Arrays.sort(sortedB);
        final double ml=sortedL[sortedL.length/2];
        final double ma=sortedA[sortedA.length/2];
        final double mb=sortedB[sortedB.length/2];

        double[] edgeDistances = new double[l.length];
        for (int i=0;i<l.length;i++) {
            double dl=(l[i]-ml)*0.78, da=a[i]-ma, db=b[i]-mb;
            edgeDistances[i] = Math.sqrt(dl*dl + da*da + db*db);
        }
        Arrays.sort(edgeDistances);
        // Threshold above most border variation to avoid treating background
        // texture as foreground when corners are not perfectly uniform.
        final double robustNoise = edgeDistances[(int)(edgeDistances.length*0.82)];
        final double desired = 12.0 + (100.0 - sensitivity) * 0.44;
        final double threshold = Math.max(desired, robustNoise + 6.0);

        byte[] mask = new byte[w*h];
        for (int i=0, p=0; i<mask.length; i++,p+=3) {
            double dl=((pixels[p]&255)-ml)*0.78;
            double da=(pixels[p+1]&255)-ma;
            double db=(pixels[p+2]&255)-mb;
            double d2=dl*dl+da*da+db*db;
            mask[i] = (byte)(d2 > threshold*threshold ? 255 : 0);
        }
        result.create(h,w,CvType.CV_8UC1);
        result.put(0,0,mask);
    }
}
