package com.termoak.app

import com.termoak.ffi.CommandWatcher
import com.termoak.ffi.LineTracker
import com.termoak.ffi.TerminalScreen
import com.termoak.ffi.TransferHandle

/**
 * The engine's objects the app makes itself: each terminal's emulator, the
 * watcher of its commands and the tracker of the line being typed, and the
 * handle that cancels a transfer. The JVM tests, where the engine's native
 * library can't load, give stand-ins (the store screenshots).
 */
internal object EngineObjects {
    @Volatile var newScreen: () -> TerminalScreen = { TerminalScreen(80u, 24u, 0u) }
    @Volatile var newWatcher: (TerminalScreen) -> CommandWatcher = { CommandWatcher(it) }
    @Volatile var newLineTracker: () -> LineTracker = { LineTracker() }
    @Volatile var newTransferHandle: () -> TransferHandle = { TransferHandle() }
}
