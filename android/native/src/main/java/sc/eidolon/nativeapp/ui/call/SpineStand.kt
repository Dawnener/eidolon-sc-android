package sc.eidolon.nativeapp.ui.call

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewAssetLoader
import sc.eidolon.nativeapp.model.Outfit

enum class StandPhase { Loading, Ready, Failed }

/**
 * 通话层上的 Spine 立绘。
 *
 * runtime 是官方 spine-ts 3.6 的原样副本（assets/stand/spine-webgl.js，见 ADR-0009），
 * 页面只做两件事：按服务器给的衣装目录载入一套立绘，按通话状态推进待机、表情和口型。
 * 画布透明，铺在背景图和界面之间。
 *
 * 加载失败或设备不支持 WebGL 时回调 [StandPhase.Failed]，调用方可以退回头像。
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun SpineStand(
    outfit: Outfit,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    speaking: Boolean = false,
    mood: String = "calm",
    nod: String? = null,
    nodId: Int = 0,
    onPhase: (StandPhase) -> Unit = {},
) {
    val phaseCallback by rememberUpdatedState(onPhase)
    val pausedNow by rememberUpdatedState(paused)
    val speakingNow by rememberUpdatedState(speaking)
    val moodNow by rememberUpdatedState(mood)
    val nodNow by rememberUpdatedState(nod)
    val nodIdNow by rememberUpdatedState(nodId)
    val lifecycleOwner = LocalLifecycleOwner.current
    var lifetimePaused by remember { mutableStateOf(false) }

    val effectivePaused = pausedNow || lifetimePaused
    val direction = standDirection(speakingNow, moodNow, nodNow, nodIdNow)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                -> lifetimePaused = true

                Lifecycle.Event.ON_RESUME,
                Lifecycle.Event.ON_START,
                -> lifetimePaused = false

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val handler = Handler(Looper.getMainLooper())
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()
            WebView(context).apply {
                Log.i(TAG, "creating stand webview for ${outfit.url}")
                setBackgroundColor(Color.TRANSPARENT)
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    mediaPlaybackRequiresUserGesture = false
                    // 局域网部署的服务器还是 http://；本地页面本身是 https://appassets。
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        Log.i(
                            TAG,
                            "js ${message.messageLevel()} ${message.message()} " +
                                "(${message.sourceId()}:${message.lineNumber()})",
                        )
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? =
                        loader.shouldInterceptRequest(request.url)

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean = true

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        Log.i(
                            TAG,
                            "stand page finished $url webview=${view.width}x${view.height} " +
                                "measured=${view.measuredWidth}x${view.measuredHeight}",
                        )
                        // 页面可能晚于第一次 update 才加载完；这里再推一次最新状态。
                        applyStandState(
                            view,
                            pausedNow || lifetimePaused,
                            standDirection(speakingNow, moodNow, nodNow, nodIdNow),
                        )
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        Log.w(
                            TAG,
                            "stand page error ${request.url}: ${error.errorCode} ${error.description}",
                        )
                    }
                }
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun ready() {
                            Log.i(TAG, "stand ready")
                            handler.post { phaseCallback(StandPhase.Ready) }
                        }

                        @JavascriptInterface
                        fun failed(message: String) {
                            Log.w(TAG, "stand failed: $message")
                            handler.post { phaseCallback(StandPhase.Failed) }
                        }
                    },
                    "EidolonStand",
                )
                loadUrl(
                    "https://appassets.androidplatform.net/assets/stand/stand.html" +
                        "?outfit=" + Uri.encode(outfit.url),
                )
                post {
                    Log.i(
                        TAG,
                        "stand webview laid out ${width}x${height} " +
                            "density=${resources.displayMetrics.density}",
                    )
                }
            }
        },
        update = { view ->
            applyStandState(view, effectivePaused, direction)
        },
        onRelease = { view ->
            view.removeJavascriptInterface("EidolonStand")
            view.stopLoading()
            view.destroy()
        },
    )
}

private fun standDirection(
    speaking: Boolean,
    mood: String,
    nod: String?,
    nodId: Int,
): String {
    val nodJson = if (nod != null) """{"kind":${jsonQuote(nod)},"id":$nodId}""" else "null"
    return """{"mood":${jsonQuote(mood)},"speaking":$speaking,"looking":false,"nod":$nodJson}"""
}

private fun jsonQuote(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

private const val TAG = "EidolonStand"

private fun applyStandState(view: WebView, paused: Boolean, direction: String) {
    val js = "window.stand&&(window.stand.setDirection($direction)," +
        "window.stand.setPaused(${if (paused) "true" else "false"}))"
    view.evaluateJavascript(js, null)
}
