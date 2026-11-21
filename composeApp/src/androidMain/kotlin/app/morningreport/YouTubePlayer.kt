package app.morningreport

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

actual val inAppVideo: Boolean = true

private const val ORIGIN = "https://app.morningreport"
private val ALLOWED_FRAME_HOSTS = listOf("youtube.com", "youtube-nocookie.com", "google.com", "googlevideo.com", "ytimg.com", "gstatic.com")

/**
 * YouTube's IFrame player in a WebView. Only YouTube frames may load inside; every top-level navigation is blocked,
 * so the player cannot wander off. "ended" comes back through a JavaScript bridge bound to this page only.
 */
@Composable
actual fun YouTubeView(videoId: String, modifier: Modifier, onEnded: () -> Unit) {
    if (!Regex("^[A-Za-z0-9_-]{11}$").matches(videoId)) return
    val holder = remember { arrayOfNulls<WebView>(1) }
    val full = remember { FullscreenVideo() }
    // In fullscreen, back leaves fullscreen first; the next back closes the dialog.
    BackHandler(enabled = full.active) { full.exit() }
    DisposableEffect(Unit) {
        onDispose {
            full.exit(); full.release()
            holder[0]?.apply { loadUrl("about:blank"); stopLoading(); destroy() }
            holder[0] = null
        }
    }
    AndroidView(modifier = modifier, factory = { ctx ->
        val container = FrameLayout(ctx)
        val web = createWebView(ctx, full, onEnded)
        holder[0] = web
        container.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        web.loadDataWithBaseURL("$ORIGIN/", playerHtml(videoId), "text/html", "utf-8", null)
        container
    })
}

private class EndedBridge(val onEnded: () -> Unit) {
    @android.webkit.JavascriptInterface fun ended() { android.os.Handler(android.os.Looper.getMainLooper()).post(onEnded) }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
private fun createWebView(ctx: Context, full: FullscreenVideo, onEnded: () -> Unit): WebView {
    val web = WebView(ctx)
    web.setBackgroundColor(android.graphics.Color.BLACK)
    web.settings.apply {
        javaScriptEnabled = true // required by the IFrame Player API
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        mediaPlaybackRequiresUserGesture = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        setSupportMultipleWindows(false)
        javaScriptCanOpenWindowsAutomatically = false
    }
    web.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (request.isForMainFrame) return true
            val host = request.url.host.orEmpty()
            return !(request.url.scheme == "https" && ALLOWED_FRAME_HOSTS.any { host == it || host.endsWith(".$it") })
        }
    }
    web.webChromeClient = object : WebChromeClient() {
        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            val activity = ctx.findActivity() ?: return callback.onCustomViewHidden()
            full.enter(activity, view, callback)
        }
        override fun onHideCustomView() = full.exit()
    }
    web.addJavascriptInterface(EndedBridge(onEnded), "appBridge")
    return web
}

/**
 * YouTube's fullscreen button: the video view goes over the whole window, the phone turns to landscape (either side,
 * even with auto-rotate off) and the system bars hide. Leaving shows the bars and puts the phone back upright; it stays
 * upright while the dialog is open, and [release] gives the activity its own orientation back when the dialog closes.
 */
private class FullscreenVideo {
    var active by mutableStateOf(false)
        private set
    private var activity: Activity? = null
    private var view: View? = null
    private var callback: WebChromeClient.CustomViewCallback? = null
    private var host: Activity? = null
    private var saved: Int? = null

    fun enter(activity: Activity, view: View, callback: WebChromeClient.CustomViewCallback) {
        if (active) exit()
        this.activity = activity; this.view = view; this.callback = callback
        view.setBackgroundColor(android.graphics.Color.BLACK)
        (activity.window.decorView as ViewGroup).addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (saved == null) { saved = activity.requestedOrientation; host = activity }
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        active = true
    }

    fun exit() {
        val a = activity ?: return
        activity = null
        view?.let { (a.window.decorView as ViewGroup).removeView(it) }
        view = null
        WindowCompat.getInsetsController(a.window, a.window.decorView).show(WindowInsetsCompat.Type.systemBars())
        a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        active = false
        callback?.also { callback = null }?.onCustomViewHidden()
    }

    fun release() {
        val a = host ?: return
        saved?.let { a.requestedOrientation = it }
        host = null; saved = null
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** [videoId] is checked against ^[A-Za-z0-9_-]{11}$ before it reaches this template. */
private fun playerHtml(videoId: String) = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="referrer" content="strict-origin-when-cross-origin">
<style>html,body{margin:0;height:100%;background:#000}#p{width:100%;height:100%}</style></head>
<body><div id="p"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
function onYouTubeIframeAPIReady(){
  new YT.Player('p',{videoId:'$videoId',
    playerVars:{rel:0,playsinline:1,autoplay:1,origin:'$ORIGIN',widget_referrer:'$ORIGIN'},
    events:{ onStateChange:function(e){ if(e.data===0 && window.appBridge){ appBridge.ended(); } } }});
}
</script></body></html>
""".trimIndent()
