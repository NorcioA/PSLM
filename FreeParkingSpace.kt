package com.example.samplee.model

/** A free parking zone: no fee and no pre-booking. Park any time. */
class FreeParkingSpace(
    id: Long,
    name: String,
    barangay: String,
    address: String,
    latitude: Double,
    longitude: Double,
    totalSlots: Int
) : ParkingSpace(id, name, barangay, address, latitude, longitude, totalSlots) {

    override val isPaid: Boolean = false
    override val markerColor: String = "#2E7D32"
    override val actionLabel: String = "Park Here (Free)"

    override fun requiresBooking(): Boolean = false

    override fun feeFor(type: VehicleType, tier: DurationTier): Double = 0.0

    override fun describe(): String =
        "FREE parking • No booking and no fee • Park any time while space is available."

    // POLYMORPHISM: a free lot talks about "spaces", not reserved "slots".
    override fun availabilityLabel(): String =
        if (isAvailable) "Open • $availableSlots of $totalSlots spaces free"
        else "Full • 0 of $totalSlots spaces free"
}
