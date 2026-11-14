package com.example.samplee.util

/**
 * Map settings in ONE place.
 *
 * STYLE_URL: a free MapLibre vector style from OpenFreeMap (no API key needed).
 *   Other free choices: ".../styles/liberty" (more detail) or ".../styles/positron" (light grey).
 *
 * SCROLL LIMIT: the camera can only move inside the box below, which covers
 * Poblacion 1-12, Wawa, Bucana, Lumbangan, Cogunan, Pantalan and Putat (Nasugbu, Batangas).
 * To widen or shrink the area, edit the four BOUNDS_* numbers.
 */
object MapConfig {

    const val STYLE_URL = "https://tiles.openfreemap.org/styles/bright"

    // Starting view: centre of Nasugbu poblacion
    const val CENTER_LAT = 14.0720
    const val CENTER_LNG = 120.6330
    const val START_ZOOM = 13.0

    // Zoom limits (cannot zoom out to the whole country, cannot over-zoom)
    const val MIN_ZOOM = 12.5
    const val MAX_ZOOM = 18.0

    // Scroll limit box (degrees)
    const val BOUNDS_NORTH = 14.112
    const val BOUNDS_SOUTH = 14.030
    const val BOUNDS_EAST = 120.680
    const val BOUNDS_WEST = 120.600
}
