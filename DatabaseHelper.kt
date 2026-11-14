package com.example.samplee.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.samplee.model.Booking
import com.example.samplee.model.BookingResult
import com.example.samplee.model.BookingStatus
import com.example.samplee.model.DurationTier
import com.example.samplee.model.FreeParkingSpace
import com.example.samplee.model.FreeSession
import com.example.samplee.model.PaidParkingSpace
import com.example.samplee.model.ParkingLog
import com.example.samplee.model.ParkingSpace
import com.example.samplee.model.PricingMatrix
import com.example.samplee.model.User
import com.example.samplee.model.Vehicle
import com.example.samplee.model.VehicleType
import com.example.samplee.util.PasswordHasher
import com.example.samplee.util.TimeUtils
import com.example.samplee.model.SlotInfo
import java.util.Locale

/**
 * The single gateway to SQLite. Tables:
 *  users, parking_spaces, parking_slots, bookings, ticket_passes,
 *  parking_logs, free_parking_sessions
 *
 * Every time value is stored as epoch milliseconds (INTEGER).
 * NOTE: numbers are written straight into the SQL text (they are Longs, so
 * this is injection-safe) because SQLite compares bound text parameters
 * differently from numbers when the other side is an expression.
 */
class DatabaseHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    // ------------------------------------------------------------------ setup

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                full_name TEXT NOT NULL,
                email TEXT NOT NULL UNIQUE,
                phone TEXT NOT NULL,
                password_hash TEXT NOT NULL,
                salt TEXT NOT NULL,
                created_at INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE parking_spaces (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                barangay TEXT NOT NULL,
                address TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                is_paid INTEGER NOT NULL,
                total_slots INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE parking_slots (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                space_id INTEGER NOT NULL REFERENCES parking_spaces(id) ON DELETE CASCADE,
                slot_label TEXT NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE bookings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL REFERENCES users(id),
                space_id INTEGER NOT NULL REFERENCES parking_spaces(id),
                slot_id INTEGER NOT NULL REFERENCES parking_slots(id),
                plate_number TEXT NOT NULL,
                vehicle_type TEXT NOT NULL,
                vehicle_color TEXT NOT NULL,
                duration_tier TEXT NOT NULL,
                booking_start INTEGER NOT NULL,
                booking_end INTEGER NOT NULL,
                base_fee REAL NOT NULL,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE ticket_passes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                booking_id INTEGER NOT NULL UNIQUE REFERENCES bookings(id) ON DELETE CASCADE,
                ticket_no TEXT NOT NULL UNIQUE,
                issued_at INTEGER NOT NULL,
                entry_time INTEGER,
                exit_time INTEGER,
                overstay_fee REAL NOT NULL DEFAULT 0,
                total_fee REAL NOT NULL,
                status TEXT NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE parking_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL REFERENCES users(id),
                space_id INTEGER NOT NULL REFERENCES parking_spaces(id),
                booking_id INTEGER,
                plate_number TEXT NOT NULL,
                vehicle_type TEXT NOT NULL,
                vehicle_color TEXT NOT NULL,
                'action' TEXT NOT NULL,
                log_time INTEGER NOT NULL,
                note TEXT)"""
        )
        db.execSQL(
            """CREATE TABLE free_parking_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL REFERENCES users(id),
                space_id INTEGER NOT NULL REFERENCES parking_spaces(id),
                plate_number TEXT NOT NULL,
                vehicle_type TEXT NOT NULL,
                vehicle_color TEXT NOT NULL,
                time_in INTEGER NOT NULL,
                time_out INTEGER)"""
        )
        db.execSQL("CREATE INDEX idx_bookings_slot ON bookings(slot_id, status)")
        db.execSQL("CREATE INDEX idx_bookings_user ON bookings(user_id)")
        db.execSQL("CREATE INDEX idx_slots_space ON parking_slots(space_id)")
        db.execSQL("CREATE INDEX idx_free_space ON free_parking_sessions(space_id, time_out)")

        SeedData.insertAll(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 only. When you change the schema, bump DB_VERSION and migrate here.
        db.execSQL("DROP TABLE IF EXISTS free_parking_sessions")
        db.execSQL("DROP TABLE IF EXISTS parking_logs")
        db.execSQL("DROP TABLE IF EXISTS ticket_passes")
        db.execSQL("DROP TABLE IF EXISTS bookings")
        db.execSQL("DROP TABLE IF EXISTS parking_slots")
        db.execSQL("DROP TABLE IF EXISTS parking_spaces")
        db.execSQL("DROP TABLE IF EXISTS users")
        onCreate(db)
    }

    // ------------------------------------------------------------------ users

    fun emailExists(email: String): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM users WHERE email = ?", arrayOf(email.trim().lowercase())
        ).use { it.moveToFirst() }

    /** Returns false when the e-mail is already registered. */
    fun registerUser(fullName: String, email: String, phone: String, password: String): Boolean {
        if (emailExists(email)) return false
        val salt = PasswordHasher.newSalt()
        val values = ContentValues().apply {
            put("full_name", fullName.trim())
            put("email", email.trim().lowercase())
            put("phone", phone.trim())
            put("password_hash", PasswordHasher.hash(password, salt))
            put("salt", salt)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insert("users", null, values) != -1L
    }

    /** Returns the user when e-mail + password are correct, otherwise null. */
    fun loginUser(email: String, password: String): User? =
        readableDatabase.rawQuery(
            "SELECT id, full_name, email, phone, password_hash, salt FROM users WHERE email = ?",
            arrayOf(email.trim().lowercase())
        ).use { c ->
            if (c.moveToFirst() && PasswordHasher.matches(password, c.getString(5), c.getString(4))) {
                User(c.getLong(0), c.getString(1), c.getString(2), c.getString(3))
            } else {
                null
            }
        }

    // ----------------------------------------------------------------- spaces

    fun getAllSpaces(now: Long = System.currentTimeMillis()): List<ParkingSpace> {
        expireStaleBookings(now)
        val list = mutableListOf<ParkingSpace>()
        readableDatabase.rawQuery(SPACE_SELECT + " ORDER BY id", null).use { c ->
            while (c.moveToNext()) list.add(mapSpace(c, now))
        }
        return list
    }

    fun getSpace(spaceId: Long, now: Long = System.currentTimeMillis()): ParkingSpace? {
        expireStaleBookings(now)
        return readableDatabase.rawQuery("$SPACE_SELECT WHERE id = $spaceId", null).use { c ->
            if (c.moveToFirst()) mapSpace(c, now) else null
        }
    }

    private fun mapSpace(c: Cursor, now: Long): ParkingSpace {
        val id = c.getLong(0)
        val space: ParkingSpace = if (c.getInt(6) == 1) {
            PaidParkingSpace(id, c.getString(1), c.getString(2), c.getString(3), c.getDouble(4), c.getDouble(5), c.getInt(7))
        } else {
            FreeParkingSpace(id, c.getString(1), c.getString(2), c.getString(3), c.getDouble(4), c.getDouble(5), c.getInt(7))
        }
        space.updateOccupancy(if (space.isPaid) occupiedPaidSlots(id, now) else activeFreeCount(id))
        return space
    }

    /** Slots in use RIGHT NOW: vehicles inside + reservations whose time has started. */
    private fun occupiedPaidSlots(spaceId: Long, now: Long): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(DISTINCT slot_id) FROM bookings WHERE space_id = $spaceId AND " +
                    "(status = 'ACTIVE' OR (status = 'BOOKED' AND booking_start <= $now AND booking_end > $now))",
            null
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun activeFreeCount(spaceId: Long): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM free_parking_sessions WHERE space_id = $spaceId AND time_out IS NULL",
            null
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /** How many slots are still free for the whole window [start, end). */
    fun countFreeSlots(spaceId: Long, startMillis: Long, endMillis: Long, now: Long = System.currentTimeMillis()): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM parking_slots s WHERE s.space_id = $spaceId AND " + freeSlotCondition(startMillis, endMillis, now),
            null
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /** Every slot of a space, each flagged free / taken for the window [start, end). Drives the lot layout. */
    fun getSlotAvailability(
        spaceId: Long,
        startMillis: Long,
        endMillis: Long,
        now: Long = System.currentTimeMillis()
    ): List<SlotInfo> {
        expireStaleBookings(now)
        val list = mutableListOf<SlotInfo>()
        readableDatabase.rawQuery(
            "SELECT s.id, s.slot_label, " +
                    "CASE WHEN " + freeSlotCondition(startMillis, endMillis, now) + " THEN 1 ELSE 0 END " +
                    "FROM parking_slots s WHERE s.space_id = $spaceId ORDER BY s.id",
            null
        ).use { c ->
            while (c.moveToNext()) list.add(SlotInfo(c.getLong(0), c.getString(1), (c.getInt(2) == 1)))
        }
        return list
    }

    /**
     * First free slot for the window, or - when [onlySlotId] is given - that exact slot
     * if (and only if) it belongs to the space and is free.
     */
    private fun findFreeSlot(
        db: SQLiteDatabase, spaceId: Long, startMillis: Long, endMillis: Long, now: Long,
        onlySlotId: Long? = null
    ): Pair<Long, String>? {
        val onlyClause = if (onlySlotId != null) " AND s.id = $onlySlotId" else ""
        return db.rawQuery(
            "SELECT s.id, s.slot_label FROM parking_slots s WHERE s.space_id = $spaceId" + onlyClause +
                    " AND " + freeSlotCondition(startMillis, endMillis, now) + " ORDER BY s.id LIMIT 1",
            null
        ).use { if (it.moveToFirst()) Pair(it.getLong(0), it.getString(1)) else null }
    }

    /**
     * A slot is free when no live booking overlaps the requested window.
     * A vehicle that is still inside past its booked end (overstay) keeps its
     * slot blocked for another 30 minutes from now.
     */
    private fun freeSlotCondition(startMillis: Long, endMillis: Long, now: Long): String =
        "NOT EXISTS (SELECT 1 FROM bookings b WHERE b.slot_id = s.id AND b.status IN ('BOOKED','ACTIVE') " +
                "AND b.booking_start < $endMillis AND " +
                "(CASE WHEN b.status = 'ACTIVE' THEN MAX(b.booking_end, ${now + OVERSTAY_BUFFER_MS}) " +
                "ELSE b.booking_end END) > $startMillis)"

    // --------------------------------------------------------------- bookings

    /**
     * Strict reservation rule: no start date/time = no booking.
     * Picks the first free slot, issues the ticket pass and writes the log in ONE transaction.
     */
    fun createBooking(
        userId: Long,
        spaceId: Long,
        vehicle: Vehicle,
        tier: DurationTier,
        startMillis: Long?,
        preferredSlotId: Long? = null
    ): BookingResult {
        if (startMillis == null) {
            return BookingResult.Failure("Booking date and time are required. No booking, no reservation.")
        }
        val now = System.currentTimeMillis()
        if (startMillis < now - 60_000L) return BookingResult.Failure("Booking time cannot be in the past.")
        if (startMillis > now + MAX_ADVANCE_MS) return BookingResult.Failure("You can only book up to 30 days ahead.")

        expireStaleBookings(now)
        val endMillis = startMillis + tier.minutes * 60_000L
        val fee = PricingMatrix.priceOf(vehicle.type, tier)
        val db = writableDatabase

        db.beginTransaction()
        try {
            val isPaid: Boolean? = db.rawQuery(
                "SELECT is_paid FROM parking_spaces WHERE id = $spaceId", null
            ).use { if (it.moveToFirst()) it.getInt(0) == 1 else null }
            if (isPaid == null) return BookingResult.Failure("Parking space not found.")
            if (!isPaid) return BookingResult.Failure("This is a free parking space. No booking is needed.")

            val plateClash = db.rawQuery(
                "SELECT COUNT(*) FROM bookings WHERE plate_number = ? AND status IN ('BOOKED','ACTIVE') " +
                        "AND booking_start < $endMillis AND booking_end > $startMillis",
                arrayOf(vehicle.plateNumber)
            ).use { it.moveToFirst() && it.getInt(0) > 0 }
            if (plateClash) {
                return BookingResult.Failure("Plate ${vehicle.plateNumber} already has a booking that overlaps this time.")
            }

            // The user may have tapped a specific slot on the lot layout; otherwise take the first free one.
            val slot = if (preferredSlotId != null) {
                findFreeSlot(db, spaceId, startMillis, endMillis, now, preferredSlotId)
                    ?: return BookingResult.Failure("That slot is no longer free for your chosen time. Please pick another slot.")
            } else {
                findFreeSlot(db, spaceId, startMillis, endMillis, now)
                    ?: return BookingResult.Failure("No slot is available for that date, time and duration. Try another time.")
            }

            val bookingValues = ContentValues().apply {
                put("user_id", userId)
                put("space_id", spaceId)
                put("slot_id", slot.first)
                put("plate_number", vehicle.plateNumber)
                put("vehicle_type", vehicle.type.name)
                put("vehicle_color", vehicle.color)
                put("duration_tier", tier.name)
                put("booking_start", startMillis)
                put("booking_end", endMillis)
                put("base_fee", fee)
                put("status", BookingStatus.BOOKED.name)
                put("created_at", now)
            }
            val bookingId = db.insertOrThrow("bookings", null, bookingValues)

            val ticketNo = "POS-" + TimeUtils.ymd(now) + "-" + String.format(Locale.US, "%05d", bookingId)
            val ticketValues = ContentValues().apply {
                put("booking_id", bookingId)
                put("ticket_no", ticketNo)
                put("issued_at", now)
                put("overstay_fee", 0.0)
                put("total_fee", fee)
                put("status", "ISSUED")
            }
            db.insertOrThrow("ticket_passes", null, ticketValues)

            insertLog(
                db, userId, spaceId, bookingId, vehicle.plateNumber, vehicle.type, vehicle.color,
                "BOOKING_CREATED", "Slot ${slot.second}, ${tier.label}, starts ${TimeUtils.format(startMillis)}", now
            )
            db.setTransactionSuccessful()
            return BookingResult.Success(bookingId)
        } catch (e: Exception) {
            return BookingResult.Failure("Could not save the booking: ${e.message}")
        } finally {
            db.endTransaction()
        }
    }

    fun getBooking(bookingId: Long): Booking? =
        readableDatabase.rawQuery("$BOOKING_SELECT WHERE b.id = $bookingId", null).use { c ->
            if (c.moveToFirst()) mapBooking(c) else null
        }

    fun getBookingsForUser(userId: Long): List<Booking> {
        expireStaleBookings()
        val list = mutableListOf<Booking>()
        readableDatabase.rawQuery(
            "$BOOKING_SELECT WHERE b.user_id = $userId ORDER BY b.booking_start DESC", null
        ).use { c -> while (c.moveToNext()) list.add(mapBooking(c)) }
        return list
    }

    /** Entrance: allowed from 15 minutes before the booked start. Returns an error message or null. */
    fun checkIn(bookingId: Long, now: Long = System.currentTimeMillis()): String? {
        expireStaleBookings(now)
        val b = getBooking(bookingId) ?: return "Booking not found."
        if (b.status != BookingStatus.BOOKED) {
            return "This ticket is ${b.status.label.lowercase()} and cannot be used to enter."
        }
        val opens = b.startMillis - EARLY_CHECKIN_MS
        if (now < opens) return "Too early. The entrance opens at ${TimeUtils.format(opens)}."

        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE bookings SET status = 'ACTIVE' WHERE id = $bookingId")
            db.execSQL("UPDATE ticket_passes SET entry_time = $now, status = 'ENTERED' WHERE booking_id = $bookingId")
            insertLog(db, b.userId, b.spaceId, b.id, b.plateNumber, b.vehicleType, b.vehicleColor, "ENTRY", "Slot ${b.slotLabel}", now)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return null
    }

    /** Exit: closes the ticket and prices any overstay. Returns an error message or null. */
    fun checkOut(bookingId: Long, now: Long = System.currentTimeMillis()): String? {
        val b = getBooking(bookingId) ?: return "Booking not found."
        if (b.status != BookingStatus.ACTIVE) return "This vehicle has not entered yet, so it cannot exit."

        val overstay = PricingMatrix.overstayFee(b.vehicleType, b.endMillis, now)
        val total = b.baseFee + overstay
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE bookings SET status = 'COMPLETED' WHERE id = $bookingId")
            db.execSQL(
                "UPDATE ticket_passes SET exit_time = $now, overstay_fee = $overstay, total_fee = $total, " +
                        "status = 'EXITED' WHERE booking_id = $bookingId"
            )
            val note = "Total ${PricingMatrix.formatPeso(total)}" +
                    if (overstay > 0) " (includes overstay ${PricingMatrix.formatPeso(overstay)})" else ""
            insertLog(db, b.userId, b.spaceId, b.id, b.plateNumber, b.vehicleType, b.vehicleColor, "EXIT", note, now)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return null
    }

    fun cancelBooking(bookingId: Long, now: Long = System.currentTimeMillis()): String? {
        val b = getBooking(bookingId) ?: return "Booking not found."
        if (b.status != BookingStatus.BOOKED) return "Only a booking that has not been used yet can be cancelled."
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE bookings SET status = 'CANCELLED' WHERE id = $bookingId")
            db.execSQL("UPDATE ticket_passes SET status = 'VOID' WHERE booking_id = $bookingId")
            insertLog(db, b.userId, b.spaceId, b.id, b.plateNumber, b.vehicleType, b.vehicleColor, "BOOKING_CANCELLED", null, now)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return null
    }

    /** Un-used bookings are released 30 minutes after their start time (no-show). */
    fun expireStaleBookings(now: Long = System.currentTimeMillis()) {
        val db = writableDatabase
        val staleIds = mutableListOf<Long>()
        db.rawQuery(
            "SELECT id FROM bookings WHERE status = 'BOOKED' AND booking_start + $NO_SHOW_GRACE_MS < $now", null
        ).use { c -> while (c.moveToNext()) staleIds.add(c.getLong(0)) }
        if (staleIds.isEmpty()) return

        db.beginTransaction()
        try {
            for (id in staleIds) {
                val b = getBooking(id) ?: continue
                db.execSQL("UPDATE bookings SET status = 'EXPIRED' WHERE id = $id")
                db.execSQL("UPDATE ticket_passes SET status = 'VOID' WHERE booking_id = $id")
                insertLog(db, b.userId, b.spaceId, b.id, b.plateNumber, b.vehicleType, b.vehicleColor, "BOOKING_EXPIRED", "No-show", now)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ------------------------------------------------------------ free parking

    /** Returns an error message or null on success. */
    fun startFreeParking(userId: Long, spaceId: Long, vehicle: Vehicle): String? {
        val now = System.currentTimeMillis()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val info = db.rawQuery(
                "SELECT is_paid, total_slots FROM parking_spaces WHERE id = $spaceId", null
            ).use { if (it.moveToFirst()) Pair(it.getInt(0), it.getInt(1)) else null }
                ?: return "Parking space not found."
            if (info.first == 1) return "This is a paid space. Please book a slot instead."

            val inside = db.rawQuery(
                "SELECT COUNT(*) FROM free_parking_sessions WHERE space_id = $spaceId AND time_out IS NULL", null
            ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
            if (inside >= info.second) return "This free lot is full right now."

            val alreadyParked = db.rawQuery(
                "SELECT COUNT(*) FROM free_parking_sessions WHERE plate_number = ? AND time_out IS NULL",
                arrayOf(vehicle.plateNumber)
            ).use { it.moveToFirst() && it.getInt(0) > 0 }
            if (alreadyParked) return "Vehicle ${vehicle.plateNumber} is already parked in a free lot."

            val values = ContentValues().apply {
                put("user_id", userId)
                put("space_id", spaceId)
                put("plate_number", vehicle.plateNumber)
                put("vehicle_type", vehicle.type.name)
                put("vehicle_color", vehicle.color)
                put("time_in", now)
            }
            db.insertOrThrow("free_parking_sessions", null, values)
            insertLog(db, userId, spaceId, null, vehicle.plateNumber, vehicle.type, vehicle.color, "FREE_PARK_IN", null, now)
            db.setTransactionSuccessful()
            return null
        } catch (e: Exception) {
            return "Could not save: ${e.message}"
        } finally {
            db.endTransaction()
        }
    }

    fun endFreeParking(sessionId: Long): String? {
        val now = System.currentTimeMillis()
        val s = getFreeSession(sessionId) ?: return "Parking record not found."
        if (s.timeOut != null) return "This vehicle already left."
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE free_parking_sessions SET time_out = $now WHERE id = $sessionId")
            insertLog(
                db, userIdOfSession(db, sessionId), s.spaceId, null, s.plateNumber, s.vehicleType, s.vehicleColor,
                "FREE_PARK_OUT", "Stayed ${TimeUtils.formatDuration(now - s.timeIn)}", now
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return null
    }

    fun getActiveFreeSessions(userId: Long): List<FreeSession> {
        val list = mutableListOf<FreeSession>()
        readableDatabase.rawQuery(
            "$FREE_SELECT WHERE f.user_id = $userId AND f.time_out IS NULL ORDER BY f.time_in DESC", null
        ).use { c -> while (c.moveToNext()) list.add(mapFree(c)) }
        return list
    }

    private fun getFreeSession(sessionId: Long): FreeSession? =
        readableDatabase.rawQuery("$FREE_SELECT WHERE f.id = $sessionId", null).use { c ->
            if (c.moveToFirst()) mapFree(c) else null
        }

    private fun userIdOfSession(db: SQLiteDatabase, sessionId: Long): Long =
        db.rawQuery("SELECT user_id FROM free_parking_sessions WHERE id = $sessionId", null)
            .use { if (it.moveToFirst()) it.getLong(0) else -1L }

    // ------------------------------------------------------------------- logs

    fun getLogsForUser(userId: Long, limit: Int = 30): List<ParkingLog> {
        val list = mutableListOf<ParkingLog>()
        readableDatabase.rawQuery(
            "SELECT l.id, s.name, l.plate_number, l.action, l.log_time, l.note FROM parking_logs l " +
                    "JOIN parking_spaces s ON s.id = l.space_id WHERE l.user_id = $userId " +
                    "ORDER BY l.log_time DESC, l.id DESC LIMIT $limit",
            null
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    ParkingLog(
                        c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4),
                        if (c.isNull(5)) null else c.getString(5)
                    )
                )
            }
        }
        return list
    }

    private fun insertLog(
        db: SQLiteDatabase, userId: Long, spaceId: Long, bookingId: Long?,
        plate: String, type: VehicleType, color: String, action: String, note: String?, time: Long
    ) {
        val values = ContentValues().apply {
            put("user_id", userId)
            put("space_id", spaceId)
            if (bookingId != null) put("booking_id", bookingId) else putNull("booking_id")
            put("plate_number", plate)
            put("vehicle_type", type.name)
            put("vehicle_color", color)
            put("action", action)
            put("log_time", time)
            if (note != null) put("note", note) else putNull("note")
        }
        db.insert("parking_logs", null, values)
    }

    // ----------------------------------------------------------------- mapping

    private fun mapBooking(c: Cursor) = Booking(
        id = c.getLong(0),
        ticketNo = c.getString(1),
        userId = c.getLong(2),
        spaceId = c.getLong(3),
        spaceName = c.getString(4),
        barangay = c.getString(5),
        slotLabel = c.getString(6),
        plateNumber = c.getString(7),
        vehicleType = VehicleType.valueOf(c.getString(8)),
        vehicleColor = c.getString(9),
        tier = DurationTier.valueOf(c.getString(10)),
        startMillis = c.getLong(11),
        endMillis = c.getLong(12),
        baseFee = c.getDouble(13),
        status = BookingStatus.valueOf(c.getString(14)),
        createdAt = c.getLong(15),
        entryMillis = if (c.isNull(16)) null else c.getLong(16),
        exitMillis = if (c.isNull(17)) null else c.getLong(17),
        overstayFee = c.getDouble(18)
    )

    private fun mapFree(c: Cursor) = FreeSession(
        id = c.getLong(0),
        spaceId = c.getLong(1),
        spaceName = c.getString(2),
        barangay = c.getString(3),
        plateNumber = c.getString(4),
        vehicleType = VehicleType.valueOf(c.getString(5)),
        vehicleColor = c.getString(6),
        timeIn = c.getLong(7),
        timeOut = if (c.isNull(8)) null else c.getLong(8)
    )

    companion object {
        private const val DB_NAME = "parkonspot.db"
        private const val DB_VERSION = 1

        const val EARLY_CHECKIN_MS = 15 * 60 * 1000L
        const val NO_SHOW_GRACE_MS = 30 * 60 * 1000L
        private const val OVERSTAY_BUFFER_MS = 30 * 60 * 1000L
        private const val MAX_ADVANCE_MS = 30L * 24 * 60 * 60 * 1000

        private const val SPACE_SELECT =
            "SELECT id, name, barangay, address, latitude, longitude, is_paid, total_slots FROM parking_spaces"

        private const val BOOKING_SELECT =
            "SELECT b.id, t.ticket_no, b.user_id, b.space_id, s.name, s.barangay, sl.slot_label, " +
                    "b.plate_number, b.vehicle_type, b.vehicle_color, b.duration_tier, b.booking_start, " +
                    "b.booking_end, b.base_fee, b.status, b.created_at, t.entry_time, t.exit_time, t.overstay_fee " +
                    "FROM bookings b " +
                    "JOIN ticket_passes t ON t.booking_id = b.id " +
                    "JOIN parking_spaces s ON s.id = b.space_id " +
                    "JOIN parking_slots sl ON sl.id = b.slot_id"

        private const val FREE_SELECT =
            "SELECT f.id, f.space_id, s.name, s.barangay, f.plate_number, f.vehicle_type, " +
                    "f.vehicle_color, f.time_in, f.time_out " +
                    "FROM free_parking_sessions f JOIN parking_spaces s ON s.id = f.space_id"

        @Volatile
        private var instance: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper =
            instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context.applicationContext).also { instance = it }
            }
    }
}
