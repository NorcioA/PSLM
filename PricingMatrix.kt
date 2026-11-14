package com.example.samplee.model

import java.util.Locale

/**
 * Single source of truth for parking fees (Philippine peso).
 *
 *  Duration | 2 wheels | 3 wheels | 4 wheels
 *  30 mins  |   10     |   15     |   20
 *  1 hr     |   15     |   25     |   40
 *  3 hrs    |   30     |   50     |   80
 *  5 hrs    |   45     |   75     |  120
 *  24 hrs   |  100     |  150     |  250
 *
 * Staying beyond the booked time is charged per started 30-minute block
 * at the vehicle's 30-minute rate.
 */
object PricingMatrix {

    private const val OVERSTAY_BLOCK_MS = 30 * 60 * 1000L

    private fun row(m30: Double, h1: Double, h3: Double, h5: Double, h24: Double) = mapOf(
        DurationTier.THIRTY_MIN to m30,
        DurationTier.ONE_HOUR to h1,
        DurationTier.THREE_HOURS to h3,
        DurationTier.FIVE_HOURS to h5,
        DurationTier.TWENTY_FOUR_HOURS to h24
    )

    private val matrix: Map<VehicleType, Map<DurationTier, Double>> = mapOf(
        VehicleType.TWO_WHEELS to row(10.0, 15.0, 30.0, 45.0, 100.0),
        VehicleType.THREE_WHEELS to row(15.0, 25.0, 50.0, 75.0, 150.0),
        VehicleType.FOUR_WHEELS to row(20.0, 40.0, 80.0, 120.0, 250.0)
    )

    fun priceOf(type: VehicleType, tier: DurationTier): Double =
        matrix.getValue(type).getValue(tier)

    /** Extra charge when a vehicle exits after its booked end time. */
    fun overstayFee(type: VehicleType, endMillis: Long, atMillis: Long): Double {
        if (atMillis <= endMillis) return 0.0
        val blocks = (atMillis - endMillis + OVERSTAY_BLOCK_MS - 1) / OVERSTAY_BLOCK_MS
        return blocks * priceOf(type, DurationTier.THIRTY_MIN)
    }

    fun lowestPrice(): Double = matrix.values.flatMap { it.values }.minOrNull() ?: 0.0

    fun highestPrice(): Double = matrix.values.flatMap { it.values }.maxOrNull() ?: 0.0

    fun formatPeso(amount: Double): String =
        "₱" + String.format(Locale.US, "%,.2f", amount)

    /** Monospace rate card shown on the booking screen. */
    fun ratesTable(): String {
        val sb = StringBuilder()
        sb.append("Rates (PHP)".padEnd(12))
        for (type in VehicleType.values()) sb.append("${type.wheels}-wheel".padStart(9))
        sb.append('\n')
        for (tier in DurationTier.values()) {
            sb.append(tier.label.padEnd(12))
            for (type in VehicleType.values()) {
                sb.append(String.format(Locale.US, "%.0f", priceOf(type, tier)).padStart(9))
            }
            sb.append('\n')
        }
        return sb.toString().trimEnd()
    }
}
