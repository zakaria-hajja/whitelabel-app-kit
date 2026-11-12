package com.whitelabel.core.consent

/** TCF purpose 1 (store and/or access information on a device) is the first character of the purpose-consent string. */
fun storageAllowed(purposeConsents: String?): Boolean = purposeConsents?.firstOrNull() == '1'
