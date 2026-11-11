package com.whitelabel.core.ios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore

/**
 * Owns one shared ViewModel for a SwiftUI screen, as a navigation entry does on Android: [clear]
 * (from `SharedModel`'s deinit) cancels its work. Swift can't call a ViewModel's onCleared itself.
 */
class ViewModelHolder(val viewModel: ViewModel) {
    private val store = ViewModelStore().apply {
        @Suppress("RestrictedApi")
        put("shared", viewModel)
    }

    var isCleared: Boolean = false
        private set

    fun clear() {
        if (isCleared) return
        store.clear()
        isCleared = true
    }
}
