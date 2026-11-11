package com.whitelabel.core.shell

// The deep-link policy is shared with iOS: see com.whitelabel.core.shell.model.deepLinkAction.
internal typealias DeepLinkAction = com.whitelabel.core.shell.model.DeepLinkAction

internal fun deepLinkAction(inMain: Boolean, inAuth: Boolean, signedIn: Boolean, hasGate: Boolean): DeepLinkAction =
    com.whitelabel.core.shell.model.deepLinkAction(inMain, inAuth, signedIn, hasGate)
