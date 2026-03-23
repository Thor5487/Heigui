package com.iq200.heigui.events.core

import com.iq200.heigui.utils.logError

abstract class CancellableEvent : Event {
    var isCancelled = false
        private set

    fun cancel() {
        isCancelled = true
    }

    override fun postAndCatch(): Boolean {
        runCatching {
            EventBus.post(this)
        }.onFailure {
            logError(it, this)
        }
        return isCancelled
    }
}