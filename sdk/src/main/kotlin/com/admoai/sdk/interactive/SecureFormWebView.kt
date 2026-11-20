package com.admoai.sdk.interactive

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.VisibleForTesting
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ProfileStore
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * The real [BridgeTransport]: a WebView that only the form's own origin can talk to.
 *
 * - One injected object, `AdmoaiBridge`, exists only in frames of [launchOrigin]; the app listens
 *   only to the main frame of that origin and only to text messages.
 * - Every navigation the page starts is blocked, no window can open, no file or content address can
 *   load, DOM storage is off and the view uses its own WebView profile where the device has one.
 * - It holds the application context; while attached to a window it borrows the parent's context
 *   (so selects and the keyboard work) and gives it back on detach, never keeping an Activity.
 *
 * Main thread only.
 */
internal class SecureFormWebView(context: Context, private val launchOrigin: String) : BridgeTransport {
    private val contextWrapper = MutableContextWrapper(context.applicationContext ?: context)
    private val appContext: Context = contextWrapper.baseContext

    @VisibleForTesting
    internal val webView: WebView

    /** Test seam: answers requests the page makes, in place of the network. */
    @VisibleForTesting
    internal var testInterceptor: ((WebResourceRequest) -> WebResourceResponse?)? = null

    private var onMessage: (String) -> Unit = {}
    private var onRendererGone: () -> Unit = {}
    private var replyProxy: JavaScriptReplyProxy? = null
    private var destroyed = false

    override val view: View get() = webView

    init {
        webView = WebView(contextWrapper)
        useOwnProfile(webView)
        configure(webView.settings)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                (v.parent as? View)?.context?.let { contextWrapper.baseContext = it }
            }

            override fun onViewDetachedFromWindow(v: View) {
                contextWrapper.baseContext = appContext
            }
        })
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                testInterceptor?.invoke(request)

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                onRendererGone()
                return true
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message
            ): Boolean = false
        }
        WebViewCompat.addWebMessageListener(webView, BRIDGE_NAME, setOf(launchOrigin)) {
                _,
                message,
                sourceOrigin,
                isMainFrame,
                proxy
            ->
            if (isMainFrame &&
                normalize(sourceOrigin.toString()) == normalize(launchOrigin) &&
                message.type == WebMessageCompat.TYPE_STRING
            ) {
                message.data?.let {
                    replyProxy = proxy
                    onMessage(it)
                }
            }
        }
    }

    override fun load(url: String) {
        if (!destroyed) webView.loadUrl(url)
    }

    override fun send(text: String) {
        if (!destroyed) replyProxy?.postMessage(text)
    }

    override fun setOnMessage(listener: (String) -> Unit) {
        onMessage = listener
    }

    override fun setOnRendererGone(listener: () -> Unit) {
        onRendererGone = listener
    }

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        onMessage = {}
        onRendererGone = {}
        replyProxy = null
        try {
            WebViewCompat.removeWebMessageListener(webView, BRIDGE_NAME)
        } catch (_: Exception) {
            // The view is going away either way.
        }
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
        contextWrapper.baseContext = appContext
    }

    private fun normalize(origin: String): String = origin.trimEnd('/')

    @SuppressLint("RequiresFeature")
    private fun useOwnProfile(webView: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            ProfileStore.getInstance().getOrCreateProfile(PROFILE)
            WebViewCompat.setProfile(webView, PROFILE)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(settings: WebSettings) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = false
        settings.databaseEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setGeolocationEnabled(false)
    }

    internal companion object {
        const val BRIDGE_NAME = "AdmoaiBridge"
        const val PROFILE = "admoai_interactive_experience"
    }
}
