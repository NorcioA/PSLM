package com.example.samplee.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.example.samplee.databinding.ActivityMyBookingsBinding
import com.example.samplee.databinding.ItemBookingBinding
import com.example.samplee.databinding.ItemFreeSessionBinding
import com.example.samplee.model.PricingMatrix
import com.example.samplee.util.TimeUtils
import com.example.samplee.util.UiUtils

/** My tickets (paid bookings), my vehicles in free lots, and the parking log. */
class MyBookingsActivity : AuthenticatedActivity() {

    private lateinit var binding: ActivityMyBookingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!session.isLoggedIn) return
        binding = ActivityMyBookingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) render()
    }

    private fun render() {
        renderBookings()
        renderFreeSessions()
        renderLogs()
    }

    private fun renderBookings() {
        val bookings = db.getBookingsForUser(userId)
        binding.containerBookings.removeAllViews()
        binding.tvNoBookings.visibility = if (bookings.isEmpty()) View.VISIBLE else View.GONE

        for (b in bookings) {
            val row = ItemBookingBinding.inflate(layoutInflater, binding.containerBookings, false)
            row.tvTicketNo.text = b.ticketNo
            row.tvBookingSpace.text = "${b.spaceName}  •  Slot ${b.slotLabel}"
            row.tvBookingVehicle.text = "${b.plateNumber}  •  ${b.vehicleType.label}  •  ${b.vehicleColor}"
            row.tvBookingWhen.text = "${TimeUtils.format(b.startMillis)}  (${b.tier.label})"
            row.tvBookingStatus.text = b.status.label
            UiUtils.styleBadge(row.tvBookingStatus, b.status.colorHex)
            row.tvBookingFee.text = PricingMatrix.formatPeso(b.totalFee)
            row.root.setOnClickListener {
                startActivity(Intent(this, TicketActivity::class.java).putExtra(Extras.BOOKING_ID, b.id))
            }
            binding.containerBookings.addView(row.root)
        }
    }

    private fun renderFreeSessions() {
        val sessions = db.getActiveFreeSessions(userId)
        binding.containerFree.removeAllViews()
        binding.tvNoFree.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE

        for (s in sessions) {
            val row = ItemFreeSessionBinding.inflate(layoutInflater, binding.containerFree, false)
            row.tvFreePlate.text = "${s.plateNumber}  •  ${s.vehicleType.label}  •  ${s.vehicleColor}"
            row.tvFreeInfo.text = "${s.spaceName}\nParked since ${TimeUtils.format(s.timeIn)}"
            row.btnEndFree.setOnClickListener {
                db.endFreeParking(s.id)?.let { msg -> toast(msg) }
                render()
            }
            binding.containerFree.addView(row.root)
        }
    }

    private fun renderLogs() {
        val logs = db.getLogsForUser(userId)
        binding.containerLogs.removeAllViews()
        binding.tvNoLogs.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE

        val pad = (6 * resources.displayMetrics.density).toInt()
        for (log in logs) {
            val line = TextView(this).apply {
                text = "${TimeUtils.format(log.logTime)}\n" +
                        "${log.action.replace('_', ' ')}  •  ${log.plateNumber}  •  ${log.spaceName}" +
                        (log.note?.let { "\n$it" } ?: "")
                textSize = 13f
                setPadding(0, pad, 0, pad)
            }
            binding.containerLogs.addView(line)
        }
    }
}
