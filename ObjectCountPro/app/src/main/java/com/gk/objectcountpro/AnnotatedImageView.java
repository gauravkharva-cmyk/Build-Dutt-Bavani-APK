package com.gk.objectcountpro;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AnnotatedImageView extends View {
    public interface TapListener { void onImageTap(float x, float y); }

    private Bitmap bitmap;
    private List<DetectorEngine.Hit> hits = new ArrayList<>();
    private List<PointF> manual = new ArrayList<>();
    private List<String> manualClasses = new ArrayList<>();
    private float threshold = 0.30f;
    private String selectedClass = "All objects";
    private TapListener onTap;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    public AnnotatedImageView(Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
    public void setBitmap(Bitmap b) { bitmap = b; invalidate(); }
    public void setHits(List<DetectorEngine.Hit> h) { hits = h; invalidate(); }
    public void setManual(List<PointF> points) { manual = points; invalidate(); }
    public void setManualClasses(List<String> classes) { manualClasses = classes; invalidate(); }
    public void setThreshold(float f) { threshold = f; invalidate(); }
    public void setSelectedClass(String s) { selectedClass = s; invalidate(); }
    public void setOnTap(TapListener listener) { onTap = listener; }

    private RectF viewport() {
        if (bitmap == null || getWidth() == 0 || getHeight() == 0) return new RectF();
        float scale = Math.min((float) getWidth() / bitmap.getWidth(),
                               (float) getHeight() / bitmap.getHeight());
        float drawW = bitmap.getWidth() * scale;
        float drawH = bitmap.getHeight() * scale;
        float x = (getWidth() - drawW) * 0.5f;
        float y = (getHeight() - drawH) * 0.5f;
        return new RectF(x, y, x + drawW, y + drawH);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(12, 20, 37));
        if (bitmap == null) {
            p.setColor(Color.rgb(116, 137, 174));
            p.setTextSize(18f * getResources().getDisplayMetrics().scaledDensity);
            p.setStyle(Paint.Style.FILL);
            String line1 = "YOUR IMAGE APPEARS HERE";
            String line2 = "Choose Gallery or Camera above";
            float x = getWidth() / 2f;
            float y = getHeight() / 2f;
            p.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(line1, x, y - 10, p);
            p.setTextSize(13f * getResources().getDisplayMetrics().scaledDensity);
            canvas.drawText(line2, x, y + 24, p);
            p.setTextAlign(Paint.Align.LEFT);
            return;
        }
        RectF rect = viewport();
        p.setAlpha(255);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.FILL);
        canvas.drawBitmap(bitmap, null, rect, p);
        canvas.save();
        canvas.translate(rect.left, rect.top);
        float scale = rect.width() / bitmap.getWidth();
        canvas.scale(scale, scale);
        drawAnnotations(canvas, 1 / scale);
        canvas.restore();
    }

    private boolean matches(DetectorEngine.Hit h) {
        return !h.excluded && h.score >= threshold &&
               ("All objects".equals(selectedClass) || selectedClass.equals(h.label));
    }

    private int colorFor(String text) {
        int[] colors = {0xFF36D9F7, 0xFFAD96FF, 0xFF3DE6AF, 0xFFFFC76F, 0xFFFF8AB1};
        return colors[(text.hashCode() & Integer.MAX_VALUE) % colors.length];
    }

    private void drawAnnotations(Canvas canvas, float ratio) {
        float stroke = Math.max(2.2f, 2.5f * ratio);
        float labelSize = Math.max(14f, 13f * ratio);
        float pad = Math.max(4f, 4f * ratio);
        float rad = Math.max(6f, 6f * ratio);
        for (DetectorEngine.Hit h : hits) {
            if (!matches(h)) continue;
            int color = colorFor(h.label);
            p.setColor(color);
            p.setAlpha(255);
            p.setStrokeWidth(stroke);
            p.setStyle(Paint.Style.STROKE);
            canvas.drawRoundRect(h.box, 8f * ratio, 8f * ratio, p);
            p.setStyle(Paint.Style.FILL);
            p.setTextSize(labelSize);
            p.setFakeBoldText(true);
            String caption = String.format(Locale.US, "%s  %d%%", h.label, Math.round(h.score * 100));
            float w = p.measureText(caption) + pad * 2;
            float left = Math.max(0, h.box.left);
            float top = Math.max(labelSize + 2 * pad, h.box.top);
            float y = top - labelSize - 2 * pad;
            p.setColor(color);
            canvas.drawRoundRect(new RectF(left, y, left + w, y + labelSize + 2 * pad), rad, rad, p);
            p.setColor(0xFF0A1329);
            canvas.drawText(caption, left + pad, y + labelSize + pad, p);
            p.setFakeBoldText(false);
        }

        int marks = Math.min(manual.size(), manualClasses.size());
        for (int i=0; i<marks; i++) {
            if (!"All objects".equals(selectedClass) &&
                    !selectedClass.equals(manualClasses.get(i))) continue;
            PointF point = manual.get(i);
            float radius = Math.max(11f, 12f * ratio);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke);
            p.setColor(0xFFFFD66E);
            canvas.drawCircle(point.x, point.y, radius, p);
            canvas.drawLine(point.x-radius*1.25f, point.y,
                            point.x+radius*1.25f, point.y, p);
            canvas.drawLine(point.x, point.y-radius*1.25f,
                            point.x, point.y+radius*1.25f, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null) return super.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_UP) {
            RectF area = viewport();
            if (area.contains(event.getX(), event.getY())) {
                float x = (event.getX() - area.left) * bitmap.getWidth() / area.width();
                float y = (event.getY() - area.top) * bitmap.getHeight() / area.height();
                if (onTap != null) onTap.onImageTap(x, y);
            }
            performClick();
            return true;
        }
        return event.getAction() == MotionEvent.ACTION_DOWN || super.onTouchEvent(event);
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    public Bitmap export() {
        if (bitmap == null) return null;
        Bitmap out = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),
                                         Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        p.setColor(Color.WHITE);
        p.setAlpha(255);
        p.setStyle(Paint.Style.FILL);
        canvas.drawBitmap(bitmap, 0, 0, p);
        drawAnnotations(canvas, Math.max(1f, bitmap.getWidth() / 1100f));
        return out;
    }
}
