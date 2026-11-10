package com.whitelabel.core.shell

import android.app.Application

/** Base for every app's Application class, which adds `@HiltAndroidApp` (a Hilt requirement). */
open class FoundationApplication : Application()
