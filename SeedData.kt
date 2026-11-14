package com.example.samplee.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

/**
 * Starter parking spaces for Nasugbu, Batangas.
 *
 * IMPORTANT - these are SAMPLE records:
 *  - Barangay centres (Wawa, Bucana, Lumbangan, Pantalan, Poblacion 6) come from PhilAtlas.
 *  - Cogunan and Putat centres, and every individual lot position, are APPROXIMATE.
 *  - Slot counts are made up for the demo.
 * Open Google Maps / OpenStreetMap, right-click the real entrance of each lot,
 * copy the coordinates and edit the numbers below before real use.
 * (Edit, then uninstall the app or clear its storage so the database is re-seeded.)
 */
object SeedData {

    private class SpaceSeed(
        val name: String,
        val barangay: String,
        val address: String,
        val lat: Double,
        val lng: Double,
        val paid: Boolean,
        val slots: Int
    )

    private val seeds = listOf(
        // ---------- PAID ----------
        SpaceSeed("Town Plaza & Municipal Hall Parking", "Poblacion 1", "Nasugbu Town Plaza area", 14.0738, 120.6318, true, 20),
        SpaceSeed("Parish Church Visitors Parking", "Poblacion 2", "Near Immaculate Conception Parish", 14.0729, 120.6303, true, 15),
        SpaceSeed("Jollibee Nasugbu Parking", "Poblacion 4", "Poblacion commercial strip", 14.0700, 120.6345, true, 12),
        SpaceSeed("Nasugbu Public Market Parking", "Poblacion 5", "Public market area", 14.0712, 120.6336, true, 30),
        SpaceSeed("M. Rojales Street Parking", "Poblacion 7", "Supermarket parking lot", 14.0690, 120.6372, true, 40),
        SpaceSeed("Bus & Jeepney Terminal Parking", "Poblacion 9", "Poblacion transport terminal", 14.0752, 120.6322, true, 25),
        SpaceSeed("Wawa Beachfront Parking", "Wawa", "Wawa beach road", 14.0830, 120.6235, true, 35),
        SpaceSeed("Nasugbu Port Terminal Parking", "Pantalan", "Pantalan port area", 14.0902, 120.6318, true, 25),
        SpaceSeed("Bucana Riverside Parking", "Bucana", "Bucana main road", 14.0680, 120.6272, true, 18),
        SpaceSeed("Lumbangan Crossing Parking", "Lumbangan", "Lumbangan road crossing", 14.0498, 120.6592, true, 15),

        // ---------- FREE ----------
        SpaceSeed("Poblacion 3 Roadside Parking Bay", "Poblacion 3", "Roadside bay", 14.0722, 120.6311, false, 10),
        SpaceSeed("Poblacion 6 Barangay Hall Open Lot", "Poblacion 6", "Beside barangay hall", 14.0680, 120.6382, false, 15),
        SpaceSeed("Poblacion 8 Open Lot", "Poblacion 8", "Open lot", 14.0705, 120.6360, false, 12),
        SpaceSeed("Poblacion 10 Basketball Court Lot", "Poblacion 10", "Beside covered court", 14.0745, 120.6340, false, 20),
        SpaceSeed("Poblacion 11 Open Lot", "Poblacion 11", "Open lot", 14.0758, 120.6310, false, 10),
        SpaceSeed("Poblacion 12 Open Lot", "Poblacion 12", "Open lot", 14.0762, 120.6330, false, 12),
        SpaceSeed("Wawa Shoreline Open Lot", "Wawa", "Near the shoreline", 14.0815, 120.6252, false, 25),
        SpaceSeed("Pantalan Fish Port Open Area", "Pantalan", "Near fish port", 14.0914, 120.6330, false, 20),
        SpaceSeed("Cogunan Barangay Hall Lot", "Cogunan", "Beside barangay hall (approx. position)", 14.0660, 120.6405, false, 12),
        SpaceSeed("Putat Open Lot", "Putat", "Open lot (approx. position)", 14.0652, 120.6385, false, 10)
    )

    fun insertAll(db: SQLiteDatabase) {
        for (seed in seeds) {
            val spaceValues = ContentValues().apply {
                put("name", seed.name)
                put("barangay", seed.barangay)
                put("address", seed.address)
                put("latitude", seed.lat)
                put("longitude", seed.lng)
                put("is_paid", if (seed.paid) 1 else 0)
                put("total_slots", seed.slots)
            }
            val spaceId = db.insert("parking_spaces", null, spaceValues)

            // Paid spaces get individual, labelled slots (S-01, S-02 ...).
            if (seed.paid) {
                for (i in 1..seed.slots) {
                    val slotValues = ContentValues().apply {
                        put("space_id", spaceId)
                        put("slot_label", String.format("S-%02d", i))
                    }
                    db.insert("parking_slots", null, slotValues)
                }
            }
        }
    }
}
