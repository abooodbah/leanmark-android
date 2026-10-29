package io.github.abooodbah.leanmark

import android.annotation.TargetApi
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebMessage
import android.webkit.WebMessagePort
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Shows one Markdown document in the desktop reader page. Files arrive through
 * VIEW and SEND intents or the page's Open button; the app reads them with the
 * grant that came with the intent and never writes to them.
 */
class MainActivity : Activity() {
    // Milliseconds from creation to each step of the first document, for
    // scripts/measure.sh. Each step is logged once per activity.
    private val createdAt = SystemClock.uptimeMillis()
    private val phases = HashSet<String>()

    private fun phase(name: String) {
        if (phases.add(name)) Log.i("LeanMarkTiming", "phase $name ${SystemClock.uptimeMillis() - createdAt} ms")
    }


    private sealed interface Source {
        data class FromUri(val uri: Uri) : Source
        class FromText(val bytes: ByteArray, val name: String) : Source
    }

    private sealed interface Shown {
        data object Empty : Shown

        /** [fields] is dropped once the page has it; the page keeps the document. */
        class Document(val name: String, val path: String, val bytes: Long, var fields: String?) : Shown

        class Failed(val name: String, val message: String) : Shown
    }

    /** What a document looked like when it was opened, to notice changes on return. */
    private data class Stamp(val size: Long?, val modified: Long?)

    private lateinit var root: FrameLayout
    private lateinit var prefs: SharedPreferences
    private val main = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()

    private var webView: WebView? = null
    private var port: WebMessagePort? = null
    private var portPosted = false
    private var readerReady = false
    private var source: Source? = null
    private var shown: Shown = Shown.Empty
    private var loads = 0
    private var loading = false
    private var stamp: Stamp? = null
    private var viewerOpen = false
    private var backCallback: Any? = null
    private var awaitingFirstRender = true
    private var resumedOnce = false
    private var theme = "system"
    private var zoom = 1.0

    /** Every request the WebView made that was refused. Read by the instrumented tests. */
    internal val blockedRequests = CopyOnWriteArrayList<String>()

    internal val webViewForTest: WebView? get() = webView
    internal val viewerOpenForTest: Boolean get() = viewerOpen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        theme = ReaderCommand.themeName(prefs.getString(KEY_THEME, null))
        zoom = prefs.getFloat(KEY_ZOOM, 1f).toDouble()

        root = FrameLayout(this)
        setContentView(root)
        layOutEdgeToEdge()
        applyBarColors()

        // Read and parse the file while the WebView starts; creating the
        // WebView is the slowest part of a cold start.
        val restored = savedInstanceState?.getString(STATE_URI)?.let(Uri::parse)
        if (restored != null) open(Source.FromUri(restored)) else openFrom(intent)

        val provider = WebView.getCurrentWebViewPackage()?.versionName
        if (!WebViewSupport.isSupported(provider)) {
            showUnsupported(WebViewSupport.message(provider))
            return
        }
        try {
            createWebView()
            phase("webview created")
        } catch (error: RuntimeException) {
            // Thrown when the WebView provider is missing or being updated.
            showUnsupported(WebViewSupport.message(null))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFrom(intent)
    }

