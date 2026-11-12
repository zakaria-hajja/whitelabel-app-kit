package com.whitelabel.core.domain.remote

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.KSerializer

/** The fallback for apps without a remote-config backend: every read returns the caller's default. */
object DefaultsOnlyRemoteValues : RemoteValues {
    override fun long(key: String, default: Long): Long = default
    override fun double(key: String, default: Double): Double = default
    override fun string(key: String, default: String): String = default
    override fun boolean(key: String, default: Boolean): Boolean = default
    override fun <T> json(key: String, default: T, serializer: KSerializer<T>): T = default
    override val changes: Flow<Unit> = emptyFlow()
}
