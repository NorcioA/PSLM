package com.example.samplee.model

/** The exact parking duration options offered for paid parking. */
enum class DurationTier(val label: String, val minutes: Int) {
    THIRTY_MIN("30 mins", 30),
    ONE_HOUR("1 hr", 60),
    THREE_HOURS("3 hrs", 180),
    FIVE_HOURS("5 hrs", 300),
    TWENTY_FOUR_HOURS("24 hrs", 1440);

    override fun toString(): String = label
}
