package com.whitelabel.core.firebase.google

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.whitelabel.core.firebase.RemoteConfigSource

/** [RemoteConfigSource] over Firebase Remote Config. A fresh fetch is allowed every [minimumFetchIntervalSeconds]. */
class FirebaseRemoteConfigSource(
    private val config: FirebaseRemoteConfig,
    minimumFetchIntervalSeconds: Long,
) : RemoteConfigSource {
    init {
        config.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(minimumFetchIntervalSeconds).build(),
        )
    }

    override fun raw(key: String): String? {
        val value = config.getValue(key)
        // Static means "no remote value and no in-app default": absent, not an empty string.
        return if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) null else value.asString()
    }

    override fun fetchAndActivate(onDone: (Boolean) -> Unit) {
        runCatching {
            config.fetchAndActivate().addOnCompleteListener { task ->
                onDone(task.isSuccessful && task.result == true)
            }
        }.onFailure { onDone(false) }
    }
}
