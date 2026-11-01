package com.gk.sketchstudio;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.util.Base64;

/** Offline drawing workspace. No INTERNET permission and no remote resources. */
public final class MainActivity extends Activity {
    private static final int PICK_FILE = 224;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private final ExecutorService fileExecutor = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(22, 24, 33));
        getWindow().setNavigationBarColor(Color.rgb(22, 24, 33));
        FrameLayout root = new FrameLayout(this);
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                root.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(17, 19, 27));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        webView.addJavascriptInterface(new ExportBridge(), "GKAndroid");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                return !"file".equalsIgnoreCase(request.getUrl().getScheme());
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> callback,
                                                           FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "image/png", "image/jpeg", "image/webp", "image/gif",
                    "application/json", "application/octet-stream"
                });
                try {
                    startActivityForResult(intent, PICK_FILE);
                    return true;
                } catch (Exception ex) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "File picker unavailable", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE && fileCallback != null) {
            Uri[] selected = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                selected = new Uri[]{data.getData()};
            }
            fileCallback.onReceiveValue(selected);
            fileCallback = null;
        }
    }

    private final class ExportBridge {
        @JavascriptInterface public void saveBase64(String mimeType, String filename, String base64Data) {
            // Called from WebView's bridge thread, not the UI thread.
            fileExecutor.execute(() -> {
                Uri uri = null;
                boolean success = false;
                String info = "";
                try {
                    if (!("image/png".equals(mimeType) || "image/jpeg".equals(mimeType)
                            || "application/octet-stream".equals(mimeType))) {
                        throw new IllegalArgumentException("Unsupported export type");
                    }
                    if (filename == null || !filename.matches("[a-zA-Z0-9_ .-]{1,110}\\.(png|jpg|gkdraw)")) {
                        throw new IllegalArgumentException("Invalid export filename");
                    }
                    final byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS + "/GK Sketch Studio");
                    values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                    uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new IllegalStateException("Cannot open Downloads");
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out == null) throw new IllegalStateException("Cannot write file");
                        out.write(bytes);
                        out.flush();
                    }
                    values.clear();
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);
                    success = true;
                } catch (Exception error) {
                    info = error.getMessage() == null ? "Storage error" : error.getMessage();
                    if (uri != null) getContentResolver().delete(uri, null, null);
                }
                final boolean ok = success;
                final String detail = info;
                runOnUiThread(() -> {
                    if (webView != null) {
                        String safeName = org.json.JSONObject.quote(filename);
                        String safeInfo = org.json.JSONObject.quote(detail);
                        webView.evaluateJavascript("window.GKExportComplete(" + safeName + "," +
                                ok + "," + safeInfo + ")", null);
                    }
                });
            });
        }
    }

    @Override protected void onPause() {
        if (webView != null) webView.evaluateJavascript("if(window.saveNow){saveNow(false)}", null);
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        fileExecutor.shutdown();
        if (webView != null) {webView.destroy();webView=null;}
        super.onDestroy();
    }
}
