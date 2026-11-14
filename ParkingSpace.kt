package com.example.samplee.model

/**
 * ABSTRACTION + INHERITANCE: the parent ("GeographicFeature") of every
 * parking location. It holds what ALL spaces share; PaidParkingSpace and
 * FreeParkingSpace specialise it.
 *
 * ENCAPSULATION: coordinates and slot counts are protected by validating
 * setters; occupancy can only change through updateOccupancy().
 */
abstract class ParkingSpace(
    val id: Long,
    val name: String,
    val barangay: String,
    val address: String,
    latitude: Double,
    longitude: Double,
    totalSlots: Int
) : Reservable {

    var latitude: Double = 0.0
        set(value) {
            require(value in -90.0..90.0) { "Latitude must be between -90 and 90." }
            field = value
        }

    var longitude: Double = 0.0
        set(value) {
            require(value in -180.0..180.0) { "Longitude must be between -180 and 180." }
            field = value
        }

    var totalSlots: Int = 1
        private set(value) {
            require(value > 0) { "A parking space needs at least one slot." }
            field = value
        }

    var occupiedSlots: Int = 0
        private set

    val availableSlots: Int get() = totalSlots - occupiedSlots
    val isAvailable: Boolean get() = availableSlots > 0

    init {
        this.latitude = latitude
        this.longitude = longitude
        this.totalSlots = totalSlots
    }

    fun updateOccupancy(occupied: Int) {
        occupiedSlots = occupied.coerceIn(0, totalSlots)
    }

    // ---- Abstract members: every child MUST provide its own version ----
    abstract val isPaid: Boolean
    abstract val markerColor: String
    abstract val actionLabel: String

    /** POLYMORPHISM: same call, different text depending on the real object. */
    abstract fun describe(): String

    /** Open so a child may override it; default wording is shared. */
    open fun availabilityLabel(): String =
        if (isAvailable) "Available • $availableSlots of $totalSlots slots free"
        else "Full • 0 of $totalSlots slots free"
}
