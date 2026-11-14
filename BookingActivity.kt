package com.example.samplee.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import com.example.samplee.databinding.ActivityBookingBinding
import com.example.samplee.model.BookingResult
import com.example.samplee.model.DurationTier
import com.example.samplee.model.ParkingSpace
import com.example.samplee.model.PricingMatrix
import com.example.samplee.model.SlotInfo
import com.example.samplee.model.Vehicle
import com.example.samplee.model.VehicleType
import com.example.samplee.util.TimeUtils
import com.example.samplee.util.UiUtils
import java.util.Calendar

/**
 * Booking screen for PAID spaces.
 * Required: plate, vehicle type, colour, duration AND booking date + time.
 *
 * You can reserve IN ADVANCE (up to 30 days). The reservation only holds a slot;
 * your parking time and fee start counting at the booked date/time.
 */
class BookingActivity : AuthenticatedActivity() {

    private lateinit var binding: ActivityBookingBinding
    private lateinit var space: ParkingSpace

    private var pickedYear = -1
    private var pickedMonth = -1
    private var pickedDay = -1
    private var pickedHour = -1
    private var pickedMinute = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!session.isLoggedIn) return

        binding = ActivityBookingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val found = db.getSpace(intent.getLongExtra(Extras.SPACE_ID, -1L))
        if (found == null || !found.requiresBooking()) {
            toast("Paid parking space not found.")
            finish()
            return
        }
        space = found

        binding.tvSpaceName.text = space.name
        binding.tvRates.text = PricingMatrix.ratesTable()
        showSpaceInfo()

        binding.spinnerVehicleType.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, VehicleType.values())
        binding.spinnerDuration.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, DurationTier.values())
        binding.spinnerVehicleType.onItemSelectedListener = UiUtils.onSelected { updateSummary() }
        binding.spinnerDuration.onItemSelectedListener = UiUtils.onSelected { updateSummary() }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPickDate.setOnClickListener { pickDate() }
        binding.btnPickTime.setOnClickListener { pickTime() }
        binding.btnConfirm.setOnClickListener { confirmBooking() }

        binding.parkingLot.listener = object : ParkingLotView.Listener {
            override fun onSlotSelected(slot: SlotInfo?) = updateSelectedSlotText()

            override fun onTakenSlotTapped(slot: SlotInfo) {
                toast("Slot ${slot.slotLabel} is taken for your chosen time. Please tap a green slot.")
            }
        }

        updateSummary()
    }

    private fun showSpaceInfo() {
        binding.tvSpaceInfo.text =
            "Brgy. ${space.barangay}  •  ${space.address}\nRight now: ${space.availabilityLabel()}"
    }

    private fun pickDate() {
        val now = Calendar.getInstance()
        val dialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                pickedYear = year
                pickedMonth = month
                pickedDay = day
                updateSummary()
                if (pickedHour < 0) pickTime() // convenience: ask for the time right after the date
            },
            if (pickedYear >= 0) pickedYear else now.get(Calendar.YEAR),
            if (pickedMonth >= 0) pickedMonth else now.get(Calendar.MONTH),
            if (pickedDay >= 0) pickedDay else now.get(Calendar.DAY_OF_MONTH)
        )
        // Start of today up to 30 days ahead, so tomorrow / next week are all selectable.
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        dialog.datePicker.minDate = today
        dialog.datePicker.maxDate = today + 30L * 24 * 60 * 60 * 1000
        dialog.show()
    }

    private fun pickTime() {
        val now = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hour, minute ->
                pickedHour = hour
                pickedMinute = minute
                updateSummary()
            },
            if (pickedHour >= 0) pickedHour else now.get(Calendar.HOUR_OF_DAY),
            if (pickedMinute >= 0) pickedMinute else now.get(Calendar.MINUTE),
            false
        ).show()
    }

    /** Null until BOTH date and time were chosen (no date/time = no reservation). */
    private fun startMillisOrNull(): Long? {
        if (pickedYear < 0 || pickedHour < 0) return null
        return Calendar.getInstance().apply {
            clear()
            set(pickedYear, pickedMonth, pickedDay, pickedHour, pickedMinute, 0)
        }.timeInMillis
    }

    private fun updateSummary() {
        val type = binding.spinnerVehicleType.selectedItem as VehicleType
        val tier = binding.spinnerDuration.selectedItem as DurationTier
        binding.tvFee.text = PricingMatrix.formatPeso(space.feeFor(type, tier))

        // Buttons show what was chosen
        binding.btnPickDate.text = if (pickedYear >= 0) {
            TimeUtils.formatDate(Calendar.getInstance().apply { clear(); set(pickedYear, pickedMonth, pickedDay) }.timeInMillis)
        } else {
            "Pick date"
        }
        binding.btnPickTime.text = if (pickedHour >= 0) {
            TimeUtils.formatTime(Calendar.getInstance().apply { clear(); set(2000, 0, 1, pickedHour, pickedMinute, 0) }.timeInMillis)
        } else {
            "Pick time"
        }

        val muted = Color.parseColor("#5F6B7A")
        val start = startMillisOrNull()
        if (start == null) {
            binding.tvStart.text = "—"
            binding.tvEnd.text = "—"
            binding.tvSlotsForTime.text = "—"
            binding.tvSlotsForTime.setTextColor(muted)
            binding.tvStartsIn.text = "Pick BOTH a date and a time. No date and time = no reservation."
            binding.tvStartsIn.setTextColor(muted)
            hideLot("Pick a date and time first. Then you can see the parking layout and choose your slot.")
            return
        }

        val end = start + tier.minutes * 60_000L
        binding.tvStart.text = TimeUtils.format(start)
        binding.tvEnd.text = TimeUtils.format(end)

        val now = System.currentTimeMillis()
        if (start < now - 60_000L) {
            binding.tvSlotsForTime.text = "—"
            binding.tvSlotsForTime.setTextColor(muted)
            binding.tvStartsIn.text = "That time has already passed. Pick a later time."
            binding.tvStartsIn.setTextColor(Color.parseColor("#C62828"))
            hideLot("Pick a later time to see the parking layout.")
            return
        }

        val free = db.countFreeSlots(space.id, start, end)
        showLot(start, end)
        binding.tvSlotsForTime.text = "$free of ${space.totalSlots}"
        binding.tvSlotsForTime.setTextColor(Color.parseColor(if (free > 0) "#2E7D32" else "#C62828"))

        val untilStart = start - now
        binding.tvStartsIn.text = if (untilStart > 5 * 60_000L) {
            "Advance reservation: starts in ${TimeUtils.formatDuration(untilStart)}. " +
                    "Your parking time and fee only start counting at the booked time."
        } else {
            "Starts right away."
        }
        binding.tvStartsIn.setTextColor(muted)
    }

    // ----------------------------------------------------- parking lot layout

    private fun showLot(start: Long, end: Long) {
        binding.parkingLot.setSlots(db.getSlotAvailability(space.id, start, end))
        binding.lotSection.visibility = View.VISIBLE
        binding.tvLotHint.text =
            "Tap a green slot to choose it (tap again to un-choose). Red slots are taken for your chosen time. " +
                    "The entrance is at the bottom."
        updateSelectedSlotText()
    }

    private fun hideLot(hint: String) {
        binding.parkingLot.clearSelection()
        binding.lotSection.visibility = View.GONE
        binding.tvLotHint.text = hint
    }

    private fun updateSelectedSlotText() {
        val label = binding.parkingLot.selectedLabel
        binding.tvSelectedSlot.text = if (label == null) {
            "No slot chosen. We will assign the first free slot for you."
        } else {
            "Your slot: $label"
        }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun confirmBooking() {
        binding.tvError.visibility = View.GONE
        val type = binding.spinnerVehicleType.selectedItem as VehicleType
        val tier = binding.spinnerDuration.selectedItem as DurationTier

        val vehicle = try {
            Vehicle(binding.etPlate.text.toString(), type, binding.etColor.text.toString())
        } catch (e: IllegalArgumentException) {
            showError(e.message ?: "Invalid vehicle details.")
            return
        }

        when (val result = db.createBooking(userId, space.id, vehicle, tier, startMillisOrNull(), binding.parkingLot.selectedSlotId)) {
            is BookingResult.Success -> {
                toast("Booking confirmed! Your ticket pass is ready.")
                startActivity(
                    Intent(this, TicketActivity::class.java).putExtra(Extras.BOOKING_ID, result.bookingId)
                )
                finish()
            }
            is BookingResult.Failure -> showError(result.message)
        }
    }
}
