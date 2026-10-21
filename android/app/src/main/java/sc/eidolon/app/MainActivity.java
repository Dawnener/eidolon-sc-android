package sc.eidolon.app;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 把局域网上的 Eidolon-SC 网页装进一个全屏 WebView。
 *
 * 只做壳该做的事：加载地址、授权麦克风、选择文件、下载、返回键和外部链接。
 * 页面本身、样式和路由都在 Next.js 那边，这里不注入任何脚本。
 */
public class MainActivity extends Activity {
    private static final String PREFS = "eidolon";
    private static final String KEY_URL = "server_url";
    private static final int REQ_FILE = 1001;
    private static final int REQ_MIC = 1002;

    /**
     * 网页的顶层页面。在这些页面上按返回是退出应用，而不是沿浏览历史退回上一个页面：
     * 应用内导航是 push 进历史栈的，从主界面进设置再回主界面，历史里还留着设置，
     * 照网页的方式返回就会退回设置。
     */
    private static final Set<String> ROOT_PATHS =
            new HashSet<>(Arrays.asList("/", "/conversations", "/settings", "/gifts", "/admin"));

    private WebView webView;
    private ProgressBar loading;
    private View setupView;
    private EditText urlInput;
    private TextView errorText;

    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest micRequest;
    private String serverUrl;
    private String loadedOrigin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.web);
        loading = findViewById(R.id.loading);
        setupView = findViewById(R.id.setup);
        urlInput = findViewById(R.id.url);
        errorText = findViewById(R.id.error);
        Button open = findViewById(R.id.open);

        configureWebView();

        open.setOnClickListener(v -> openFromInput());
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                openFromInput();
                return true;
            }
            return false;
        });

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = prefs.getString(KEY_URL, null);
        if (saved == null) {
            serverUrl = getString(R.string.default_server_url);
            showSetup(getString(R.string.first_run));
        } else {
            serverUrl = saved;
            if (savedInstanceState != null) {
                // 进程被回收后恢复页面；否则照常加载一次
                webView.restoreState(savedInstanceState);
                loadedOrigin = originOf(serverUrl);
                setupView.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            } else {
                loadServer();
            }
        }
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true); // 文件选择器给的是 content:// URI
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        WebView.setWebContentsDebuggingEnabled(debuggable);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl());
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                loading.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                loading.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // 子资源失败不挡页面；主框架失败才把地址设置浮层叫出来。
                // 主动取消的导航（例如换地址时）会以 ERR_ABORTED 报上来，那不是故障。
                String description = String.valueOf(error.getDescription());
                if (request.isForMainFrame() && !description.contains("ERR_ABORTED")) {
                    showSetup(getString(R.string.load_failed, description));
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> grantAudioIfPossible(request));
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (micRequest == request) {
                    micRequest = null;
                }
            }

            @Override
            public boolean onShowFileChooser(
                    WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }
                fileCallback = callback;
                try {
                    Intent intent = params.createIntent();
                    if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    }
                    startActivityForResult(
                            Intent.createChooser(intent, getString(R.string.choose_file)), REQ_FILE);
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        webView.setDownloadListener(this::startDownload);
    }

    /** 只把同 host 的页面留在 WebView；别的交给系统浏览器，免得登录页、GitHub 之类困在壳里。 */
    private boolean handleNavigation(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme();
        if (scheme.equals("http") || scheme.equals("https")) {
            Uri server = Uri.parse(serverUrl);
            if (uri.getHost() != null && uri.getHost().equals(server.getHost())) {
                return false;
            }
            openExternal(uri);
            return true;
        }
        if (scheme.equals("about") || scheme.equals("data") || scheme.equals("blob")) {
            return false;
        }
        openExternal(uri);
        return true;
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_SHORT).show();
        }
    }

    private void grantAudioIfPossible(PermissionRequest request) {
        boolean wantsAudio = false;
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                wantsAudio = true;
            }
        }
        if (!wantsAudio) {
            request.deny();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            request.grant(new String[] {PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            return;
        }
        if (micRequest != null) {
            micRequest.deny();
        }
        micRequest = request;
        requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, REQ_MIC);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_MIC || micRequest == null) {
            return;
        }
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            micRequest.grant(new String[] {PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        } else {
            micRequest.deny();
        }
        micRequest = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    result = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        result[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    result = new Uri[] {data.getData()};
                }
            }
            fileCallback.onReceiveValue(result);
            fileCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void startDownload(
            String url, String userAgent, String contentDisposition, String mimeType, long length) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            // 分享图是 blob: URL，DownloadManager 接不了
            Toast.makeText(this, R.string.download_unsupported, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            if (mimeType != null) {
                request.setMimeType(mimeType);
            }
            if (userAgent != null) {
                request.addRequestHeader("User-Agent", userAgent);
            }
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) {
                request.addRequestHeader("Cookie", cookie);
            }
            request.setTitle(name);
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(
                    this, Environment.DIRECTORY_DOWNLOADS, name);
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            manager.enqueue(request);
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void openFromInput() {
        String normalized = normalizeUrl(urlInput.getText().toString());
        if (normalized == null) {
            errorText.setText(R.string.bad_url);
            return;
        }
        serverUrl = normalized;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_URL, serverUrl).apply();
        loadServer();
    }

    /** 补上 http://、去掉末尾斜杠；不是 http(s) 地址就返回 null。 */
    private String normalizeUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (!value.contains("://")) {
            value = "http://" + value;
        }
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            return null;
        }
        if (uri.getHost() == null) {
            return null;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private void loadServer() {
        String nextOrigin = originOf(serverUrl);
        if (loadedOrigin != null && !loadedOrigin.equals(nextOrigin)) {
            // 换了一台服务器：旧的会话 cookie 不能带过去
            CookieManager cookies = CookieManager.getInstance();
            cookies.removeAllCookies(null);
            cookies.flush();
            webView.clearCache(true);
            WebStorage.getInstance().deleteAllData();
        }
        loadedOrigin = nextOrigin;

        setupView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        loading.setVisibility(View.VISIBLE);
        webView.loadUrl(serverUrl);
    }

    private static String originOf(String url) {
        Uri uri = Uri.parse(url);
        int port = uri.getPort();
        if (port == -1) {
            port = "https".equals(uri.getScheme()) ? 443 : 80;
        }
        return uri.getScheme() + "://" + uri.getHost() + ":" + port;
    }

    private void showSetup(String message) {
        loading.setVisibility(View.GONE);
        webView.setVisibility(View.INVISIBLE);
        setupView.setVisibility(View.VISIBLE);
        errorText.setText(message);
        urlInput.setText(serverUrl);
    }

    @Override
    public void onBackPressed() {
        if (setupView.getVisibility() == View.VISIBLE || webView == null) {
            super.onBackPressed();
            return;
        }
        String path = pathOf(webView.getUrl());
        if (ROOT_PATHS.contains(path)) {
            // 顶层页面：像原生应用一样退出，不再退回网页历史
            super.onBackPressed();
            return;
        }
        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }
        // 直接打开的子页面没有可回退的历史，退回它的上级
        webView.loadUrl(originOf(serverUrl) + parentOf(path));
    }

    /** 去掉查询串和末尾斜杠后的路径；拿不到时当作根路径。 */
    private static String pathOf(String url) {
        if (url == null) {
            return "/";
        }
        String path = Uri.parse(url).getPath();
        if (path == null || path.isEmpty()) {
            return "/";
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static String parentOf(String path) {
        if (path.startsWith("/conversation/")) {
            return "/conversations";
        }
        if (path.startsWith("/settings/")) {
            return "/settings";
        }
        if (path.startsWith("/admin/")) {
            return "/admin";
        }
        return "/";
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
