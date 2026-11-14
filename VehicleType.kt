package com.example.samplee.model

/** The three vehicle classes the parking fee depends on. */
enum class VehicleType(val label: String, val wheels: Int) {
    TWO_WHEELS("2 Wheels (Motorcycle)", 2),
    THREE_WHEELS("3 Wheels (Tricycle)", 3),
    FOUR_WHEELS("4 Wheels (Car / Van)", 4);

    // Spinners display toString(), so show the friendly label.
    override fun toString(): String = label
}