package com.example.swiftaid

import android.content.Context
import android.location.Geocoder
import androidx.lifecycle.ViewModel
import com.example.swiftaid.db.PoiSyncWorker
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

class MapViewModel : ViewModel() {

    private val _userLocation = MutableStateFlow<LatLng?>(null)
    val userLocation: StateFlow<LatLng?> = _userLocation

    private val _userAddress = MutableStateFlow<String>("Locating...")
    val userAddress: StateFlow<String> = _userAddress

    fun fetchLocation(context: Context) {
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)

            fusedClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    _userLocation.value = latLng
                    PoiSyncWorker.schedule(context, latLng.latitude, latLng.longitude)
                    updateAddress(context, latLng)
                } else {
                    // If last location is null, request a fresh one
                    val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 1000
                    ).setMaxUpdates(1).build()

                    fusedClient.requestLocationUpdates(
                        locationRequest,
                        object : com.google.android.gms.location.LocationCallback() {
                            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                                result.lastLocation?.let { newLocation ->
                                    val latLng = LatLng(newLocation.latitude, newLocation.longitude)
                                    _userLocation.value = latLng
                                    PoiSyncWorker.schedule(context, latLng.latitude, latLng.longitude)
                                    updateAddress(context, latLng)
                                }
                            }
                        },
                        android.os.Looper.getMainLooper()
                    )
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            _userAddress.value = "Permission Denied"
        }
    }

    private fun updateAddress(context: Context, latLng: LatLng) {
        val geocoder = Geocoder(context, Locale.getDefault())
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { addresses ->
                if (addresses.isNotEmpty()) {
                    _userAddress.value = formatAddress(addresses[0])
                } else {
                    _userAddress.value = "Unknown Location"
                }
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    _userAddress.value = formatAddress(addresses[0])
                } else {
                    _userAddress.value = "Unknown Location"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _userAddress.value = "Address unavailable"
            }
        }
    }

    private fun formatAddress(address: android.location.Address): String {
        val subLocality = address.subLocality ?: ""
        val locality = address.locality ?: ""
        return if (subLocality.isNotEmpty() && locality.isNotEmpty()) {
            "$subLocality, $locality"
        } else if (locality.isNotEmpty()) {
            locality
        } else {
            address.getAddressLine(0)?.split(",")?.take(2)?.joinToString(",") ?: "Unknown Location"
        }
    }
}
