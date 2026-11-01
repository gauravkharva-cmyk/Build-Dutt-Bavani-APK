package com.gk.tonegenerator;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.WebChromeClient;
import android.webkit.JavascriptInterface;
import android.util.Base64;
import android.widget.Toast;
import android.net.Uri;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView webView;
    private byte[] wavBytes;
    private static final int SAVE_WAV_REQUEST = 404;

    public final class FileBridge {
        @JavascriptInterface public void saveWav(String encoded, String fileName) {
            try {
                final byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
                final String safeName = fileName.replaceAll("[^A-Za-z0-9_.-]", "_");
                runOnUiThread(() -> {
                    wavBytes = bytes;
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("audio/wav");
                    intent.putExtra(Intent.EXTRA_TITLE, safeName);
                    startActivityForResult(intent, SAVE_WAV_REQUEST);
                });
            } catch (Exception ex) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Cannot prepare WAV file", Toast.LENGTH_LONG).show());
            }
        }
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xff0b1421);
        getWindow().setNavigationBarColor(0xff0b1421);
        webView = new WebView(this);
        webView.setBackgroundColor(0xff0b1421);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        webView.addJavascriptInterface(new FileBridge(), "GKFiles");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !url.startsWith("file:///android_asset/");
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode==SAVE_WAV_REQUEST) {
            if(resultCode==RESULT_OK && data!=null && data.getData()!=null && wavBytes!=null) {
                try(OutputStream stream=getContentResolver().openOutputStream(data.getData())) {
                    if (stream==null) throw new Exception("Cannot open file");
                    stream.write(wavBytes);
                    Toast.makeText(this,"WAV waveform saved",Toast.LENGTH_LONG).show();
                }catch(Exception ex) {
                    Toast.makeText(this,"Unable to save WAV",Toast.LENGTH_LONG).show();
                }
            }
            wavBytes=null;
        }
    }
    @Override public void onPause() {
        if (webView != null) {
            webView.evaluateJavascript("if(window.stopSound){window.stopSound();}", null);
            webView.onPause();
        }
        super.onPause();
    }
    @Override public void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }
    @Override public void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
            webView=null;
        }
        super.onDestroy();
    }
}