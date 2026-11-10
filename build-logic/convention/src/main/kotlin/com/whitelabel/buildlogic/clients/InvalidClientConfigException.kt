package com.whitelabel.buildlogic.clients

import org.gradle.api.GradleException

class InvalidClientConfigException(val problems: List<String>) :
    GradleException("Invalid client configuration:\n- " + problems.joinToString("\n- "))
