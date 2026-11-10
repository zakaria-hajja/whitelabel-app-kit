package com.whitelabel.core.shell

/** `com.example.DetailRoute/{id}` → `DetailRoute`: the analytics name of a typed-route destination. */
internal fun screenName(route: String): String =
    route.substringBefore('/').substringBefore('?').substringAfterLast('.')
