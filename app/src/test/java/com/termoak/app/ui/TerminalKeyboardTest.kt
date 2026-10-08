package com.termoak.app.ui

import android.app.Application
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.termoak.app.term.TerminalView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The on-screen keyboard and leaving the terminal: a NavHost with Home and
 * the terminal screen (two terminals, as tabs, in it) and the screen's
 * KeyboardLeavesWithTerminal, the IME watched through Robolectric's shadow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class TerminalKeyboardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController
    private val terminals = arrayOfNulls<TerminalView>(2)
    /** The terminal tab in view (0 or 1). */
    private var tab by mutableIntStateOf(0)

    private fun keyboardShown(): Boolean =
        shadowOf(rule.activity.getSystemService(InputMethodManager::class.java)).isSoftInputVisible

    private fun app() {
        rule.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = Routes.HOSTS) {
                composable(Routes.HOSTS) { Text("Home") }
                composable(Routes.SETTINGS) { Text("Settings") }
                composable(Routes.TERMINAL) {
                    val views = remember { mutableListOf<TerminalView>() }
                    KeyboardLeavesWithTerminal(nav, views)
                    Column {
                        // One view per tab, like a pane per terminal: only the one in view is composed.
                        val i = tab
                        androidx.compose.runtime.key(i) {
                            AndroidView(
                                { ctx -> TerminalView(ctx).also { v -> views += v; terminals[i] = v } },
                                Modifier.fillMaxWidth().height(300.dp),
                                onRelease = { v -> views -= v },
                            )
                        }
                    }
                }
            }
        }
        rule.runOnUiThread { nav.navigate(Routes.TERMINAL) { launchSingleTop = true } }
        rule.waitForIdle()
        rule.runOnUiThread { terminals[0]!!.showKeyboard() }
        rule.waitForIdle()
        assertTrue(keyboardShown())
        assertTrue(terminals[0]!!.hasFocus())
    }

    @Test
    fun backToHomeHidesTheKeyboard() {
        app()
        // Back (the gesture, the button or the screen's arrow): popBackStack.
        rule.runOnUiThread { nav.popBackStack() }
        rule.waitForIdle()
        assertFalse(keyboardShown())
    }

    @Test
    fun theHomeTabHidesTheKeyboard() {
        app()
        // The Home tab, on a phone or in the desktop layout.
        rule.runOnUiThread { nav.goTab(Routes.HOSTS) }
        rule.waitForIdle()
        assertEquals(Routes.HOSTS, nav.currentDestination?.route)
        assertFalse(keyboardShown())
    }

    /** Home from the terminal opened from the Vault lands on Home, and the Vault tab doesn't bring the terminal back. */
    @Test
    fun homeTabLeavesTheTerminal() {
        app()
        rule.runOnUiThread { nav.goTab(Routes.HOSTS) }
        rule.waitForIdle()
        assertEquals(listOf(Routes.HOSTS), routes())
        rule.runOnUiThread { nav.navigate(Routes.TERMINAL) { launchSingleTop = true } }
        rule.waitForIdle()
        rule.runOnUiThread { nav.goTab(Routes.SETTINGS) }
        rule.waitForIdle()
        assertEquals(listOf(Routes.HOSTS, Routes.SETTINGS), routes())
        rule.runOnUiThread { nav.goTab(Routes.HOSTS) }
        rule.waitForIdle()
        assertEquals(listOf(Routes.HOSTS), routes())
    }

    private fun routes() = nav.currentBackStack.value.mapNotNull { it.destination.route }

    @Test
    fun anotherSectionHidesTheKeyboard() {
        app()
        rule.runOnUiThread { nav.goTab(Routes.SETTINGS) }
        rule.waitForIdle()
        assertFalse(keyboardShown())
    }

    @Test
    fun hiddenAtOnceNotAfterTheFadeOut() {
        app()
        // Right when the destination changes, without letting the transition run.
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { nav.goTab(Routes.HOSTS) }
        assertFalse(keyboardShown())
        rule.mainClock.autoAdvance = true
    }

    @Test
    fun switchingTerminalsKeepsTheKeyboard() {
        app()
        // Another tab: its terminal takes the focus and the keyboard stays.
        rule.runOnUiThread { tab = 1 }
        rule.waitForIdle()
        rule.runOnUiThread { terminals[1]!!.showKeyboard() }
        rule.waitForIdle()
        assertTrue(keyboardShown())
        assertTrue(terminals[1]!!.hasFocus())
        // And then Home: hidden.
        rule.runOnUiThread { nav.goTab(Routes.HOSTS) }
        rule.waitForIdle()
        assertFalse(keyboardShown())
    }
}
