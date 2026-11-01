package com.gk.tonegenerator;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class MainActivity extends Activity {
    private WebView webView;
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
        settings.setMediaPlaybackRequiresUserGesture(true);
        webView.setWebChromeClient(new WebChromeClient());
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
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
