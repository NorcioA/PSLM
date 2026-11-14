package com.example.samplee.model

/**
 * ABSTRACTION: the only things the UI needs to know about how a parking
 * space is reserved and charged. Paid and free spaces answer differently.
 */
interface Reservable {
    /** True when date & time of booking are mandatory before parking. */
    fun requiresBooking(): Boolean

    /** Fee for the given vehicle type and duration tier. */
    fun feeFor(type: VehicleType, tier: DurationTier): Double
}
