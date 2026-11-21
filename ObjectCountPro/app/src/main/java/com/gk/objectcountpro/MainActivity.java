package com.gk.objectcountpro;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_GALLERY = 81;
    private static final int REQUEST_CAMERA = 82;
    private static final int BG = 0xFF080E1C;
    private static final int WHITE = 0xFFF5F8FF;
    private static final int MUTED = 0xFFA1B2CD;
    private static final int CYAN = 0xFF40D6F7;
    private static final int PURPLE = 0xFF9E91FF;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private DetectorEngine detector;
    private AnnotatedImageView preview;
    private TextView countLabel, statusLabel, hintLabel, confidenceLabel, breakdownLabel;
    private Spinner categorySpinner;
    private ArrayAdapter<String> categoryAdapter;
    private Button markButton, saveButton, shareButton, resetButton;
    private Switch enhancedSwitch;

    private Bitmap currentImage;
    private Uri cameraUri;
    private File temporaryCameraFile;
    private List<DetectorEngine.Hit> hits = new ArrayList<>();
    private final List<PointF> manualMarks = new ArrayList<>();
    private float minConfidence = 0.30f;
    private String selectedCategory = "All objects";
    private boolean isMarkMode = false;
    private boolean enhanced = false;
    private int generation = 0;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildUi();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private GradientDrawable gradient(int start, int end, int radius) {
        GradientDrawable d = new GradientDrawable(
             GradientDrawable.Orientation.TL_BR, new int[] {start, end});
        d.setCornerRadius(dp(radius));
        return d;
    }

    private LinearLayout vertical(int spacing) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(spacing), dp(spacing), dp(spacing), dp(spacing));
        return l;
    }

    private void addGap(LinearLayout parent, int height) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private Button button(String title, int startColor, int endColor) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(WHITE);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        b.setBackground(gradient(startColor, endColor, 16));
        b.setMinHeight(dp(54));
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        b.setElevation(dp(2));
        return b;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = vertical(18);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = text("OBJECTCOUNT  PRO", 24, WHITE, true);
        title.setLetterSpacing(0.07f);
        root.addView(title);
        TextView subtitle = text("OFFLINE AI IMAGE ANALYZER  •  ANDROID", 11, CYAN, true);
        subtitle.setLetterSpacing(0.11f);
        root.addView(subtitle);
        addGap(root, 18);

        LinearLayout statCard = vertical(20);
        statCard.setBackground(gradient(0xFF1B254B, 0xFF10192E, 22));
        TextView kicker = text("TOTAL OBJECTS IN IMAGE", 12, MUTED, true);
        kicker.setLetterSpacing(0.1f);
        statCard.addView(kicker);
        countLabel = text("0", 70, WHITE, true);
        countLabel.setIncludeFontPadding(false);
        statCard.addView(countLabel);
        statusLabel = text("READY FOR A PHOTO", 13, CYAN, true);
        statCard.addView(statusLabel);
        root.addView(statCard);
        addGap(root, 16);

        LinearLayout sourceRow = new LinearLayout(this);
        sourceRow.setOrientation(LinearLayout.HORIZONTAL);
        Button gallery = button("▧  Open gallery", 0xFF365DDF, 0xFF5337B4);
        Button camera = button("◎  Take photo", 0xFF1388A8, 0xFF07577F);
        LinearLayout.LayoutParams half1 = new LinearLayout.LayoutParams(0, dp(56), 1);
        half1.setMargins(0,0,dp(7),0);
        sourceRow.addView(gallery, half1);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(56), 1);
        half2.setMargins(dp(7),0,0,0);
        sourceRow.addView(camera, half2);
        root.addView(sourceRow);
        gallery.setOnClickListener(v -> openGallery());
        camera.setOnClickListener(v -> openCamera());
        addGap(root, 16);

        FrameLayout imageContainer = new FrameLayout(this);
        imageContainer.setBackground(background(0xFF0D1528, 20));
        imageContainer.setClipToOutline(true);
        preview = new AnnotatedImageView(this);
        imageContainer.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(imageContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(370)));
        preview.setOnTap((x,y) -> handleImageTap(x,y));
        addGap(root, 8);

        hintLabel = text("Boxes show each detected object • tap a box to exclude it", 12, MUTED, false);
        root.addView(hintLabel);
        addGap(root, 18);

        LinearLayout controls = vertical(16);
        controls.setBackground(background(0xFF141C30, 20));
        TextView toolsHeader = text("DETECTION CONTROLS", 14, WHITE, true);
        toolsHeader.setLetterSpacing(0.08f);
        controls.addView(toolsHeader);
        addGap(controls, 11);

        confidenceLabel = text("Minimum confidence  •  30%", 13, MUTED, false);
        controls.addView(confidenceLabel);
        SeekBar seek = new SeekBar(this);
        seek.setMax(70);
        seek.setProgress(15);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(CYAN));
        controls.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s,int progress,boolean fromUser) {
                minConfidence = (15 + progress) / 100f;
                confidenceLabel.setText("Minimum confidence  •  " + Math.round(minConfidence * 100) + "%");
                if (currentImage != null) { updateCategoryChoices(); refreshView(); }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        TextView filterTitle = text("COUNT BY OBJECT TYPE", 12, MUTED, true);
        controls.addView(filterTitle);
        categorySpinner = new Spinner(this);
        categoryAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
                                                   new ArrayList<>()) {
            @Override public View getView(int pos, View convert, ViewGroup parent) {
                TextView t = text(getItem(pos), 16, WHITE, true);
                t.setPadding(dp(12), dp(12), dp(12), dp(12));
                return t;
            }
            @Override public View getDropDownView(int pos, View convert, ViewGroup parent) {
                TextView t = text(getItem(pos), 16, WHITE, false);
                t.setPadding(dp(16), dp(14), dp(16), dp(14));
                t.setBackgroundColor(0xFF1D2A46);
                return t;
            }
        };
        categorySpinner.setAdapter(categoryAdapter);
        categorySpinner.setBackground(background(0xFF26324D, 12));
        controls.addView(categorySpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                String chosen = categoryAdapter.getItem(pos);
                if (chosen != null) selectedCategory = chosen;
                refreshView();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        categoryAdapter.add("All objects");

        enhancedSwitch = new Switch(this);
        enhancedSwitch.setText("Enhanced scan for small objects");
        enhancedSwitch.setTextColor(WHITE);
        enhancedSwitch.setTextSize(13);
        enhancedSwitch.setChecked(false);
        controls.addView(enhancedSwitch);
        enhancedSwitch.setOnCheckedChangeListener((b, checked) -> {
            enhanced = checked;
            if (currentImage != null) runDetection();
        });

        breakdownLabel = text("No detections yet", 13, MUTED, false);
        breakdownLabel.setLineSpacing(dp(5), 1);
        controls.addView(breakdownLabel);
        root.addView(controls);
        addGap(root, 14);

        LinearLayout correctRow = new LinearLayout(this);
        markButton = button("+  Mark missed", 0xFF6D4EAB, 0xFF433B8F);
        resetButton = button("↺  Reset edits", 0xFF303D53, 0xFF243149);
        LinearLayout.LayoutParams h1 = new LinearLayout.LayoutParams(0, dp(54), 1);
        h1.setMargins(0,0,dp(7),0);
        correctRow.addView(markButton, h1);
        LinearLayout.LayoutParams h2 = new LinearLayout.LayoutParams(0, dp(54), 1);
        h2.setMargins(dp(7),0,0,0);
        correctRow.addView(resetButton, h2);
        root.addView(correctRow);
        markButton.setOnClickListener(v -> {
            isMarkMode = !isMarkMode;
            markButton.setText(isMarkMode ? "✓  Marking ON" : "+  Mark missed");
            hintLabel.setText(isMarkMode
                    ? "Tap each missing object in the photo to add one to the count."
                    : "Tap a detected box to exclude it from the count.");
        });
        resetButton.setOnClickListener(v -> {
            for (DetectorEngine.Hit h : hits) h.excluded = false;
            manualMarks.clear();
            isMarkMode = false;
            markButton.setText("+  Mark missed");
            hintLabel.setText("Tap a detected box to exclude it from the count.");
            updateCategoryChoices();
            refreshView();
        });
        addGap(root, 14);

        LinearLayout exportRow = new LinearLayout(this);
        saveButton = button("↓  Save image", 0xFF15725E, 0xFF0A594E);
        shareButton = button("↗  Share result", 0xFF234E9B, 0xFF31377F);
        LinearLayout.LayoutParams e1 = new LinearLayout.LayoutParams(0, dp(54), 1);
        e1.setMargins(0,0,dp(7),0);
        exportRow.addView(saveButton, e1);
        LinearLayout.LayoutParams e2 = new LinearLayout.LayoutParams(0, dp(54), 1);
        e2.setMargins(dp(7),0,0,0);
        exportRow.addView(shareButton, e2);
        root.addView(exportRow);
        saveButton.setEnabled(false);
        shareButton.setEnabled(false);
        saveButton.setOnClickListener(v -> exportImage(false));
        shareButton.setOnClickListener(v -> exportImage(true));

        addGap(root, 18);
        TextView note = text("PRIVATE BY DESIGN • ALL IMAGE PROCESSING STAYS ON YOUR DEVICE", 11, MUTED, true);
        note.setGravity(Gravity.CENTER);
        note.setLetterSpacing(0.04f);
        root.addView(note);
        TextView copyright = text("Developed By : Gaurav Kharva", 11, 0xFF7487A8, false);
        copyright.setGravity(Gravity.END);
        root.addView(copyright);
        addGap(root, 15);
    }

    private void openGallery() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try { startActivityForResult(i, REQUEST_GALLERY); }
        catch (Exception e) { message("Cannot open photo picker: " + e.getMessage()); }
    }

    private void openCamera() {
        try {
            temporaryCameraFile = File.createTempFile("objectcount_camera_", ".jpg", getCacheDir());
            cameraUri = FileProvider.getUriForFile(this,
                    "com.gk.objectcountpro.provider", temporaryCameraFile);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                       Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(i, REQUEST_CAMERA);
        } catch (Exception e) {
            message("Camera unavailable: " + e.getMessage());
        }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        if (requestCode == REQUEST_GALLERY && data != null && data.getData() != null) {
            loadImage(data.getData());
        } else if (requestCode == REQUEST_CAMERA && cameraUri != null) {
            loadImage(cameraUri);
        }
    }

    private Bitmap decodeBitmap(Uri uri) throws Exception {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("Image could not be read.");
            BitmapFactory.decodeStream(in, null, opts);
        }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) throw new Exception("Unsupported image format.");
        int sampleSize = 1;
        while (Math.max(opts.outWidth / sampleSize, opts.outHeight / sampleSize) > 2200) {
            sampleSize *= 2;
        }
        BitmapFactory.Options real = new BitmapFactory.Options();
        real.inSampleSize = sampleSize;
        real.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap decoded;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("Image could not be read.");
            decoded = BitmapFactory.decodeStream(in, null, real);
        }
        if (decoded == null) throw new Exception("Image format cannot be decoded.");
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in != null) orientation = new ExifInterface(in)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception ignored) { }
        Matrix transform = new Matrix();
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90) transform.postRotate(90);
        if (orientation == ExifInterface.ORIENTATION_ROTATE_180) transform.postRotate(180);
        if (orientation == ExifInterface.ORIENTATION_ROTATE_270) transform.postRotate(270);
        if (orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL) transform.postScale(-1,1);
        if (orientation == ExifInterface.ORIENTATION_TRANSPOSE) { transform.postRotate(90); transform.postScale(-1,1); }
        if (orientation == ExifInterface.ORIENTATION_TRANSVERSE) { transform.postRotate(270); transform.postScale(-1,1); }
        if (!transform.isIdentity()) {
            Bitmap rotated = Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),
                                                 decoded.getHeight(),transform,true);
            if (rotated != decoded) decoded.recycle();
            decoded = rotated;
        }
        return decoded;
    }

    private void loadImage(Uri uri) {
        final int job = ++generation;
        statusLabel.setText("OPENING IMAGE…");
        statusLabel.setTextColor(CYAN);
        executor.execute(() -> {
            try {
                Bitmap loaded = decodeBitmap(uri);
                runOnUiThread(() -> {
                    if (job != generation || isFinishing()) { loaded.recycle(); return; }
                    Bitmap old = currentImage;
                    currentImage = loaded;
                    preview.setBitmap(currentImage);
                    hits.clear();
                    manualMarks.clear();
                    selectedCategory = "All objects";
                    isMarkMode = false;
                    markButton.setText("+  Mark missed");
                    updateCategoryChoices();
                    refreshView();
                    runDetection();
                    if (old != null && old != loaded) old.recycle();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (job == generation) {
                        statusLabel.setText("COULD NOT OPEN IMAGE");
                        message(e.getMessage());
                    }
                });
            }
        });
    }

    private void runDetection() {
        if (currentImage == null) return;
        final int job = ++generation;
        final Bitmap image = currentImage;
        final boolean useTiles = enhanced;
        statusLabel.setText(useTiles ? "ENHANCED AI SCAN RUNNING…" : "AI SCAN RUNNING…");
        statusLabel.setTextColor(PURPLE);
        saveButton.setEnabled(false);
        shareButton.setEnabled(false);
        executor.execute(() -> {
            try {
                if (detector == null) detector = new DetectorEngine(getApplicationContext());
                List<DetectorEngine.Hit> result = detector.analyze(image, useTiles);
                runOnUiThread(() -> {
                    if (job != generation || isFinishing()) return;
                    hits = result;
                    statusLabel.setText("OFFLINE ANALYSIS COMPLETE");
                    statusLabel.setTextColor(0xFF41E3AE);
                    updateCategoryChoices();
                    refreshView();
                    saveButton.setEnabled(true);
                    shareButton.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (job != generation) return;
                    statusLabel.setText("DETECTION MODEL ERROR");
                    statusLabel.setTextColor(0xFFFFA3A3);
                    message("Detector could not run: " + e.getMessage());
                });
            }
        });
    }

    private void updateCategoryChoices() {
        if (categoryAdapter == null) return;
        Set<String> sorted = new TreeSet<>();
        for (DetectorEngine.Hit h : hits)
            if (h.score >= minConfidence && !h.excluded) sorted.add(h.label);
        if (!manualMarks.isEmpty()) sorted.add("Manual");
        List<String> options = new ArrayList<>();
        options.add("All objects");
        options.addAll(sorted);
        if (!options.contains(selectedCategory)) selectedCategory = "All objects";
        categoryAdapter.clear();
        categoryAdapter.addAll(options);
        categoryAdapter.notifyDataSetChanged();
        categorySpinner.setSelection(options.indexOf(selectedCategory));
    }

    private void refreshView() {
        if (preview == null) return;
        preview.setHits(hits);
        preview.setThreshold(minConfidence);
        preview.setSelectedClass(selectedCategory);
        preview.setManual(manualMarks);
        Map<String,Integer> grouped = new LinkedHashMap<>();
        for (DetectorEngine.Hit h : hits) {
            if (!h.excluded && h.score >= minConfidence)
                grouped.put(h.label, grouped.getOrDefault(h.label,0) + 1);
        }
        if (!manualMarks.isEmpty())
            grouped.put("Manual", manualMarks.size());

        int total = 0;
        List<String> summary = new ArrayList<>();
        for (Map.Entry<String,Integer> e : grouped.entrySet()) {
            summary.add(e.getKey() + "  " + e.getValue());
            if ("All objects".equals(selectedCategory) ||
                selectedCategory.equals(e.getKey())) total += e.getValue();
        }
        countLabel.setText(String.valueOf(total));
        breakdownLabel.setText(summary.isEmpty()
            ? "No objects found at this confidence. Try a lower confidence, enhanced scan, or manual markers."
            : "Detected categories\n" + android.text.TextUtils.join("    •    ", summary));
    }

    private void handleImageTap(float x,float y) {
        if (currentImage == null) return;
        if (isMarkMode) {
            manualMarks.add(new PointF(x,y));
            updateCategoryChoices();
            refreshView();
            return;
        }
        DetectorEngine.Hit chosen = null;
        float smallestArea = Float.MAX_VALUE;
        for (DetectorEngine.Hit h : hits) {
            if (h.score < minConfidence || !h.box.contains(x,y)) continue;
            if (!"All objects".equals(selectedCategory) && !h.label.equals(selectedCategory)) continue;
            float area = h.box.width() * h.box.height();
            if (area < smallestArea) { smallestArea=area; chosen=h; }
        }
        if (chosen != null) {
            chosen.excluded = !chosen.excluded;
            updateCategoryChoices();
            refreshView();
            message(chosen.excluded ? "Object removed from count." : "Object restored.");
        } else {
            message("Use '+ Mark missed' to add a missing object.");
        }
    }

    private void exportImage(boolean share) {
        if (currentImage == null) return;
        Bitmap annotated = preview.export();
        if (annotated == null) return;
        statusLabel.setText(share ? "PREPARING IMAGE TO SHARE…" : "SAVING ANNOTATED PHOTO…");
        executor.execute(() -> {
            try {
                Uri uri = saveToPictures(annotated);
                runOnUiThread(() -> {
                    statusLabel.setText("ANNOTATED IMAGE SAVED");
                    statusLabel.setTextColor(0xFF41E3AE);
                    if (share) {
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType("image/jpeg");
                        i.putExtra(Intent.EXTRA_STREAM,uri);
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(i,"Share your object count"));
                    } else {
                        message("Saved in Pictures / ObjectCountPRO");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusLabel.setText("SAVE ERROR");
                    statusLabel.setTextColor(0xFFFFA3A3);
                    message(e.getMessage());
                });
            } finally { annotated.recycle(); }
        });
    }

    private Uri saveToPictures(Bitmap bmp) throws Exception {
        String date = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "ObjectCountPRO_" + date + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ObjectCountPRO");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new Exception("Could not create image in gallery.");
        try {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null || !bmp.compress(Bitmap.CompressFormat.JPEG,92,out))
                    throw new Exception("Image encoding failed.");
            }
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING,0);
            getContentResolver().update(uri, values, null, null);
            return uri;
        } catch (Exception e) {
            getContentResolver().delete(uri,null,null);
            throw e;
        }
    }

    private void message(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }

    @Override protected void onDestroy() {
        generation++;
        executor.shutdownNow();
        super.onDestroy();
        // Avoid recycling a bitmap while inference is still using it.
        if (executor.isTerminated() && currentImage != null) currentImage.recycle();
        if (executor.isTerminated() && detector != null) detector.close();
    }
}
