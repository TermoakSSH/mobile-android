package com.termoak.app.store

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import com.termoak.app.TermoakApp
import com.termoak.app.data.AccountNamesState
import com.termoak.app.data.QuickAction
import com.termoak.app.data.ThemeMode
import com.termoak.app.EngineObjects
import com.termoak.app.R
import com.termoak.app.data.Turn
import com.termoak.ffi.AiTaskStatus
import com.termoak.app.ui.AppRoot
import com.termoak.app.ui.TermoakTheme
import com.termoak.ffi.AccountNames
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The store screenshots (Google Play and F-Droid), on the JVM: the real app
 * (AppRoot with its screens, components and theme) drawn by Robolectric with
 * Android's own graphics, at each device's size and density, in English and
 * Spanish. The engine can't load here, so its calls are answered by
 * stand-ins with demo data (StoreFakes, Demo, TermDemo); the screens get
 * there as a person would, by tapping through the app.
 *
 * Run with scripts/screenshots.sh (or -PtermoakStore=1, optionally
 * -PtermoakStoreOnly=<regex of "device-lang-NN">); they are skipped otherwise.
 * PNGs in app/build/store/<lang>/<device>/NN_name.png.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(
    application = TermoakApp::class,
    sdk = [34],
    instrumentedPackages = ["com.termoak.ffi"],
    shadows = [ShadowFfi::class],
)
class StoreScreenshotTest(private val device: Device, private val lang: Lang, private val shot: Shot) {
    enum class Device(
        val dir: String,
        val widthPx: Int,
        val heightPx: Int,
        val dpi: Int,
        val statusDp: Int,
        val navDp: Int,
    ) {
        PHONE("phoneScreenshots", 1080, 2160, 420, 32, 24),
        TABLET7("sevenInchScreenshots", 1920, 1200, 240, 24, 24),
        TABLET10("tenInchScreenshots", 2560, 1600, 320, 24, 24),
        ;

        val density get() = dpi / 160f
        val tablet get() = this != PHONE

        fun qualifiers(lang: Lang): String {
            val w = Math.round(widthPx / density)
            val h = Math.round(heightPx / density)
            return "${lang.qualifier}-w${w}dp-h${h}dp-${if (w > h) "land" else "port"}-${dpi}dpi"
        }
    }

    enum class Lang(val dir: String, val qualifier: String) {
        EN("en-US", "en-rUS"),
        ES("es-ES", "es-rES"),
    }