    override fun onResume() {
        super.onResume()
        if (resumedOnce) reloadIfChanged()
        resumedOnce = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        (source as? Source.FromUri)?.let { outState.putString(STATE_URI, it.uri.toString()) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyBarColors()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        // Messages the page sent just before this can still be queued on the
        // main thread. Dropping the port first makes them no-ops instead of
        // answers on a closed port, which throws.
        readerReady = false
        port?.close()
        port = null
        webView?.destroy()
        super.onDestroy()
    }

    // Below Android 13 back arrives as a key. Android 13 and later use
    // backCallback, registered only while the figure viewer is open.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (Build.VERSION.SDK_INT < 33 && viewerOpen && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) send(HostMessages.BACK)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Activity result API from the platform; AndroidX is not used.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == REQUEST_OPEN && resultCode == RESULT_OK && uri != null) {
            open(Source.FromUri(uri))
        }
    }

    // Opening documents

    private fun openFrom(intent: Intent?) {
        val next = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data?.let { Source.FromUri(it) }
            Intent.ACTION_SEND -> sharedStream(intent)?.let { Source.FromUri(it) }
                ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.let { text ->
                    val name = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                        ?.takeIf { it.isNotBlank() } ?: "Shared text"
                    Source.FromText(text.toString().toByteArray(Charsets.UTF_8), name)
                }
            else -> null
        }
        if (next != null) {
            open(next)
        } else {
            source = null
            shown = Shown.Empty
            if (readerReady) sendCurrentView()
        }
    }

    private fun sharedStream(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun open(next: Source) {
        source = next
        val generation = ++loads
        loading = true
        worker.execute {
            val result = load(next)
            val nextStamp = stampOf(next)
            main.post {
                if (generation != loads || isDestroyed) return@post
                loading = false
                shown = result
                stamp = nextStamp
                if (readerReady) sendCurrentView()
            }
        }
    }

    private fun load(next: Source): Shown {
        val name = displayName(next)
        return try {
            val bytes = readBytes(next)
            val fields = String(MarkdownCore.renderFields(bytes), Charsets.UTF_8)
            val path = when (next) {
                is Source.FromUri -> next.uri.toString()
                is Source.FromText -> name
            }
            Shown.Document(name, path, bytes.size.toLong(), fields)
        } catch (error: DocumentReader.TooLargeException) {
            Shown.Failed(name, DocumentReader.TOO_LARGE)
        } catch (error: MarkdownException) {
            Shown.Failed(name, error.message ?: CANNOT_PARSE)
        } catch (error: OutOfMemoryError) {
            Shown.Failed(name, TOO_LARGE_FOR_DEVICE)
        } catch (error: Exception) {
            // FileNotFoundException, SecurityException and other provider failures.
            Shown.Failed(name, CANNOT_READ)
        }
    }

    private fun readBytes(from: Source): ByteArray = when (from) {
        is Source.FromText -> from.bytes
        is Source.FromUri -> {
            // Only content URIs carry a grant from the sending app. Paths and
            // other schemes would read with this app's own access instead.
            if (from.uri.scheme != "content") throw SecurityException("not a content URI")
            val size = querySize(from.uri) ?: -1
            val input = contentResolver.openInputStream(from.uri) ?: throw IOException("no stream")
            input.use { DocumentReader.readLimited(it, DocumentReader.MAX_BYTES, size) }
        }
    }

    private fun displayName(from: Source): String = when (from) {
        is Source.FromText -> from.name
        is Source.FromUri -> queryString(from.uri, OpenableColumns.DISPLAY_NAME)
            ?: from.uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "Untitled.md"
    }

    private fun querySize(uri: Uri): Long? = queryLong(uri, OpenableColumns.SIZE)

    private fun stampOf(from: Source): Stamp? = (from as? Source.FromUri)?.let {
        Stamp(querySize(it.uri), queryLong(it.uri, DocumentsContract.Document.COLUMN_LAST_MODIFIED))
    }

    private fun queryString(uri: Uri, column: String): String? = try {
        contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(column)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getString(index) else null
        }?.takeIf { it.isNotBlank() }
    } catch (error: Exception) {
        null
    }

    private fun queryLong(uri: Uri, column: String): Long? = try {
        contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(column)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getLong(index) else null
        }
    } catch (error: Exception) {
        null
    }

    /** Reloads when the file's provider reports a new size or time since it was opened. */
    private fun reloadIfChanged() {
        val current = source as? Source.FromUri ?: return
        val before = stamp ?: return
        if (loading || shown !is Shown.Document) return
        worker.execute {
            val now = stampOf(current)
            main.post {
                if (source == current && now != null && now != before &&
                    (now.size != null || now.modified != null)
                ) {
                    open(current)
                }
            }
        }
    }

    // The reader page

    private fun createWebView() {
        if (isDebuggable()) WebView.setWebContentsDebuggingEnabled(true)
        val view = WebView(this)
        view.setBackgroundColor(MarkdownCore.themeCanvas(theme, isNight()))
        with(view.settings) {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            domStorageEnabled = false
            setGeolocationEnabled(false)
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = false
            mediaPlaybackRequiresUserGesture = true
            builtInZoomControls = true
            displayZoomControls = false
            textZoom = textZoomPercent()
        }
        view.webViewClient = ReaderClient()
        if (isDebuggable()) view.webChromeClient = ConsoleLog
        root.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        webView = view
        portPosted = false
        readerReady = false
        view.loadUrl(ReaderAssets.PAGE_URL)
    }

    private inner class ReaderClient : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url.toString()
            val asset = if (request.method == "GET") ReaderAssets.route(url) else null
            if (asset == null) {
                blockedRequests += url
                return blocked()
            }
            return try {
                val encoding = if (asset.mimeType.startsWith("text/")) "utf-8" else null
                WebResourceResponse(asset.mimeType, encoding, assets.open(asset.assetPath))
            } catch (error: IOException) {
                blockedRequests += url
                blocked()
            }
        }

        // Links are handled by the page, which posts open-link. Anything that
        // still tries to navigate is refused, except moving to an #anchor.
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            return !(url.substringBefore('#') == ReaderAssets.PAGE_URL && url.contains('#'))
        }

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            portPosted = false
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (view === webView && !portPosted && url.startsWith(ReaderAssets.PAGE_URL)) {
                portPosted = true
                phase("page loaded")
                connect(view)
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // Without this the whole app would close. Start a new page and show
            // the document again, read fresh from its source.
            if (view === webView) {
                root.removeView(view)
                webView = null
                port = null
                readerReady = false
                setViewerOpen(false)
                view.destroy()
                createWebView()
            }
            return true
        }
    }

    private fun blocked() =
        WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private fun connect(view: WebView) {
        val (mine, theirs) = view.createWebMessageChannel()
        mine.setWebMessageCallback(
            object : WebMessagePort.WebMessageCallback() {
                override fun onMessage(from: WebMessagePort, message: WebMessage) {
                    if (from === port) message.data?.let(::handleReaderMessage)
                }
            },
            main,
        )
        port = mine
        view.postWebMessage(WebMessage("leanmark-port", arrayOf(theirs)), Uri.parse(ReaderAssets.ORIGIN))
    }

    private fun send(message: String) {
        if (!readerReady) return
        try {
            port?.postMessage(WebMessage(message))
        } catch (closed: IllegalStateException) {
            // The page went away (renderer gone or activity closing); the
            // replacement page asks for everything again when it is ready.
            readerReady = false
        }
    }

    private fun handleReaderMessage(message: String) {
        when (val command = ReaderCommand.parse(message) ?: return) {
            ReaderCommand.Ready -> {
                readerReady = true
                phase("reader ready")
                send(HostMessages.HOST)
                sendCurrentView()
            }
            ReaderCommand.OpenFile -> pickDocument()
            ReaderCommand.Reload -> source?.let(::open)
            is ReaderCommand.Theme -> {
                theme = command.value
                prefs.edit().putString(KEY_THEME, theme).apply()
                send(HostMessages.theme(theme))
                applyBarColors()
            }
            is ReaderCommand.Zoom -> applyZoom(MarkdownCore.stepZoom(zoom, command.direction))
            is ReaderCommand.OpenLink -> followLink(command.href)
            is ReaderCommand.CopySource -> copySource(command)
            is ReaderCommand.Viewer -> setViewerOpen(command.open)
            is ReaderCommand.Rendered -> if (awaitingFirstRender && !loading) reportDrawn()
        }
    }

    private fun sendCurrentView() {
        when (val current = shown) {
            Shown.Empty -> if (!loading) send(HostMessages.empty(theme))
            is Shown.Document -> {
                val fields = current.fields
                if (fields == null) {
                    // The page was replaced after it had the document.
                    source?.let(::open)
                } else {
                    phase("document sent")
                    send(HostMessages.document(theme, current.name, current.path, current.bytes, fields))
                    current.fields = null
                }
            }
            is Shown.Failed -> send(HostMessages.error(theme, current.name, current.message))
        }
    }

    private fun reportDrawn() {
        val view = webView ?: return
        awaitingFirstRender = false
        // Fires once the page's new content is ready to be drawn on screen.
        view.postVisualStateCallback(
            0,
            object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    phase("drawn")
                    try {
                        reportFullyDrawn()
                    } catch (error: SecurityException) {
                        // Some older builds required a permission for this call.
                    }
                }
            },
        )
    }

    private fun showUnsupported(message: String) {
        root.removeAllViews()
        val padding = (24 * resources.displayMetrics.density).roundToInt()
        root.addView(
            TextView(this).apply {
                text = message
                gravity = Gravity.CENTER
                textSize = 18f
                setTextColor(getColor(R.color.ink))
                setPadding(padding, padding, padding, padding)
            },
            FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT),
        )
        awaitingFirstRender = false
        root.post {
            try {
                reportFullyDrawn()
            } catch (error: SecurityException) {
                // As above.
            }
        }
    }

    // Commands from the page

    private fun pickDocument() {
        val pick = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, PICKER_TYPES)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(pick, REQUEST_OPEN)
        } catch (error: ActivityNotFoundException) {
            send(HostMessages.status("This device has no file picker.", "error"))
        }
    }

    private fun followLink(href: String) {
        when (val action = LinkAction.forHref(href)) {
            is LinkAction.External -> try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(action.uri)).addCategory(Intent.CATEGORY_BROWSABLE),
                )
            } catch (error: ActivityNotFoundException) {
                send(HostMessages.status("No app on this device can open that link.", "warning"))
            }
            is LinkAction.Refuse -> send(HostMessages.status(action.message, "warning"))
            LinkAction.Ignore -> Unit
        }
    }

    private fun copySource(command: ReaderCommand.CopySource) {
        val from = source
        if (from == null || shown !is Shown.Document) {
            send(HostMessages.copied(command.requestId, false))
            send(HostMessages.status("Open a document before copying.", "warning"))
            return
        }
        worker.execute {
            // The copy comes from the file, so it is the exact Markdown.
            var text: String? = null
            var problem: String? = null
            var stale = false
            try {
                val bytes = readBytes(from)
                text = String(
                    MarkdownCore.extractSection(bytes, command.headingIndex, command.headingCount),
                    Charsets.UTF_8,
                )
            } catch (error: MarkdownException) {
                stale = error.stale
                problem = error.message
            } catch (error: DocumentReader.TooLargeException) {
                problem = DocumentReader.TOO_LARGE
            } catch (error: Exception) {
                problem = CANNOT_READ
            } catch (error: OutOfMemoryError) {
                problem = TOO_LARGE_FOR_DEVICE
            }
            main.post {
                if (isDestroyed) return@post
                val copied = text?.let(::putOnClipboard) ?: false
                send(HostMessages.copied(command.requestId, copied))
                when {
                    stale -> {
                        open(from)
                        send(
                            HostMessages.status(
                                "The file changed since LeanMark opened it, so it was reloaded. " +
                                    "Copy again to get the current text.",
                                "warning",
                            ),
                        )
                    }
                    problem != null -> send(HostMessages.status(problem!!, "warning"))
                    !copied -> send(HostMessages.status("That section is too large to copy.", "warning"))
                }
            }
        }
    }

    private fun putOnClipboard(text: String): Boolean = try {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Markdown", text))
        true
    } catch (error: RuntimeException) {
        // A clip larger than the binder limit is refused with an exception.
        false
    }

    private fun applyZoom(value: Double) {
        zoom = value
        prefs.edit().putFloat(KEY_ZOOM, value.toFloat()).apply()
        webView?.settings?.textZoom = textZoomPercent()
        send(HostMessages.zoom((zoom * 100).roundToInt()))
    }

    // The system font size is applied on top of the reader's own zoom.
    private fun textZoomPercent(): Int = (zoom * resources.configuration.fontScale * 100).roundToInt()

    private fun setViewerOpen(open: Boolean) {
        viewerOpen = open
        if (Build.VERSION.SDK_INT >= 33) updateBackCallback()
    }

    @TargetApi(33)
    private fun updateBackCallback() {
        val existing = backCallback as OnBackInvokedCallback?
        if (viewerOpen && existing == null) {
            val callback = OnBackInvokedCallback { send(HostMessages.BACK) }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback,
            )
            backCallback = callback
        } else if (!viewerOpen && existing != null) {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(existing)
            backCallback = null
        }
    }

    // Window

    @Suppress("DEPRECATION")
    private fun layOutEdgeToEdge() {
        if (Build.VERSION.SDK_INT < 30) return
        // Android 15 and later always draw behind the bars; doing the same from
        // Android 11 keeps one code path for insets and the keyboard.
        window.setDecorFitsSystemWindows(false)
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime(),
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsets.CONSUMED
        }
    }

    private fun isNight(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    @Suppress("DEPRECATION")
    private fun applyBarColors() {
        val night = isNight()
        val dark = MarkdownCore.isDarkTheme(theme, night)
        val effective = if (theme == "system") (if (night) "dark" else "light") else theme
        val surface = SURFACES[effective] ?: SURFACES.getValue("light")
        val canvas = MarkdownCore.themeCanvas(theme, night)
        // The bars sit next to the page's top bar, which is --surface. The
        // window and the WebView take --canvas, so nothing flashes a different
        // colour before the page draws.
        root.setBackgroundColor(surface)
        window.setBackgroundDrawable(ColorDrawable(canvas))
        webView?.setBackgroundColor(canvas)
        window.statusBarColor = surface
        window.navigationBarColor = surface
        if (Build.VERSION.SDK_INT >= 30) {
            val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (dark) 0 else light, light)
        } else {
            val light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            val flags = window.decorView.systemUiVisibility
            window.decorView.systemUiVisibility = if (dark) flags and light.inv() else flags or light
        }
    }

    private fun isDebuggable() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private object ConsoleLog : WebChromeClient() {
        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            Log.d("LeanMarkPage", "${message.messageLevel()} ${message.message()} (${message.lineNumber()})")
            return true
        }
    }

    private companion object {
        const val PREFERENCES = "reader"
        const val KEY_THEME = "theme"
        const val KEY_ZOOM = "zoom"
        const val STATE_URI = "document"
        const val REQUEST_OPEN = 1

        // reader.css --surface for each theme. ThemeTest compares these with
        // the page, so they cannot drift from the desktop's colours.
        val SURFACES = mapOf(
            "light" to 0xFFFFFFFF.toInt(),
            "dark" to 0xFF1A1917.toInt(),
            "dim" to 0xFF25282C.toInt(),
            "dusk" to 0xFF29241E.toInt(),
        )

        val PICKER_TYPES = arrayOf("text/markdown", "text/x-markdown", "text/plain", "application/octet-stream")

        const val CANNOT_READ =
            "LeanMark could not read this file. The app that shared it may have " +
                "withdrawn access. Open it again from that app."
        const val CANNOT_PARSE = "LeanMark could not parse this Markdown document."
        const val TOO_LARGE_FOR_DEVICE = "This file is too large to show on this device."
    }
}
