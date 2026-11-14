package com.example.samplee.model

/** A parking space that charges a fee and REQUIRES a booking (date + time). */
class PaidParkingSpace(
    id: Long,
    name: String,
    barangay: String,
    address: String,
    latitude: Double,
    longitude: Double,
    totalSlots: Int
) : ParkingSpace(id, name, barangay, address, latitude, longitude, totalSlots) {

    override val isPaid: Boolean = true
    override val markerColor: String = "#1565C0"
    override val actionLabel: String = "Book a Slot"

    override fun requiresBooking(): Boolean = true

    override fun feeFor(type: VehicleType, tier: DurationTier): Double =
        PricingMatrix.priceOf(type, tier)

    override fun describe(): String =
        "PAID parking • Booking with date & time is required • " +
                "${PricingMatrix.formatPeso(PricingMatrix.lowestPrice())} to " +
                "${PricingMatrix.formatPeso(PricingMatrix.highestPrice())} depending on vehicle and duration."
}
