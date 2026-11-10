package com.whitelabel.buildlogic.apps

import org.gradle.api.GradleException

class InvalidAppConfigException(val problems: List<String>) :
    GradleException("Invalid app configuration:\n- " + problems.joinToString("\n- "))
