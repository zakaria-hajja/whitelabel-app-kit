package com.whitelabel.core.consent.google

import android.content.Context
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.whitelabel.core.consent.ConsentPort
import com.whitelabel.core.consent.UmpResult
import com.whitelabel.core.consent.UmpStatus

/** Name of the Debug marker file (under `filesDir/debug`) that forces the EEA geography. */
const val DEBUG_EEA_MARKER = "consent-eea"

internal fun umpStatusOf(code: Int): UmpStatus = when (code) {
    ConsentInformation.ConsentStatus.REQUIRED -> UmpStatus.REQUIRED
    ConsentInformation.ConsentStatus.NOT_REQUIRED -> UmpStatus.NOT_REQUIRED
    ConsentInformation.ConsentStatus.OBTAINED -> UmpStatus.OBTAINED
    else -> UmpStatus.UNKNOWN
}

/** [ConsentPort] backed by Google's User Messaging Platform. SDK behavior is checked on a device. */
class UmpConsentPort(
    context: Context,
    private val activity: CurrentActivity,
    private val debugGeographyEea: Boolean,
) : ConsentPort {
    private val appContext = context.applicationContext
    private val info: ConsentInformation get() = UserMessagingPlatform.getConsentInformation(appContext)

    override fun gather(onDone: (UmpResult?) -> Unit) {
        val current = activity.get()
        if (current == null) {
            onDone(null)
            return
        }
        val params = ConsentRequestParameters.Builder().apply {
            if (debugGeographyEea) {
                setConsentDebugSettings(
                    ConsentDebugSettings.Builder(appContext)
                        .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                        .build(),
                )
            }
        }.build()
        info.requestConsentInfoUpdate(
            current,
            params,
            {
                // The activity may have gone while the info update ran.
                val showOn = activity.get()
                if (showOn == null) {
                    onDone(null)
                } else {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(showOn) { formError ->
                        onDone(if (formError == null) result() else null)
                    }
                }
            },
            { onDone(null) },
        )
    }

    override fun privacyOptionsRequired(): Boolean =
        info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    override fun showPrivacyOptions(onDone: () -> Unit) {
        val current = activity.get()
        if (current == null) {
            onDone()
        } else {
            UserMessagingPlatform.showPrivacyOptionsForm(current) { onDone() }
        }
    }

    private fun result() = UmpResult(
        status = umpStatusOf(info.consentStatus),
        canRequestAds = info.canRequestAds(),
        purposeConsents = appContext.getSharedPreferences("${appContext.packageName}_preferences", Context.MODE_PRIVATE)
            .getString("IABTCF_PurposeConsents", null),
    )
}
