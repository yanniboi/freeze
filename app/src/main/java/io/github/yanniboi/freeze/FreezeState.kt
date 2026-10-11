package io.github.yanniboi.freeze

import java.util.concurrent.CopyOnWriteArraySet

enum class Mode {
    /** Nothing running. */
    OFF,

    /** Service and notification are up; touch works normally. */
    READY,

    /** Overlay is attached and swallowing touches. */
    LOCKED,
}

/**
 * In-memory state shared by the service, tile, widget and setup screen. They all live in one
 * process, and the service is the only writer. If the process dies the phone is unlocked anyway, so
 * there is nothing worth persisting.
 */
object FreezeState {
    @Volatile
    var mode: Mode = Mode.OFF
        private set

    private val listeners = CopyOnWriteArraySet<(Mode) -> Unit>()

    fun addListener(listener: (Mode) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (Mode) -> Unit) {
        listeners -= listener
    }

    internal fun set(mode: Mode) {
        if (this.mode == mode) return
        this.mode = mode
        listeners.forEach { it(mode) }
    }
}
