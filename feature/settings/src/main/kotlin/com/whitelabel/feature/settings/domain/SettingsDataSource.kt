package com.whitelabel.feature.settings.domain

import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.EmptyResult
import com.whitelabel.core.common.Result

/** The user's notification choices, kept on the server. */
internal data class NotificationSettings(val push: Boolean, val email: Boolean, val marketing: Boolean)

internal enum class NotificationKind { PUSH, EMAIL, MARKETING }

/** A partial update: `null` fields are left unchanged on the server. */
internal data class SettingsPatch(
    val theme: String? = null,
    val language: String? = null,
    val push: Boolean? = null,
    val email: Boolean? = null,
    val marketing: Boolean? = null,
)

/** `GET`/`PATCH settings`; both need a session. */
internal interface SettingsDataSource {
    suspend fun get(): Result<NotificationSettings, DataError>

    suspend fun patch(patch: SettingsPatch): EmptyResult<DataError>
}
