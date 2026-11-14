package com.example.samplee.model

data class User(
    val id: Long,
    val fullName: String,
    val email: String,
    val phone: String
)

enum class BookingStatus(val label: String, val colorHex: String) {
    BOOKED("Booked", "#1565C0"),
    ACTIVE("Parked (in progress)", "#2E7D32"),
    COMPLETED("Completed", "#455A64"),
    CANCELLED("Cancelled", "#C62828"),
    EXPIRED("Expired (no-show)", "#EF6C00")
}

/** One reservation joined with its ticket pass, slot and space. */
data class Booking(
    val id: Long,
    val ticketNo: String,
    val userId: Long,
    val spaceId: Long,
    val spaceName: String,
    val barangay: String,
    val slotLabel: String,
    val plateNumber: String,
    val vehicleType: VehicleType,
    val vehicleColor: String,
    val tier: DurationTier,
    val startMillis: Long,
    val endMillis: Long,
    val baseFee: Double,
    val status: BookingStatus,
    val createdAt: Long,
    val entryMillis: Long?,
    val exitMillis: Long?,
    val overstayFee: Double
) {
    val totalFee: Double get() = baseFee + overstayFee
}

data class FreeSession(
    val id: Long,
    val spaceId: Long,
    val spaceName: String,
    val barangay: String,
    val plateNumber: String,
    val vehicleType: VehicleType,
    val vehicleColor: String,
    val timeIn: Long,
    val timeOut: Long?
)

data class ParkingLog(
    val id: Long,
    val spaceName: String,
    val plateNumber: String,
    val action: String,
    val logTime: Long,
    val note: String?
)

/** One parking slot and whether it is free for a given date/time window. */
data class slotInfo(
    val id: Long,
    val label: String,
    val isFree: Boolean
)

sealed class BookingResult {
    data class Success(val bookingId: Long) : BookingResult()
    data class Failure(val message: String) : BookingResult()
}
