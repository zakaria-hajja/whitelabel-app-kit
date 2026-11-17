package com.whitelabel.core.ads

/** What a placement shows: an adaptive banner, or one native ad as a list item. */
enum class AdKind { BANNER, NATIVE }

/**
 * An ad slot the app declares. [name] keys its unit (`ads.<platform>.banner.<name>` for a banner,
 * `ads.<platform>.native.<name>` for a native ad). [underTab], banners only, when set, is a tab's
 * route (Android: the route object; iOS: the tab's route id): the shell draws the banner below that
 * tab's root screen. Without it, the app places `AdBanner(name)` (or a native ad item) itself.
 */
data class AdPlacement(val name: String, val underTab: Any?, val kind: AdKind) {
    /** A banner placement; kept for Swift, which has no Kotlin default arguments. */
    constructor(name: String, underTab: Any?) : this(name, underTab, AdKind.BANNER)
}

/** The `ads { }` block of an app definition: `ads { placement("home", underTab = HomeRoute) }`. */
class AdsBuilder {
    private val placements = mutableListOf<AdPlacement>()

    /** Declares a banner slot; see [AdPlacement]. Names start with a letter and use letters, digits, `_` and `-`. */
    fun placement(name: String, underTab: Any? = null) = add(AdPlacement(name, underTab, AdKind.BANNER))

    /**
     * Declares a native ad placement: one native ad the app shows as a list item. Same name rule as
     * [placement], and a name is unique across both kinds. It has no tab: the app places it.
     */
    fun native(name: String) = add(AdPlacement(name, null, AdKind.NATIVE))

    private fun add(placement: AdPlacement) {
        val name = placement.name
        require(PLACEMENT_NAME.matches(name)) {
            "placement '$name' must start with a letter and use only letters, digits, '_' and '-'"
        }
        require(placements.none { it.name == name }) { "placement '$name' is declared twice" }
        placements += placement
    }

    /** The placements in declaration order. */
    fun build(): List<AdPlacement> = placements.toList()

    private companion object {
        // The same rule the build applies to ads.<platform>.banner.<name> and ads.<platform>.native.<name>.
        val PLACEMENT_NAME = Regex("^[A-Za-z][A-Za-z0-9_-]*$")
    }
}
