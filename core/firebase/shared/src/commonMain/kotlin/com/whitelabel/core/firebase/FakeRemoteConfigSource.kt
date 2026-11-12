package com.whitelabel.core.firebase

/** An in-memory source for the FAKE mode and tests. Fetching changes nothing. */
class FakeRemoteConfigSource(private val values: Map<String, String>) : RemoteConfigSource {
    override fun raw(key: String): String? = values[key]
    override fun fetchAndActivate(onDone: (changed: Boolean) -> Unit) = onDone(false)
}
