package com.whitelabel.core.ads

/**
 * A banner slot the app declares. [name] keys its unit (`ads.<platform>.banner.<name>`); [underTab],
 * when set, is a tab's route (Android: the route object; iOS: the tab's route id): the shell draws
 * the banner below that tab's root screen. Without it, the app places `AdBanner(name)` itself.
 */
data class AdPlacement(val name: String, val underTab: Any?)

/** The `ads { }` block of an app definition: `ads { placement("home", underTab = HomeRoute) }`. */
class AdsBuilder {
    private val placements = mutableListOf<AdPlacement>()

    /** Declares a banner slot; see [AdPlacement]. Names start with a letter and use letters, digits, `_` and `-`. */
    fun placement(name: String, underTab: Any? = null) {
        require(PLACEMENT_NAME.matches(name)) {
            "placement '$name' must start with a letter and use only letters, digits, '_' and '-'"
        }
        require(placements.none { it.name == name }) { "placement '$name' is declared twice" }
        placements += AdPlacement(name, underTab)
    }

    /** The placements in declaration order. */
    fun build(): List<AdPlacement> = placements.toList()

    private companion object {
        // The same rule the build applies to ads.<platform>.banner.<name>.
        val PLACEMENT_NAME = Regex("^[A-Za-z][A-Za-z0-9_-]*$")
    }
}
