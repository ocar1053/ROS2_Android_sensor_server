package github.umer0586.sensorserver.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Bundle
import android.os.Looper
import com.google.android.gms.location.*

class GpsHandler(
    context: Context,
    private val onLocationUpdated: (Location) -> Unit // callback for invoke rosbridge message
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // gps update parameters
    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
        .setMinUpdateIntervalMillis(500)
        .build()

    // callback for location updates
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult ?: return
            for (location in locationResult.locations) {
                onLocationUpdated(location)
            }
        }
    }

    // listen to location updates
    @SuppressLint("MissingPermission")
    fun startListening() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }
}
