package com.whitelabel.buildlogic.apps

/** How an optional service (Firebase) is switched on: `false`, `true` (every build type) or `debug` (debug builds only). */
enum class ServiceMode { OFF, ON, DEBUG }
