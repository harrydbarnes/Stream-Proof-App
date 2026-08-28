package com.example.spotifyproofsender.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun ConfiguredWebView(
    modifier: Modifier = Modifier,
    initialUrl: String,
    navigationKey: String,
    userAgent: String,
    allowThirdPartyCookies: Boolean,
    fileChooserEnabled: Boolean = false,
    onPageStarted: (String) -> Unit = {},
    onPageFinished: (String) -> Unit = {},
    onPageError: (String) -> Unit = {},
    onUrlChanged: (String) -> Unit = {},
    onWebViewReady: (WebView) -> Unit = {},
) {
    val latestOnPageStarted by rememberUpdatedState(onPageStarted)
    val latestOnPageFinished by rememberUpdatedState(onPageFinished)
    val latestOnPageError by rememberUpdatedState(onPageError)
    val latestOnUrlChanged by rememberUpdatedState(onUrlChanged)
    val latestOnWebViewReady by rememberUpdatedState(onWebViewReady)
    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    fun deliverFileChooserResult(uris: List<Uri>) {
        val callback = fileChooserCallback
        fileChooserCallback = null
        callback?.onReceiveValue(uris.takeIf { it.isNotEmpty() }?.toTypedArray())
    }

    val singleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        deliverFileChooserResult(uri?.let(::listOf).orEmpty())
    }

    val multipleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        deliverFileChooserResult(uris)
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                configureForProofSender(
                    userAgent = userAgent,
                    allowThirdPartyCookies = allowThirdPartyCookies,
                )
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        if (request.url.scheme == "http" || request.url.scheme == "https") return false

                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                        }
                        return true
                    }

                    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                        latestOnUrlChanged(url)
                        latestOnPageStarted(url)
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        latestOnUrlChanged(url)
                        latestOnPageFinished(url)
                    }

                    override fun doUpdateVisitedHistory(
                        view: WebView,
                        url: String,
                        isReload: Boolean,
                    ) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        // Instagram is a single-page app. This callback also observes URLs
                        // changed by pushState/replaceState without a full page load.
                        latestOnUrlChanged(url)
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        if (request.isForMainFrame) {
                            latestOnPageError(error.description?.toString() ?: "The page could not be loaded")
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse,
                    ) {
                        if (request.isForMainFrame) {
                            val reason = errorResponse.reasonPhrase?.takeIf { it.isNotBlank() }
                            latestOnPageError(
                                if (reason == null) {
                                    "The page returned HTTP ${errorResponse.statusCode}"
                                } else {
                                    "The page returned HTTP ${errorResponse.statusCode}: $reason"
                                },
                            )
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView,
                        handler: SslErrorHandler,
                        error: android.net.http.SslError,
                    ) {
                        handler.cancel()
                        latestOnPageError("Secure connection failed. Check the device time and network, then try again.")
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(
                        webView: WebView,
                        filePathCallback: ValueCallback<Array<Uri>>,
                        fileChooserParams: FileChooserParams,
                    ): Boolean {
                        if (!fileChooserEnabled) return false

                        fileChooserCallback?.onReceiveValue(null)
                        fileChooserCallback = filePathCallback

                        // Instagram normally requests images. Keep the picker image-focused even
                        // if its accept list is empty or overly broad.
                        val requestedTypes = fileChooserParams.acceptTypes
                            .asSequence()
                            .flatMap { it.split(',').asSequence() }
                            .map(String::trim)
                            .filter { it.startsWith("image/", ignoreCase = true) }
                            .distinct()
                            .toList()
                            .ifEmpty { listOf("image/*") }
                        val launcher = if (
                            fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE
                        ) {
                            multipleFilePickerLauncher
                        } else {
                            singleFilePickerLauncher
                        }
                        runCatching {
                            launcher.launch(requestedTypes.toTypedArray())
                        }.onFailure {
                            fileChooserCallback = null
                            filePathCallback.onReceiveValue(null)
                        }
                        return true
                    }
                }
                tag = navigationKey
                loadUrl(initialUrl)
                latestOnWebViewReady(this)
            }
        },
        update = { webView ->
            if (webView.settings.userAgentString != userAgent) {
                webView.settings.userAgentString = userAgent
                webView.reload()
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, allowThirdPartyCookies)

            if (webView.tag != navigationKey) {
                webView.tag = navigationKey
                webView.loadUrl(initialUrl)
            }
            latestOnWebViewReady(webView)
        },
        onRelease = { webView ->
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = null
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = null
            webView.destroy()
        },
    )
}

fun configureForProofSender(
    webView: WebView,
    userAgent: String,
    allowThirdPartyCookies: Boolean,
) {
    with(webView.settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        loadsImagesAutomatically = true
        mediaPlaybackRequiresUserGesture = true
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
        cacheMode = WebSettings.LOAD_DEFAULT
        userAgentString = userAgent
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        setSupportMultipleWindows(false)
    }

    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, allowThirdPartyCookies)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        webView.settings.safeBrowsingEnabled = true
    }
}

fun tryInstagramAttachmentHelper(webView: WebView, onResult: (String) -> Unit) {
    val script = """
        (() => {
          const candidates = Array.from(document.querySelectorAll('button, [role="button"], a'));
          const attachment = candidates.find((element) => {
            const label = (element.getAttribute('aria-label') || element.getAttribute('title') || element.textContent || '').toLowerCase();
            const looksLikeAttachment = /(photo|image|attach|media|gallery|camera)/.test(label);
            const looksLikeSend = /(send|message|like)/.test(label);
            return looksLikeAttachment && !looksLikeSend;
          });
          if (!attachment) return 'No obvious image button found';
          attachment.click();
          return 'Clicked an obvious image button';
        })();
    """.trimIndent()

    runCatching {
        webView.evaluateJavascript(script) { rawResult ->
            onResult(
                rawResult?.trim('"')?.replace("\\\"", "\"")
                    ?: "Helper returned no result",
            )
        }
    }.onFailure {
        onResult("Helper could not run: ${it.message ?: "unknown error"}")
    }
}
