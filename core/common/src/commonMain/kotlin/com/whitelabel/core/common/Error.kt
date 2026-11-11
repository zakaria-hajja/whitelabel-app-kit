package com.whitelabel.core.common

/**
 * Marker for typed domain errors. Intentionally shadows `kotlin.Error` inside this package so
 * `Result<D, E : Error>` bounds refer to the domain type.
 */
interface Error
