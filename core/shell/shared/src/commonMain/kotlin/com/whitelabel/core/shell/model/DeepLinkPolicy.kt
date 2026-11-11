package com.whitelabel.core.shell.model

enum class DeepLinkAction { OPEN, HOLD, IGNORE }

/**
 * What to do with an incoming deep link. With a sign-in gate, a link into the main graph waits
 * until sign-in, and a link into the auth graph (e.g. reset password) only applies while signed out.
 */
fun deepLinkAction(inMain: Boolean, inAuth: Boolean, signedIn: Boolean, hasGate: Boolean): DeepLinkAction =
    when {
        !hasGate -> if (inMain) DeepLinkAction.OPEN else DeepLinkAction.IGNORE
        inMain -> if (signedIn) DeepLinkAction.OPEN else DeepLinkAction.HOLD
        inAuth -> if (signedIn) DeepLinkAction.IGNORE else DeepLinkAction.OPEN
        else -> DeepLinkAction.IGNORE
    }
