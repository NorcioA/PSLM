package com.example.samplee.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import com.example.samplee.R
import com.example.samplee.databinding.ActivityDashboardBinding
import com.example.samplee.model.ParkingSpace
import com.example.samplee.util.MapConfig
import com.example.samplee.util.UiUtils
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * The map dashboard. Blue dots = paid, green dots = free, grey dots = full.
 * Tap a dot to see details, then book (paid) or park (free).
 */
class DashboardActivity : AuthenticatedActivity() {

    private enum class Filter { ALL, PAID, FREE }

    private lateinit var binding: ActivityDashboardBinding
    private var map: MapLibreMap? = null
    private var style: Style? = null

    private var spaces: List<ParkingSpace> = emptyList()
    private var selected: ParkingSpace? = null
    private var filter = Filter.ALL

    private val ready: Boolean get() = ::binding.isInitialized

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!session.isLoggedIn) return // AuthenticatedActivity is already redirecting to Login

        MapLibre.getInstance(this) // must run BEFORE the MapView is inflated
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvWelcome.text = "Hi, ${session.userName}"
        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync { m ->
            map = m
            m.cameraPosition = CameraPosition.Builder()
                .target(LatLng(MapConfig.CENTER_LAT, MapConfig.CENTER_LNG))
                .zoom(MapConfig.START_ZOOM)
                .build()
            // Keep the map inside Nasugbu: limit scrolling and zoom
            m.setMinZoomPreference(MapConfig.MIN_ZOOM)
            m.setMaxZoomPreference(MapConfig.MAX_ZOOM)
            m.setLatLngBoundsForCameraTarget(
                LatLngBounds.from(
                    MapConfig.BOUNDS_NORTH, MapConfig.BOUNDS_EAST,
                    MapConfig.BOUNDS_SOUTH, MapConfig.BOUNDS_WEST
                )
            )
            m.setStyle(Style.Builder().fromUri(MapConfig.STYLE_URL)) { loaded ->
                style = loaded
                setUpLayers(loaded)
                m.addOnMapClickListener { latLng ->
                    val p = m.projection.toScreenLocation(latLng)
                    val box = RectF(p.x - 40f, p.y - 40f, p.x + 40f, p.y + 40f)
                    val hit = m.queryRenderedFeatures(box, LAYER_PAID, LAYER_FREE, LAYER_FULL).firstOrNull()
                    val id = hit?.getNumberProperty("space_id")?.toLong()
                    if (id != null) {
                        selectSpace(id, moveCamera = true)
                        true
                    } else {
                        false
                    }
                }
                loadSpaces()
            }
        }

        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            filter = when (checkedIds.firstOrNull()) {
                R.id.chipPaid -> Filter.PAID
                R.id.chipFree -> Filter.FREE
                else -> Filter.ALL
            }
            selected?.let { if (!matchesFilter(it)) hideCard() }
            renderMarkers()
        }

        binding.btnMyTickets.setOnClickListener {
            startActivity(Intent(this, MyBookingsActivity::class.java))
        }
        binding.btnLogout.setOnClickListener {
            session.logout()
            goToLogin()
        }
        binding.btnCloseCard.setOnClickListener { hideCard() }
        binding.btnSpaceAction.setOnClickListener {
            val space = selected ?: return@setOnClickListener
            // POLYMORPHISM in action: the space itself says whether booking is needed.
            val target = if (space.requiresBooking()) BookingActivity::class.java else FreeParkingActivity::class.java
            startActivity(Intent(this, target).putExtra(Extras.SPACE_ID, space.id))
        }
    }

    // ---------------------------------------------------------------- map data

    private fun setUpLayers(style: Style) {
        val empty = FeatureCollection.fromFeatures(emptyList<Feature>())
        style.addSource(GeoJsonSource(SRC_SELECTED, empty))
        style.addSource(GeoJsonSource(SRC_PAID, empty))
        style.addSource(GeoJsonSource(SRC_FREE, empty))
        style.addSource(GeoJsonSource(SRC_FULL, empty))

        // Highlight ring (drawn under the markers)
        style.addLayer(
            CircleLayer(LAYER_SELECTED, SRC_SELECTED).withProperties(
                PropertyFactory.circleRadius(20f),
                PropertyFactory.circleColor("#FFC107"),
                PropertyFactory.circleOpacity(0.55f),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor("#FF8F00")
            )
        )
        style.addLayer(markerLayer(LAYER_PAID, SRC_PAID, "#1565C0"))
        style.addLayer(markerLayer(LAYER_FREE, SRC_FREE, "#2E7D32"))
        style.addLayer(markerLayer(LAYER_FULL, SRC_FULL, "#9E9E9E"))
    }

    private fun markerLayer(layerId: String, sourceId: String, colorHex: String) =
        CircleLayer(layerId, sourceId).withProperties(
            PropertyFactory.circleRadius(11f),
            PropertyFactory.circleColor(colorHex),
            PropertyFactory.circleStrokeWidth(3f),
            PropertyFactory.circleStrokeColor("#FFFFFF")
        )

    private fun matchesFilter(space: ParkingSpace) = when (filter) {
        Filter.ALL -> true
        Filter.PAID -> space.isPaid
        Filter.FREE -> !space.isPaid
    }

    /** Reads the database (real-time availability) and redraws the map. */
    private fun loadSpaces() {
        spaces = db.getAllSpaces()
        renderMarkers()
        selected?.let { old ->
            // refresh the open card with the newest availability
            if (spaces.any { it.id == old.id }) selectSpace(old.id, moveCamera = false) else hideCard()
        }
    }

    private fun renderMarkers() {
        val st = style ?: return
        val paid = mutableListOf<Feature>()
        val free = mutableListOf<Feature>()
        val full = mutableListOf<Feature>()

        for (space in spaces.filter { matchesFilter(it) }) {
            val feature = Feature.fromGeometry(Point.fromLngLat(space.longitude, space.latitude))
            feature.addNumberProperty("space_id", space.id)
            when {
                !space.isAvailable -> full.add(feature)
                space.isPaid -> paid.add(feature)
                else -> free.add(feature)
            }
        }
        st.getSourceAs<GeoJsonSource>(SRC_PAID)?.setGeoJson(FeatureCollection.fromFeatures(paid))
        st.getSourceAs<GeoJsonSource>(SRC_FREE)?.setGeoJson(FeatureCollection.fromFeatures(free))
        st.getSourceAs<GeoJsonSource>(SRC_FULL)?.setGeoJson(FeatureCollection.fromFeatures(full))
    }

    // -------------------------------------------------------------- info card

    private fun selectSpace(id: Long, moveCamera: Boolean) {
        val space = spaces.firstOrNull { it.id == id } ?: return
        selected = space

        binding.tvSpaceName.text = space.name
        binding.tvSpaceBarangay.text = "Brgy. ${space.barangay}  •  ${space.address}"
        binding.tvSpaceType.text = if (space.isPaid) "PAID" else "FREE"
        UiUtils.styleBadge(binding.tvSpaceType, space.markerColor)
        binding.tvSpaceAvailability.text = space.availabilityLabel()
        binding.tvSpaceAvailability.setTextColor(Color.parseColor(if (space.isAvailable) "#2E7D32" else "#C62828"))
        binding.tvSpaceDescription.text = space.describe()
        binding.btnSpaceAction.text = space.actionLabel
        binding.btnSpaceAction.isEnabled = space.isAvailable || space.isPaid // paid: can still book a later time
        binding.cardSpace.visibility = View.VISIBLE

        val highlight = Feature.fromGeometry(Point.fromLngLat(space.longitude, space.latitude))
        style?.getSourceAs<GeoJsonSource>(SRC_SELECTED)?.setGeoJson(highlight)

        if (moveCamera) {
            map?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(space.latitude, space.longitude)))
        }
    }

    private fun hideCard() {
        selected = null
        binding.cardSpace.visibility = View.GONE
        style?.getSourceAs<GeoJsonSource>(SRC_SELECTED)
            ?.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
    }

    // --------------------------------------------------------------- lifecycle

    override fun onStart() {
        super.onStart()
        if (ready) binding.mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        if (ready) {
            binding.mapView.onResume()
            if (style != null) loadSpaces() // e.g. coming back from booking / ticket screens
        }
    }

    override fun onPause() {
        if (ready) binding.mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        if (ready) binding.mapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (ready) binding.mapView.onLowMemory()
    }

    override fun onDestroy() {
        if (ready) binding.mapView.onDestroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (ready) binding.mapView.onSaveInstanceState(outState)
    }

    private companion object {
        const val SRC_SELECTED = "src-selected"
        const val SRC_PAID = "src-paid"
        const val SRC_FREE = "src-free"
        const val SRC_FULL = "src-full"
        const val LAYER_SELECTED = "layer-selected"
        const val LAYER_PAID = "layer-paid"
        const val LAYER_FREE = "layer-free"
        const val LAYER_FULL = "layer-full"
    }
}
