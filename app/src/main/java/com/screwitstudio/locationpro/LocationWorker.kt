package com.screwitstudio.locationpro

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.Exception
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.screwitstudio.locationpro.utils.Constants.LATITUDE
import com.screwitstudio.locationpro.utils.Constants.LONGITUDE
import com.screwitstudio.locationpro.utils.Constants.TAG
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

@SuppressLint("MissingPermission")
class LocationWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Initialize the FusedLocationProviderClient
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)


            val location = retrieveLocation(fusedLocationClient)

            if (location != null) {
                Log.d(TAG, "Latitude: ${location.latitude}, Longitude: ${location.longitude}")
            }
            val outputData = if (location != null) {
                val latitude = location.latitude
                val longitude = location.longitude

                // Create a Data object to return latitude and longitude
                Data.Builder()
                    .putDouble(LATITUDE, latitude)
                    .putDouble(LONGITUDE, longitude)
                    .build()
            } else {
                // Location data is not available, return failure
                null
            }

            if (outputData != null) {
                Result.success(outputData)
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e("LocationWorker", "Error in doWork: ${e.message}", e)
            Result.failure()
        }
    }

    private suspend fun retrieveLocation(fusedLocationClient: FusedLocationProviderClient): Location? {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000
        ).build()

        // Create a shared Channel for location updates
        val locationChannel = Channel<Location?>(
            capacity = 1, // Adjust the capacity as needed
            onBufferOverflow = BufferOverflow.DROP_OLDEST // Handle overflow by dropping the oldest location
        )

        // Define the number of retries
        val maxRetries = 3 // You can adjust this value

        // Create a mutex to protect access to the Channel
        val channelMutex = Mutex()

        // Create a LocationCallback to handle updates
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                // Use a coroutine to safely send the location to the Channel
                GlobalScope.launch {
                    try {
                        channelMutex.withLock {
                            // Send the location to the Channel
                            locationChannel.send(location)
                        }
                    } catch (e: Exception) {
                        // Handle any exceptions that may occur while sending the location
                        Log.e("LocationWorker", "Error sending location to Channel: ${e.message}")
                    }
                }
            }
        }

        val currentLooper = Looper.myLooper()
        if (currentLooper != null) {
            // Use the current Looper
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                currentLooper
            )
        } else {
            // Use the main Looper as a fallback
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }

        try {
            // Use a coroutine to receive the first location from the Channel
            val locationResult = withTimeoutOrNull(30000) {
                locationChannel.receive()
            }

            if (locationResult != null) {
                return locationResult
            }
        } catch (e: Exception) {
            // Log any exceptions for debugging purposes
            Log.e("LocationWorker", "Error retrieving location: ${e.message}")
        } finally {
            // Remove the location updates and close the Channel
            fusedLocationClient.removeLocationUpdates(locationCallback)
            locationChannel.close()
        }

        // If all retries fail, return null or handle the case as needed
        return null
    }


}