    enum class Shot(val file: String) {
        VAULT("01_vault"),
        TERMINAL("02_terminal"),
        SPLIT("03_split"),
        AI("04_ai"),
        FILES("05_files"),
        EDITOR("06_host_editor"),
        SHARING("07_sharing"),
        SETTINGS("08_settings"),
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}-{2}")
        fun params(): List<Array<Any>> {
            val only = System.getProperty("termoak.store.only").orEmpty().ifBlank { null }?.let(::Regex)
            return Device.entries.flatMap { d ->
                Lang.entries.flatMap { l -> Shot.entries.map { s -> arrayOf<Any>(d, l, s) } }
            }.filter { (d, l, s) -> only == null || only.containsMatchIn("${(d as Device).name}-${(l as Lang).name}-${(s as Shot).name}".lowercase()) }
        }
    }

    @get:Rule val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<ComponentActivity>
    private val app get() = RuntimeEnvironment.getApplication() as TermoakApp
    private val core = FakeCore()

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun screenshot() {
        assumeTrue("store screenshots: run scripts/screenshots.sh", System.getProperty("termoak.store") == "1")
        RuntimeEnvironment.setQualifiers(device.qualifiers(lang))
        Demo.es = lang == Lang.ES
        EngineObjects.newScreen = { FakeScreen() }
        EngineObjects.newWatcher = { FakeWatcher() }
        EngineObjects.newLineTracker = { FakeLineTracker() }
        EngineObjects.newTransferHandle = { FakeTransferHandle() }
        injectCore()
        app.prefs.setTheme(ThemeMode.DARK)
        app.prefs.setHostStatusChecks(true)
        core.view = Demo.A1

        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { a ->
            a.enableEdgeToEdge()
            AccountNamesState.names = AccountNames(Demo.aliases, false)
            app.accounts.refreshNow()
            a.setContent {
                TermoakTheme(app.prefs) { Box(Modifier.fillMaxSize()) { AppRoot(app) } }
            }
        }
        settle()
        dispatchInsets()
        settle()

        try {
            scene()
        } catch (e: Throwable) {
            // What was on screen, to see why.
            File("build/store/failed").mkdirs()
            File("build/store/failed/${device.name}-${lang.name}-${shot.name}.txt").writeText(
                compose.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().joinToString("\n\n") { it.printTree() },
            )
            save(File("build/store/failed/${device.name}-${lang.name}-${shot.name}.png"))
            throw e
        }
        settle()
        save()
    }

    private fun androidx.compose.ui.semantics.SemanticsNode.printTree(indent: String = ""): String =
        indent + config.toString() + "\n" + children.joinToString("") { it.printTree("$indent  ") }

    private fun scene() {
        when (shot) {
            Shot.VAULT -> vault()
            Shot.TERMINAL -> terminal()
            Shot.SPLIT -> if (device.tablet) split() else copilot()
            Shot.AI -> aiTask()
            Shot.FILES -> files()
            Shot.EDITOR -> editor()
            Shot.SHARING -> sharing()
            Shot.SETTINGS -> settings()
        }
    }

    // ----- The screens -----

    private fun vault() {
        // Tablets: two terminals open in the background, as tabs of the desktop layout.
        if (device.tablet) {
            background("h-web-1") { c, r -> TermDemo.web1(c, r) }
            background("h-db-primary") { c, r -> TermDemo.broadcast("db-primary", c, r) }
        }
    }

    private fun terminal() {
        if (device.tablet) {
            background("h-db-primary") { c, r -> TermDemo.broadcast("db-primary", c, r) }
            background("h-backup") { c, r -> TermDemo.backup(c, r) }
            openTerminal("h-web-1") { c, r -> TermDemo.htop(c, r) }
        } else {
            openTerminal("h-web-1") { c, r -> TermDemo.web1(c, r) }
        }
    }

    /** Four terminals side by side, typing in all of them at once. */
    private fun split() {
        val ids = listOf("h-web-1", "h-web-2", "h-db-primary", "h-api-1").map { id ->
            val label = host(id).label
            background(id) { c, r -> TermDemo.broadcast(label, c, r) }
        }
        app.sessions.setSplit(ids)
        app.sessions.select(ids.first())
        app.sessions.setBroadcast(true)
        val h = host("h-web-1")
        app.pendingQuickAction.value = QuickAction.Host(h.id, h.accountId)
        settle()
        // The shortcut opened one more tab: the split keeps the four.
        app.sessions.list.value.lastOrNull()?.takeIf { it.id !in ids }?.let { app.sessions.close(it.id) }
        app.sessions.setSplit(ids)
        app.sessions.select(ids.first())
        app.sessions.setBroadcast(true)
        settle()
    }

    /** The phone's terminal with the copilot open beside it, after a question about a failing API. */
    private fun copilot() {
        openTerminal("h-web-1") { c, r -> TermDemo.web1Logs(c, r) }
        val session = app.sessions.list.value.first()
        val chat = app.copilot.chat(session)
        setState(chat, "taskId", "0192f0c3-3333-7000-8000-00000000c009")
        setState(chat, "status", AiTaskStatus.COMPLETED)
        setState(chat, "turns", listOf(
            Turn.User(Demo.tr("Why is /api/cart returning 502?", "¿Por qué /api/cart devuelve 502?")),
            Turn.Tool(
                "c1", "run_command", "systemctl status shop-api --no-pager | head -3",
                "× shop-api.service - Shop API\n     Loaded: loaded (/etc/systemd/system/shop-api.service; enabled)\n" +
                    "     Active: failed (Result: oom-kill) since Mon 2026-10-12 12:27:39 UTC; 2min ago",
                false,
            ),
            Turn.Assistant(
                Demo.tr(
                    "nginx gets **Connection refused** from its upstream because `shop-api` is down: systemd killed it at 12:27 when it ran out of memory (`oom-kill`).\n\n" +
                        "1. Restart it: `sudo systemctl restart shop-api`\n2. Then check `free -m` and consider raising `MemoryMax` in its unit.",
                    "nginx recibe **Connection refused** de su upstream porque `shop-api` está caído: systemd lo mató a las 12:27 al quedarse sin memoria (`oom-kill`).\n\n" +
                        "1. Reinícialo: `sudo systemctl restart shop-api`\n2. Después revisa `free -m` y plantéate subir `MemoryMax` en su unidad.",
                ),
            ),
        ))
        compose.onNodeWithContentDescription(str(R.string.copilot_title)).performClick()
        settle()
    }

    private fun aiTask() {
        // The test's clock by hand: with it running by itself the tests stop infinite animations, and the
        // "waiting" spinner would stay at its first frame (a speck).
        compose.mainClock.autoAdvance = false
        clickFirst(str(R.string.section_ai))
        settle()
        // The task in the list (the approval card above has the same title).
        compose.onAllNodesWithText(Demo.tr("Free space on backup", "Liberar disco en backup"))[1].performClick()
        settle(5)
    }

    private fun files() {
        hostMenu("web-1", str(R.string.files_sftp))
        settle(5)
        // Each file's ⋮: its actions (a sheet on phones, the details pane on wide windows), then Download.
        for (name in listOf("error.log", "backup-2026-10-11.tar.gz")) {
            optionsNear(name, str(R.string.common_options))
            settle()
            clickFirst(str(R.string.files_download))
            settle()
        }
        settle(3)
    }

    private fun editor() {
        // The editor panel takes a third of the window: the sidebar collapsed (to its icons) leaves the hosts room.
        if (device.tablet) {
            app.prefs.setSidebarCollapsed(true)
            settle(1)
        }
        hostMenu("web-1", str(R.string.common_edit))
        settle(4)
    }

    private fun sharing() {
        if (device.tablet) {
            clickFirst(str(R.string.vaults_title))
            settle()
            resetSidebar()
        } else {
            clickFirst(str(R.string.section_settings))
            settle()
            clickFirst(str(R.string.vaults_title))
        }
        settle()
        clickFirst("Infra")
        settle(4)
    }

    private fun settings() {
        clickFirst(str(R.string.section_settings))
        settle()
        if (device.tablet) {
            resetSidebar()
            scrollToTop(str(R.string.settings_font_size), 180f)
        } else {
            scrollToTop(str(R.string.settings_terminal_theme) + " · Termoak", 114f)
        }
        settle()
    }

    // ----- Getting there -----

    /** A Compose state property ([name], `by mutableStateOf`) of [obj], whatever its setter's visibility. */
    @Suppress("UNCHECKED_CAST")
    private fun setState(obj: Any, name: String, value: Any?) {
        val f = obj.javaClass.getDeclaredField("$name\$delegate")
        f.isAccessible = true
        scenario.onActivity { (f.get(obj) as androidx.compose.runtime.MutableState<Any?>).value = value }
    }

    private fun str(id: Int, vararg args: Any): String = app.getString(id, *args)

    private fun clickFirst(text: String) {
        val node = compose.onAllNodesWithText(text).onFirst()
        // Into view first (the desktop sidebar's last items are below the fold on short windows).
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    /** A host's menu: its ⋮ (phones) or a long press on its card (the desktop layout), then [action]. */
    private fun hostMenu(label: String, action: String) {
        if (device.tablet) {
            compose.onAllNodesWithText(label).onFirst().performTouchInput { longClick() }
        } else {
            optionsNear(label, str(R.string.common_options))
        }
        settle()
        clickFirst(action)
    }

    /** Taps the button described [description] on the same row as [text]. */
    private fun optionsNear(text: String, description: String) {
        val y = compose.onAllNodesWithText(text).onFirst().getBoundsInRoot().let { (it.top + it.bottom) / 2 }
        val options = compose.onAllNodesWithContentDescription(description)
        val n = options.fetchSemanticsNodes().size
        val best = (0 until n).minBy { i ->
            val b = options[i].getBoundsInRoot()
            Math.abs(((b.top + b.bottom) / 2 - y).value)
        }
        options[best].performClick()
    }

    /** The desktop sidebar back at its top (a tap on one of its last items scrolls it). */
    private fun resetSidebar() {
        compose.onAllNodes(hasScrollAction() and hasAnyDescendant(hasText(str(R.string.section_keychain))))
            .fetchSemanticsNodes().indices.forEach { i ->
                compose.onAllNodes(hasScrollAction() and hasAnyDescendant(hasText(str(R.string.section_keychain))))[i]
                    .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -10_000f) }
            }
        settle(1)
    }

    /** Scrolls the screen's list until [text] is [dp] below the top of the window. */
    private fun scrollToTop(text: String, dp: Float) {
        val scrollable = compose.onAllNodes(hasScrollAction() and hasAnyDescendant(hasText(text)))
            .fetchSemanticsNodes().lastIndex
        val node = compose.onAllNodes(hasScrollAction() and hasAnyDescendant(hasText(text)))[scrollable]
        node.performScrollToNode(hasText(text))
        settle(1)
        val top = compose.onAllNodesWithText(text).onFirst().getBoundsInRoot().top.value
        node.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, (top - dp) * device.density) }
    }

    private fun host(id: String) = Demo.hosts.first { it.id == id }

    /** Opens [hostId] in a terminal as the app's icon shortcut does, with [content] on its screen. */
    private fun openTerminal(hostId: String, content: (Int, Int) -> List<String>) {
        val h = host(hostId)
        app.pendingQuickAction.value = QuickAction.Host(h.id, h.accountId)
        settle()
        app.sessions.list.value.filter { it.hostId == hostId }.forEach { (it.screen as FakeScreen).content = content }
        settle()
    }

    /** A terminal open in the background (a tab); its id. */
    private fun background(hostId: String, content: (Int, Int) -> List<String>): String {
        var id = ""
        scenario.onActivity {
            val s = app.sessions.openLocal(host(hostId), activate = false)
            (s.screen as FakeScreen).content = content
            id = s.id
        }
        settle(1)
        return id
    }

    // ----- Harness -----

    /** The app's engine: the stand-in (TermoakApp makes its own on first use). */
    private fun injectCore() {
        val f = TermoakApp::class.java.getDeclaredField("core\$delegate")
        f.isAccessible = true
        f.set(app, lazyOf(core))
    }

    private fun settle(times: Int = 3) {
        repeat(times) {
            shadowOf(android.os.Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS)
            if (!compose.mainClock.autoAdvance) compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
        }
    }

    private fun dispatchInsets() {
        val d = device.density
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, (device.statusDp * d).toInt(), 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, (device.navDp * d).toInt()))
            .build()
        scenario.onActivity { ViewCompat.dispatchApplyWindowInsets(it.window.decorView, insets) }
    }

    private fun save(out: File = File("build/store/${lang.dir}/${device.dir}/${shot.file}.png")) {
        val raw = compose.onRoot().captureToImage().asAndroidBitmap()
        val bitmap = Bitmap.createScaledBitmap(raw, device.widthPx, device.heightPx, true).copy(Bitmap.Config.ARGB_8888, true)
        drawSystemBars(bitmap)
        out.parentFile!!.mkdirs()
        Png.writeOpaque(bitmap, out)
    }

    /** A clean status bar (12:30, Wi-Fi, signal, full battery) and the gesture handle, light over the dark app. */
    private fun drawSystemBars(bitmap: Bitmap) {
        val d = device.density
        val canvas = Canvas(bitmap)
        val fg = 0xFFF2F4F8.toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textSize = 14 * d
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val mid = device.statusDp * d / 2
        canvas.drawText("12:30", 16 * d, mid + 5 * d, paint)
        // Battery (full), from the right.
        val right = bitmap.width - 16 * d
        val body = RectF(right - 22 * d, mid - 6 * d, right - 2 * d, mid + 6 * d)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(body, 2.5f * d, 2.5f * d, paint)
        canvas.drawRoundRect(RectF(right - 2 * d, mid - 3 * d, right, mid + 3 * d), 1 * d, 1 * d, paint)
        // Signal: a filled triangle.
        val sx = right - 30 * d
        canvas.drawPath(Path().apply {
            moveTo(sx, mid - 7 * d); lineTo(sx, mid + 7 * d); lineTo(sx - 14 * d, mid + 7 * d); close()
        }, paint)
        // Wi-Fi: a filled fan.
        val wx = sx - 26 * d
        val r = 9 * d
        canvas.drawArc(RectF(wx - r, mid + 6 * d - r, wx + r, mid + 6 * d + r), 225f, 90f, true, paint)
        // The gesture handle.
        val y = bitmap.height - device.navDp * d / 2
        paint.color = 0xFFD6DBE4.toInt()
        canvas.drawRoundRect(RectF(bitmap.width / 2f - 54 * d, y - 2 * d, bitmap.width / 2f + 54 * d, y + 2 * d), 2 * d, 2 * d, paint)
    }
}
