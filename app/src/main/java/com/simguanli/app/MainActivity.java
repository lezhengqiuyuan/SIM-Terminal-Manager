package com.simguanli.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.*;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String APP_URL = "https://sim.jh-xsz.cn/index.php?page=admin";
    private static final String BASE_URL = "https://sim.jh-xsz.cn";

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraImageUri;
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private SimNetworkInfo simNetworkInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.rootView);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(0, top, 0, 0);
            webView.setPadding(0, 0, 0, bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        progressBar = findViewById(R.id.progressBar);
        webView = findViewById(R.id.webView);

        fileChooserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (filePathCallback != null) {
                        Uri[] results = null;
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            String ds = result.getData().getDataString();
                            if (ds != null) results = new Uri[]{Uri.parse(ds)};
                            else {
                                ClipData cd = result.getData().getClipData();
                                if (cd != null) {
                                    results = new Uri[cd.getItemCount()];
                                    for (int i = 0; i < cd.getItemCount(); i++)
                                        results[i] = cd.getItemAt(i).getUri();
                                }
                            }
                        }
                        filePathCallback.onReceiveValue(results);
                        filePathCallback = null;
                    }
                });

        cameraLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(success && cameraImageUri != null ? new Uri[]{cameraImageUri} : null);
                filePathCallback = null;
            }
        });

        setupWebView();

        // SIM info + JS Bridge
        simNetworkInfo = new SimNetworkInfo(this);
        webView.addJavascriptInterface(new SimJsBridge(simNetworkInfo), "SimBridge");
        simNetworkInfo.registerQciListener();
        requestPermissions();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 100);

        if (savedInstanceState == null) webView.loadUrl(APP_URL);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) s.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView v, String u, Bitmap f) { progressBar.setVisibility(View.VISIBLE); }
            @Override public void onPageFinished(WebView v, String u) { progressBar.setVisibility(View.GONE); }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                progressBar.setVisibility(View.GONE);
                if (r.isForMainFrame()) showErrorPage();
            }
            @Override public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e) { h.cancel(); }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                if (!u.startsWith(BASE_URL) && URLUtil.isValidUrl(u)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); }
                    catch (ActivityNotFoundException ignored) {}
                    return true;
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                progressBar.setProgress(p);
                if (p == 100) progressBar.setVisibility(View.GONE);
            }
            @Override public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                Intent intent = params.createIntent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    Intent ci = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    if (ci.resolveActivity(getPackageManager()) != null) {
                        File pf = createImageFile();
                        if (pf != null) {
                            cameraImageUri = getUriForFile(pf);
                            ci.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
                            Intent ch = new Intent(Intent.ACTION_CHOOSER);
                            ch.putExtra(Intent.EXTRA_INTENT, intent);
                            ch.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{ci});
                            fileChooserLauncher.launch(ch);
                            return true;
                        }
                    }
                } catch (Exception ignored) {}
                try { fileChooserLauncher.launch(intent); }
                catch (ActivityNotFoundException e) { filePathCallback.onReceiveValue(null); filePathCallback = null; }
                return true;
            }
        });

        webView.setDownloadListener((url, ua, cd, mt, cl) -> {
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            r.setMimeType(mt);
            r.addRequestHeader("User-Agent", ua);
            r.setTitle(URLUtil.guessFileName(url, cd, mt));
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, cd, mt));
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm != null) dm.enqueue(r);
            Toast.makeText(this, "开始下载", Toast.LENGTH_SHORT).show();
        });
    }

    private void showErrorPage() {
        webView.loadDataWithBaseURL(null,
                "<html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                        + "<style>body{display:flex;flex-direction:column;align-items:center;justify-content:center;"
                        + "height:100vh;margin:0;font-family:sans-serif;background:#f5f7fa;color:#333;text-align:center}"
                        + "h2{margin-bottom:10px}.btn{display:inline-block;margin-top:20px;padding:12px 32px;"
                        + "background:#1a73e8;color:#fff;border-radius:8px;text-decoration:none}"
                        + "</style></head><body><h2>网络连接失败</h2><p>请检查网络后重试</p>"
                        + "<a class='btn' href='javascript:location.reload()'>重新加载</a></body></html>",
                "text/html", "UTF-8", null);
    }

    private File createImageFile() {
        try {
            return File.createTempFile("JPEG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date()) + "_",
                    ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES));
        } catch (IOException e) { return null; }
    }

    private Uri getUriForFile(File f) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f)
                : Uri.fromFile(f);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) { webView.goBack(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        webView.restoreState(savedInstanceState);
    }

    @Override protected void onDestroy() {
        if (webView != null) { webView.loadUrl("about:blank"); webView.clearHistory(); webView.destroy(); webView = null; }
        super.onDestroy();
    }

    private void requestPermissions() {
        List<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.READ_PHONE_STATE);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]), 200);
    }

    @Override public void onRequestPermissionsResult(int rc, @NonNull String[] perms, @NonNull int[] grants) {
        super.onRequestPermissionsResult(rc, perms, grants);
    }
}
