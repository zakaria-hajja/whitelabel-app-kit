package com.whitelabel.core.firebase

import com.whitelabel.core.network.ModuleContributor

/**
 * Marks the contributor that binds the real Firebase pieces (`:core:firebase:google`'s). The shell
 * finds it by type, because R8 renames classes in release builds, so a class-name check would miss it.
 */
interface FirebaseServicesContributor : ModuleContributor
