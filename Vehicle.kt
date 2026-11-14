package com.example.samplee.model

/**
 * ENCAPSULATION: plate and colour are validated by custom setters, so a
 * Vehicle object can never hold an invalid plate number or an empty colour.
 */
class Vehicle(plateNumber: String, val type: VehicleType, color: String) {

    var plateNumber: String = ""
        set(value) {
            val cleaned = value.trim().uppercase()
            require(PLATE_PATTERN.matches(cleaned)) {
                "Enter a valid plate number (example: ABC 1234)."
            }
            field = cleaned
        }

    var color: String = ""
        set(value) {
            val cleaned = value.trim()
            require(cleaned.length in 3..20) { "Enter the vehicle colour (example: Red)." }
            field = cleaned.replaceFirstChar { it.uppercase() }
        }

    init {
        this.plateNumber = plateNumber
        this.color = color
    }

    companion object {
        private val PLATE_PATTERN = Regex("^[A-Z0-9]{2,4}[ -]?[A-Z0-9]{2,5}$")
    }
}
