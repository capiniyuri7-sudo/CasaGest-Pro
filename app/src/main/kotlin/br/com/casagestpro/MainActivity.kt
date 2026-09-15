package br.com.casagestpro

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.View
import android.webkit.ValueCallback
import android.webkit.PermissionRequest
import android.webkit.WebSettings
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {
    companion object {
        private const val ALL_FILE_TYPES = "*/*"
        const val NOTIFICATION_URL_EXTRA = "notification_url"
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermissionRequest: PermissionRequest? = null

    private val devicePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val request = pendingPermissionRequest ?: return@registerForActivityResult
        pendingPermissionRequest = null
        if (permissions.values.all { it }) {
            request.grant(allowedWebResources(request))
        } else {
            request.deny()
        }
    }

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        filePathCallback?.onReceiveValue(uris.toTypedArray())
        filePathCallback = null
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSystemBars()
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.web_view)
        progressBar = findViewById(R.id.progress_bar)
        configureInsets(findViewById(R.id.content))
        configureWebView()
        webView.addJavascriptInterface(PushBridge(), "CasaGestNative")
        configureBackNavigation()
        requestNotificationPermission()
        refreshPushToken()
        restoreOrLoadPage(savedInstanceState, intent.getStringExtra(NOTIFICATION_URL_EXTRA))
    }

    private fun configureInsets(content: View) {
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.setPadding(
                maxOf(systemBars.left, cutout.left),
                maxOf(systemBars.top, cutout.top),
                maxOf(systemBars.right, cutout.right),
                maxOf(systemBars.bottom, cutout.bottom)
            )
            insets
        }
        ViewCompat.requestApplyInsets(content)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            webView.settings.forceDark = WebSettings.FORCE_DARK_AUTO
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread { requestDevicePermissions(request) }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                if (pendingPermissionRequest == request) pendingPermissionRequest = null
            }

            override fun onShowFileChooser(
                view: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                this@MainActivity.filePicker.launch(arrayOf(ALL_FILE_TYPES))
                return true
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                if (isCasaGestUrl(request.url.toString())) return false
                openExternalUrl(request.url)
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun configureBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    private fun restoreOrLoadPage(savedInstanceState: Bundle?, notificationUrl: String?) {
        if (savedInstanceState == null) {
            webView.loadUrl(notificationUrl?.takeIf(::isCasaGestUrl) ?: BuildConfig.CASA_GEST_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun refreshPushToken() {
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                getSharedPreferences(
                    CasaGestFirebaseMessagingService.PREFERENCES_NAME,
                    MODE_PRIVATE
                ).edit()
                    .putString(CasaGestFirebaseMessagingService.PUSH_TOKEN_KEY, token)
                    .apply()
            }
        } catch (_: IllegalStateException) {
            // Firebase becomes available after google-services.json is configured.
        }
    }

    inner class PushBridge {
        @JavascriptInterface
        fun getPushToken(): String = getSharedPreferences(
            CasaGestFirebaseMessagingService.PREFERENCES_NAME,
            MODE_PRIVATE
        ).getString(CasaGestFirebaseMessagingService.PUSH_TOKEN_KEY, "") ?: ""
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun isCasaGestUrl(url: String): Boolean {
        val configured = Uri.parse(BuildConfig.CASA_GEST_URL)
        val requested = Uri.parse(url)
        return configured.scheme == requested.scheme &&
            configured.host == requested.host &&
            configured.port == requested.port
    }

    private fun openExternalUrl(url: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url))
        } catch (_: ActivityNotFoundException) {
            // Ignore links for which the device has no compatible application.
        }
    }

    private fun requestDevicePermissions(request: PermissionRequest) {
        if (!isCasaGestUrl(request.origin.toString())) {
            request.deny()
            return
        }

        val permissions = request.resources.mapNotNull { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                else -> null
            }
        }.distinct()

        if (permissions.isEmpty()) {
            request.deny()
            return
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            request.grant(allowedWebResources(request))
            return
        }

        pendingPermissionRequest?.deny()
        pendingPermissionRequest = request
        devicePermissionLauncher.launch(missingPermissions.toTypedArray())
    }

    private fun allowedWebResources(request: PermissionRequest): Array<String> =
        request.resources.filter {
            it == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
                it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
        }.toTypedArray()

    @Suppress("DEPRECATION")
    private fun configureSystemBars() {
        val isDarkTheme = (resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkTheme
            isAppearanceLightNavigationBars = !isDarkTheme
        }
    }
}