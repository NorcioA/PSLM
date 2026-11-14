package com.example.samplee.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import com.example.samplee.databinding.ActivityFreeParkingBinding
import com.example.samplee.databinding.ItemFreeSessionBinding
import com.example.samplee.model.ParkingSpace
import com.example.samplee.model.Vehicle
import com.example.samplee.model.VehicleType
import com.example.samplee.util.TimeUtils
import kotlin.random.Random

/** Free parking: no fee, no booking. Park any time, leave any time. */
class FreeParkingActivity : AuthenticatedActivity() {

    private lateinit var binding: ActivityFreeParkingBinding
    private lateinit var space: ParkingSpace

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!session.isLoggedIn) return

        binding = ActivityFreeParkingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val found = db.getSpace(intent.getLongExtra(Extras.SPACE_ID, -1L))
        if (found == null || found.requiresBooking()) {
            toast("Free parking space not found.")
            finish()
            return
        }
        space = found

        binding.spinnerVehicleType.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, VehicleType.values())

        binding.btnBack.setOnClickListener { finish() }
        binding.btnRandom.setOnClickListener { fillRandomVehicle() }
        binding.btnParkNow.setOnClickListener { parkNow() }

        refresh()
    }

    /** Lets you "put a random vehicle" in the lot to try the free-parking flow. */
    private fun fillRandomVehicle() {
        val letters = (1..3).map { ('A'..'Z').random() }.joinToString("")
        binding.etPlate.setText("$letters ${Random.nextInt(1000, 10000)}")
        binding.etColor.setText(listOf("White", "Black", "Silver", "Red", "Blue", "Gray", "Green", "Yellow").random())
        binding.spinnerVehicleType.setSelection(Random.nextInt(VehicleType.values().size))
    }

    private fun parkNow() {
        binding.tvError.visibility = View.GONE
        val type = binding.spinnerVehicleType.selectedItem as VehicleType

        val vehicle = try {
            Vehicle(binding.etPlate.text.toString(), type, binding.etColor.text.toString())
        } catch (e: IllegalArgumentException) {
            showError(e.message ?: "Invalid vehicle details.")
            return
        }

        val error = db.startFreeParking(userId, space.id, vehicle)
        if (error != null) {
            showError(error)
            return
        }
        toast("${vehicle.plateNumber} is now parked. Free of charge!")
        binding.etPlate.text?.clear()
        binding.etColor.text?.clear()
        refresh()
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun refresh() {
        db.getSpace(space.id)?.let { space = it }
        binding.tvSpaceName.text = space.name
        binding.tvSpaceInfo.text =
            "Brgy. ${space.barangay}  •  ${space.address}\n${space.availabilityLabel()}"
        binding.tvFreeNote.text = space.describe()

        binding.containerActive.removeAllViews()
        val mine = db.getActiveFreeSessions(userId).filter { it.spaceId == space.id }
        binding.tvActiveTitle.text =
            if (mine.isEmpty()) "No vehicle of yours is parked here." else "Your vehicles parked here"

        for (s in mine) {
            val row = ItemFreeSessionBinding.inflate(layoutInflater, binding.containerActive, false)
            row.tvFreePlate.text = "${s.plateNumber}  •  ${s.vehicleType.label}  •  ${s.vehicleColor}"
            row.tvFreeInfo.text = "${s.spaceName}\nParked since ${TimeUtils.format(s.timeIn)}"
            row.btnEndFree.setOnClickListener {
                db.endFreeParking(s.id)?.let { msg -> toast(msg) }
                refresh()
            }
            binding.containerActive.addView(row.root)
        }
    }
}
