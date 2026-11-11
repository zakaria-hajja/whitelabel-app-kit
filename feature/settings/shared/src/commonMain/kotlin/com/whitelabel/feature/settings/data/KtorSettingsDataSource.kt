package com.whitelabel.feature.settings.data

import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.EmptyResult
import com.whitelabel.core.common.Result
import com.whitelabel.core.common.asEmptyResult
import com.whitelabel.core.common.map
import com.whitelabel.core.network.getResult
import com.whitelabel.core.network.patchResult
import com.whitelabel.feature.settings.domain.NotificationSettings
import com.whitelabel.feature.settings.domain.SettingsDataSource
import com.whitelabel.feature.settings.domain.SettingsPatch
import io.ktor.client.HttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** The parts of `SettingsDto` the app uses; theme and language live on the device. */
@Serializable
private data class SettingsDto(val notifications: NotificationsDto)

@Serializable
private data class NotificationsDto(val push: Boolean, val email: Boolean, val marketing: Boolean)

/** Null properties are omitted from the body (the client drops nulls), which is what makes it partial. */
@Serializable
private data class SettingsPatchDto(
    val theme: String? = null,
    val language: String? = null,
    val notifications: NotificationsPatchDto? = null,
)

@Serializable
private data class NotificationsPatchDto(val push: Boolean? = null, val email: Boolean? = null, val marketing: Boolean? = null)

internal class KtorSettingsDataSource(
    private val client: HttpClient,
) : SettingsDataSource {

    override suspend fun get(): Result<NotificationSettings, DataError> =
        client.getResult<SettingsDto>("settings").map { NotificationSettings(it.notifications.push, it.notifications.email, it.notifications.marketing) }

    override suspend fun patch(patch: SettingsPatch): EmptyResult<DataError> {
        val notifications = NotificationsPatchDto(patch.push, patch.email, patch.marketing)
            .takeIf { patch.push != null || patch.email != null || patch.marketing != null }
        return client.patchResult<SettingsPatchDto, JsonObject>("settings", SettingsPatchDto(patch.theme, patch.language, notifications))
            .asEmptyResult()
    }
}
