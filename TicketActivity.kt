package com.example.samplee.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.example.samplee.databinding.ActivityTicketBinding
import com.example.samplee.db.DatabaseHelper
import com.example.samplee.model.Booking
import com.example.samplee.model.BookingStatus
import com.example.samplee.model.DurationTier
import com.example.samplee.model.PricingMatrix
import com.example.samplee.util.TimeUtils
import com.example.samplee.util.UiUtils

/**
 * Digital ticket pass: the whole trip from booking -> entrance -> exit.
 *
 * ADVANCE BOOKING: the parking timer is tied to the BOOKED start time, not to the
 * moment you book or even the moment you walk in. Book tonight for 10:00 AM and
 * the timer shows "starts in ..." until 10:00 AM, then counts your booked time down.
 */
class TicketActivity : AuthenticatedActivity() {

    private lateinit var binding: ActivityTicketBinding
    private var bookingId = -1L

    private val handler = Handler(Looper.getMainLooper())
    private var current: Booking? = null
    private var enterWasOpen = false

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!session.isLoggedIn) return

        binding = ActivityTicketBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bookingId = intent.getLongExtra(Extras.BOOKING_ID, -1L)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnCheckIn.setOnClickListener { doCheckIn() }
        binding.btnCheckOut.setOnClickListener { askCheckOut() }
        binding.btnCancel.setOnClickListener { askCancel() }
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            render()
            handler.removeCallbacks(ticker)
            handler.postDelayed(ticker, 1000L)
        }
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    /** Runs every second: updates the timer and notices when the entrance opens or the slot expires. */
    private fun tick() {
        val b = current ?: return
        val now = System.currentTimeMillis()
        if (b.status == BookingStatus.BOOKED) {
            val open = now >= b.startMillis - DatabaseHelper.EARLY_CHECKIN_MS
            val expired = now > b.startMillis + DatabaseHelper.NO_SHOW_GRACE_MS
            if (open != enterWasOpen || expired) {
                render()
                return
            }
        }
        updateTimerAndFees(b, now)
    }

    private fun render() {
        db.expireStaleBookings()
        val b = db.getBooking(bookingId)
        if (b == null || b.userId != userId) {
            toast("Ticket not found.")
            finish()
            return
        }
        current = b
        val now = System.currentTimeMillis()

        binding.tvTicketNo.text = b.ticketNo
        binding.tvStatus.text = b.status.label
        UiUtils.styleBadge(binding.tvStatus, b.status.colorHex)

        binding.tvSpace.text = "${b.spaceName}\nBrgy. ${b.barangay}"
        binding.tvSlot.text = b.slotLabel
        binding.tvPlate.text = b.plateNumber
        binding.tvVehicle.text = "${b.vehicleType.label}  •  ${b.vehicleColor}"
        binding.tvDuration.text = b.tier.label
        binding.tvScheduled.text =
            "From  ${TimeUtils.format(b.startMillis)}\nTo      ${TimeUtils.format(b.endMillis)}"

        binding.tvEntry.text = b.entryMillis?.let { TimeUtils.format(it) } ?: "Not entered yet"
        binding.tvExit.text = b.exitMillis?.let { TimeUtils.format(it) } ?: "Not exited yet"

        val opens = b.startMillis - DatabaseHelper.EARLY_CHECKIN_MS
        val open = now >= opens
        enterWasOpen = open
        binding.btnCheckIn.visibility = if (b.status == BookingStatus.BOOKED) View.VISIBLE else View.GONE
        binding.btnCheckIn.isEnabled = open
        binding.btnCheckIn.text =
            if (open) "Enter parking (entrance)" else "Entrance opens ${TimeUtils.format(opens)}"
        binding.btnCancel.visibility = if (b.status == BookingStatus.BOOKED) View.VISIBLE else View.GONE
        binding.btnCheckOut.visibility = if (b.status == BookingStatus.ACTIVE) View.VISIBLE else View.GONE

        val perBlock = PricingMatrix.formatPeso(PricingMatrix.priceOf(b.vehicleType, DurationTier.THIRTY_MIN))
        binding.tvHint.text = when (b.status) {
            BookingStatus.BOOKED ->
                "Your slot ${b.slotLabel} is reserved. The entrance opens 15 minutes before your start time " +
                        "(${TimeUtils.format(opens)}). Your parking time and fee are counted from ${TimeUtils.format(b.startMillis)}, " +
                        "even if you enter early. If you do not enter within 30 minutes after the start time, the slot is released."
            BookingStatus.ACTIVE ->
                "Press \"Exit parking\" when you leave. Staying past ${TimeUtils.formatTime(b.endMillis)} costs " +
                        "$perBlock for every started 30 minutes."
            BookingStatus.COMPLETED -> "Trip finished. Thank you for parking with ParkOnSpot!"
            BookingStatus.CANCELLED -> "This booking was cancelled. The slot was released."
            BookingStatus.EXPIRED -> "You did not enter in time, so the slot was released."
        }

        updateTimerAndFees(b, now)
    }

    /** The timer + the "time parked" and fee rows, all derived from the BOOKED times. */
    private fun updateTimerAndFees(b: Booking, now: Long) {
        // ----- fees -----
        val overstay = when (b.status) {
            BookingStatus.COMPLETED -> b.overstayFee
            BookingStatus.ACTIVE -> PricingMatrix.overstayFee(b.vehicleType, b.endMillis, now)
            else -> 0.0
        }
        binding.tvBaseFee.text = PricingMatrix.formatPeso(b.baseFee)
        binding.tvOverstay.text = PricingMatrix.formatPeso(overstay) +
                if (b.status == BookingStatus.ACTIVE && overstay > 0) "  (so far)" else ""
        binding.tvTotal.text = PricingMatrix.formatPeso(b.baseFee + overstay)

        // ----- time parked: counted from the later of (entry, booked start) -----
        binding.tvTimeParked.text = b.entryMillis?.let {
            TimeUtils.formatDuration(((b.exitMillis ?: now) - maxOf(it, b.startMillis)))
        } ?: "—"

        // ----- the big timer -----
        val title: String
        val value: String
        val sub: String
        when (b.status) {
            BookingStatus.BOOKED ->
                if (now < b.startMillis) {
                    title = "PARKING TIMER STARTS IN"
                    value = TimeUtils.formatCountdown(b.startMillis - now)
                    sub = "Starts ${TimeUtils.format(b.startMillis)} • ends ${TimeUtils.format(b.endMillis)}"
                } else {
                    title = "YOUR SLOT IS HELD FOR"
                    value = TimeUtils.formatCountdown(b.startMillis + DatabaseHelper.NO_SHOW_GRACE_MS - now)
                    sub = "Your booked time has started. Enter now or the slot is released."
                }
            BookingStatus.ACTIVE ->
                when {
                    now < b.startMillis -> {
                        title = "YOU ARE IN EARLY • TIMER STARTS IN"
                        value = TimeUtils.formatCountdown(b.startMillis - now)
                        sub = "Time and fee are counted from ${TimeUtils.format(b.startMillis)}."
                    }
                    now <= b.endMillis -> {
                        title = "PARKING TIME LEFT"
                        value = TimeUtils.formatCountdown(b.endMillis - now)
                        sub = "Timer started ${TimeUtils.format(b.startMillis)} • ends ${TimeUtils.format(b.endMillis)}"
                    }
                    else -> {
                        title = "OVERTIME"
                        value = "+" + TimeUtils.formatCountdown(now - b.endMillis)
                        sub = "Booked time ended ${TimeUtils.format(b.endMillis)}. Overstay fee is running."
                    }
                }
            BookingStatus.COMPLETED -> {
                title = "PARKED FOR"
                val from = maxOf(b.entryMillis ?: b.startMillis, b.startMillis)
                value = TimeUtils.formatDuration((b.exitMillis ?: now) - from)
                sub = "Exited ${b.exitMillis?.let { TimeUtils.format(it) } ?: ""}"
            }
            BookingStatus.CANCELLED, BookingStatus.EXPIRED -> {
                title = "PARKING TIMER"
                value = "—"
                sub = "This ticket is no longer valid."
            }
        }
        binding.tvTimerTitle.text = title
        binding.tvTimer.text = value
        binding.tvTimerSub.text = sub
    }

    private fun doCheckIn() {
        val error = db.checkIn(bookingId)
        if (error != null) toast(error) else toast("Entrance recorded. Enjoy your stay!")
        render()
    }

    private fun askCheckOut() {
        val b = db.getBooking(bookingId) ?: return
        val total = b.baseFee + PricingMatrix.overstayFee(b.vehicleType, b.endMillis, System.currentTimeMillis())
        AlertDialog.Builder(this)
            .setTitle("Exit parking?")
            .setMessage("Total fee for this trip: ${PricingMatrix.formatPeso(total)}\n\nPay at the exit counter.")
            .setPositiveButton("Exit") { _, _ -> doCheckOut() }
            .setNegativeButton("Stay", null)
            .show()
    }

    private fun doCheckOut() {
        val error = db.checkOut(bookingId)
        if (error != null) {
            toast(error)
        } else {
            val b = db.getBooking(bookingId)
            toast("Exit recorded. Total paid: ${PricingMatrix.formatPeso(b?.totalFee ?: 0.0)}")
        }
        render()
    }

    private fun askCancel() {
        AlertDialog.Builder(this)
            .setTitle("Cancel booking?")
            .setMessage("The slot will be released and this ticket will become void.")
            .setPositiveButton("Cancel booking") { _, _ ->
                val error = db.cancelBooking(bookingId)
                if (error != null) toast(error) else toast("Booking cancelled.")
                render()
            }
            .setNegativeButton("Keep it", null)
            .show()
    }
}
