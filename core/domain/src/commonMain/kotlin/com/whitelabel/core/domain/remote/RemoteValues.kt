package com.whitelabel.core.domain.remote

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer

/**
 * Read-only, remotely configurable values (feature flags, limits, copy).
 *
 * Defaults always come from the caller; a missing, empty, wrong-type or garbled value returns the
 * default and never throws.
 */
interface RemoteValues {
    fun long(key: String, default: Long): Long
    fun double(key: String, default: Double): Double
    fun string(key: String, default: String): String
    fun boolean(key: String, default: Boolean): Boolean
    fun <T> json(key: String, default: T, serializer: KSerializer<T>): T

    /** Emits each time newly fetched values are activated; readers then re-read what they use. */
    val changes: Flow<Unit>
}
