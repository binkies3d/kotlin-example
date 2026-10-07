package com.example.kotlinexample

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.json.JSONObject

// Page that runs the Binkies integration (the 3D viewer) full size in its #target element.
// The app only loads it once and then drives it through window.binkies.setSettings.
private const val VIEWER_URL = "https://mobile-app.binkies3d.com/"

// Logcat tag for the bridge messages
private const val TAG = "Binkies"

// The page posts its messages to window.BinkiesNative
private const val NATIVE_CHANNEL = "BinkiesNative"

/**
 * What the viewer shows. Pass a new one to [BinkiesViewer] to change something, it then updates the page.
 *
 * - [product]: Binkies content id, e.g. "samsung-galaxy-z-flip8-cream"
 * - [thumbnailsPlacement]: where the thumbnail strip is placed
 * - [thumbnailsSelectedColor]: border color of the selected thumbnail
 */
data class BinkiesSettings(
    val product: String,
    val thumbnailsPlacement: ThumbnailsPlacement = ThumbnailsPlacement.Bottom,
    val thumbnailsSelectedColor: Color = Color(0xFF262626),
)

enum class ThumbnailsPlacement { Bottom, Left, Right }

/**
 * The 3D viewer page in a WebView, talking to it over the BinkiesNative bridge.
 *
 * - [settings] is what the viewer shows. Changing them (e.g. another product) updates the viewer without reloading the page.
 * - [visible] is whether the viewer takes up space. Pass in what [onVisibilityChange] last reported.
 * - [onVisibilityChange] is called with true when the page sends "show" (the product's 3D model has loaded)
 *   and with false when it sends "hide" (the product has no 3D model, or the device can't render it).
 * - [onError] is called with everything the page logs as an error, e.g. to forward it to your crash reporting.
 *   The errors are logged to logcat either way.
 *
 * Hiding collapses a clipping container instead of setting the WebView to GONE/INVISIBLE:
 * a WebView that isn't VISIBLE pauses the page's rendering, so the next model would never
 * finish loading and "show" would never arrive. The WebView keeps its full size and only
 * gets clipped away.
 */
@Composable
fun BinkiesViewer(
    settings: BinkiesSettings,
    visible: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onError: (message: String) -> Unit = {},
) {
    val context = LocalContext.current
    // The bridge callback is created once with the WebView, these keep it using the latest values
    val currentSettings by rememberUpdatedState(settings)
    val currentOnVisibilityChange by rememberUpdatedState(onVisibilityChange)
    val currentOnError by rememberUpdatedState(onError)

    // One WebView for the lifetime of this composable. It is recreated (and the page reloads) together
    // with the Activity, e.g. on rotation: the new page then asks for the settings again.
    val webView = remember {
        createWebView(context).apply {
            onBridgeMessage { message ->
                when (message.optString("type")) {
                    // The page is ready and asks for its settings, once per page load
                    "setSettings" -> setSettings(currentSettings)
                    "show" -> currentOnVisibilityChange(true)
                    "hide" -> currentOnVisibilityChange(false)
                    // Anything the page logged as an error, e.g. invalid settings
                    "error" -> {
                        val error = errorText(message.optString("message"), message.optString("stack"))
                        Log.e(TAG, error)
                        currentOnError(error)
                    }
                }
            }
            loadUrl(VIEWER_URL)
        }
    }

    // Sends the settings whenever they change. While the page is still loading this does nothing,
    // the page asks for the current settings once it is ready.
    LaunchedEffect(settings) {
        webView.setSettings(settings)
    }

    // Pauses the page (timers, the 3D render loop) while the app is in the background,
    // and frees the WebView when the viewer leaves the composition
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            webView.destroy()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Full width, 5:6 (width:height) leaves room for the model and the thumbnails below it
        val viewerHeight = maxWidth * 1.2f
        val height by animateDpAsState(if (visible) viewerHeight else 0.dp, label = "viewerHeight")

        // The container animates its height and clips, the WebView inside it always keeps viewerHeight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clipToBounds(),
        ) {
            AndroidView(
                factory = { webView },
                modifier = Modifier
                    .fillMaxWidth()
                    // Lets the WebView be taller than the (collapsing) container, pinned to its top,
                    // so the page never gets resized and the viewer is revealed from the top down
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .requiredHeight(viewerHeight),
            )
        }
    }
}

// The settings in the shape the page expects. The page rejects unknown keys and invalid values with an "error" message.
private fun BinkiesSettings.toJson() = JSONObject()
    .put("product", product)
    .put(
        "gallery",
        JSONObject().put(
            "thumbnails",
            JSONObject()
                .put("placement", thumbnailsPlacement.name.lowercase())
                .put("selectedColor", thumbnailsSelectedColor.toCss()),
        ),
    )

// CSS hex color with alpha: #RRGGBBAA
private fun Color.toCss(): String {
    val argb = toArgb()
    return "#%06X%02X".format(argb and 0xFFFFFF, argb ushr 24)
}

// A JS stack usually starts with the message itself, so it's only added when it doesn't
private fun errorText(message: String, stack: String) = when {
    stack.isEmpty() -> message
    stack.startsWith(message) -> stack
    else -> "$message\n$stack"
}

// Sends the full settings to the page. The JSONObject's toString is a valid JS object literal, so it can be inlined into the script.
// window.binkies only exists once the page is ready, before that this does nothing.
// evaluateJavascript doesn't report JS errors on Android, so they are logged with console.error, which the page sends back as an "error" message.
private fun WebView.setSettings(settings: BinkiesSettings) {
    evaluateJavascript("try { window.binkies?.setSettings(${settings.toJson()}) } catch (error) { console.error(error) }", null)
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(context: Context): WebView {
    // chrome://inspect for debug builds
    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
        WebView.setWebContentsDebuggingEnabled(true)
    }

    return WebView(context).apply {
        // Not optional: AndroidView adds the view with WRAP_CONTENT layout params, and with a WRAP_CONTENT height the
        // WebView lays the page out against a zero-height viewport (height: 100% / 100dvh become 0)
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        // The viewer is a JavaScript app, domStorage gives the page localStorage / sessionStorage like a browser
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // Keeps the page's navigations in this WebView. Without a WebViewClient, Android opens them in the browser app.
        webViewClient = WebViewClient()
    }
}

// Exposes window.BinkiesNative.postMessage(json) to the page. The page posts {"type": "setSettings" | "show" | "hide"}
// and {"type": "error", "message": "...", "stack": "..."} (stack is optional).
// Must be added before loadUrl, the page posts as soon as it starts (it asks for the settings right away)
private fun WebView.onBridgeMessage(onMessage: (message: JSONObject) -> Unit) {
    addJavascriptInterface(
        object {
            // Called on a WebView background thread, onMessage runs on the main thread
            @JavascriptInterface
            fun postMessage(json: String) {
                Log.d(TAG, "bridge message: $json")
                val message = runCatching { JSONObject(json) }.getOrNull()
                if (message != null) post { onMessage(message) }
            }
        },
        NATIVE_CHANNEL,
    )
}
