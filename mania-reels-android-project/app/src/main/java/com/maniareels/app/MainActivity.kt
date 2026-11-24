package com.maniareels.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val APP_URL = "https://mania-reels.pages.dev/"
    }

    private lateinit var webView: WebView
    private val appHost: String by lazy { Uri.parse(APP_URL).host.orEmpty() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Block standard Android screenshots, screen recording, and Recent Apps preview thumbnails
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        window.statusBarColor = Color.parseColor("#08080C")
        window.navigationBarColor = Color.parseColor("#08080C")

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#08080C"))
            isLongClickable = false
            setOnLongClickListener { true } // Disable long-press save/download menu
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true                     // Required for localStorage episode resume
            mediaPlaybackRequiresUserGesture = false     // Auto-play next episode after 5s/15s ad gate
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportMultipleWindows(true)              // Allow clickable ads (target="_blank")
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        // Allow ad iframes to load silently inside the app; open user-tapped external ad links in browser
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val req = request ?: return false
                val uri = req.url ?: return false
                val host = uri.host.orEmpty()

                // Keep Mania Reels own pages inside WebView
                if (host.equals(appHost, ignoreCase = true) || uri.scheme == "about") {
                    return false
                }

                // Do not hijack background ad iframe loads — only open external browser on real user clicks
                if (!req.isForMainFrame && !req.hasGesture()) {
                    return false
                }

                openExternalUrl(uri)
                return true
            }
        }

        // Handle target="_blank" clickable ads from wide banner & post-episode ad iframes
        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                if (!isUserGesture) return false
                val hitResult = view?.hitTestResult
                val url = hitResult?.extra
                if (!url.isNullOrBlank()) {
                    openExternalUrl(Uri.parse(url))
                    return false
                }
                val tempWebView = WebView(this@MainActivity)
                tempWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView?,
                        req: WebResourceRequest?
                    ): Boolean {
                        req?.url?.let { openExternalUrl(it) }
                        return true
                    }
                }
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = tempWebView
                resultMsg?.sendToTarget()
                return true
            }
        }

        // Explicitly no DownloadListener — video downloading is removed from Mania Reels
        setContentView(webView)
        webView.loadUrl(APP_URL)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    private fun openExternalUrl(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {
            // Ignore malformed ad URI
        }
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
