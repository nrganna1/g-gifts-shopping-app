package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import com.example.ui.MainApp
import com.example.util.PhotoPickerHelper
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize

class MainActivity : ComponentActivity() {

    companion object {
        var instance: MainActivity? = null
            private set
    }

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var customUriListener: ((Uri) -> Unit)? = null
    private var customUrisListener: ((List<Uri>) -> Unit)? = null

    val fileChooserLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris: Array<Uri>? = if (result.resultCode == RESULT_OK) {
            val data = result.data
            // Support both WebChromeClient parseResult and direct data/clipData fallback
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
                ?: data?.data?.let { arrayOf(it) }
                ?: data?.clipData?.let { clip ->
                    Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                }
        } else {
            null
        }

        if (uris != null && uris.isNotEmpty()) {
            customUrisListener?.invoke(uris.toList())
            customUriListener?.invoke(uris.first())
        }
        customUriListener = null
        customUrisListener = null

        fileChooserCallback?.onReceiveValue(uris)
        fileChooserCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        enableEdgeToEdge()

        configureAppCheck(this, intent)

        setContent {
            MaterialTheme {
                MainApp()
            }
        }
    }

    // Native WebChromeClient implementation supporting HTML file inputs
    // with single/multiple image selection, cancellation safety, and proper MIME filters.
    fun createWebChromeClient(
        onUriSelected: ((Uri) -> Unit)? = null,
        onUrisSelected: ((List<Uri>) -> Unit)? = null
    ): WebChromeClient {
        return object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                // Clear any previous pending callback to prevent memory leaks / stuck states
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                customUriListener = onUriSelected
                customUrisListener = onUrisSelected

                try {
                    val isMultiple = fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE
                    val baseIntent = fileChooserParams?.createIntent()
                        ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                            if (isMultiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        }

                    if (baseIntent.type.isNullOrEmpty()) {
                        baseIntent.type = "image/*"
                    }
                    if (isMultiple) {
                        baseIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }

                    val galleryIntent = PhotoPickerHelper.createGalleryPickIntent(allowMultiple = isMultiple)
                    val chooser = Intent.createChooser(baseIntent, "Select Photo")
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(galleryIntent))

                    fileChooserLauncher.launch(chooser)
                    return true
                } catch (e: Exception) {
                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = null
                    customUriListener = null
                    customUrisListener = null
                    return false
                }
            }
        }
    }

    // Safe WebViewClient implementation that intercepts renderer termination
    // (aw_browser_terminator crash, OOM, or GPU sandbox exit) to prevent host app crash.
    fun createWebViewClient(onRendererGone: (() -> Unit)? = null): WebViewClient {
        return object : WebViewClient() {
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                try {
                    (view?.parent as? ViewGroup)?.removeView(view)
                    view?.destroy()
                } catch (_: Exception) {}
                onRendererGone?.invoke()
                return true // Returning true tells Android the crash was handled and must NOT terminate the app
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        customUriListener = null
        customUrisListener = null
        if (instance == this) {
            instance = null
        }
    }
}

/**
 * Authoritative App Check helper for AI Logic applications.
 * Handles debug token persistence and production Play Integrity attestation.
 */
fun configureAppCheck(context: Context, intent: Intent?) {
    Firebase.initialize(context)
    val appCheck = Firebase.appCheck

    // In debug/test environments, AI Studio injects the debug token via intent extra.
    val debugToken = intent?.getStringExtra("FIREBASE_APPCHECK_DEBUG_TOKEN")
    if (debugToken != null) {
        val prefs = context.getSharedPreferences("_fire_ac_debug", Context.MODE_PRIVATE)
        // Mandatory: sync commit for immediate availability
        prefs.edit().putString("debug_token", debugToken).commit()
        appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    } else {
        // Production attestation
        appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
