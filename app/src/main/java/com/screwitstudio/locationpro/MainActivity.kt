package com.screwitstudio.locationpro

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.screwitstudio.locationpro.utils.Constants.LATITUDE
import com.screwitstudio.locationpro.utils.Constants.LONGITUDE
import com.vmadalin.easypermissions.EasyPermissions

class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 123
    private lateinit var button: Button
    private lateinit var textview: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        button = findViewById(R.id.btn_location)
        textview = findViewById(R.id.text_location)

        // Check for location permission
        button.setOnClickListener {
            if (hasLocationPermission()) {
                // Location permission is granted, request the current location
                locationWorker() // if you don't want worker you can you requestLocation()
            } else {
                // Request location permission
                requestLocationPermission()
            }
        }

    }

    private fun hasLocationPermission(): Boolean {
        return (ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED)
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun requestLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener(this
            ) { location ->
                if (location != null) {
                    // Use the location data (latitude, longitude, etc.) here
                    val latitude = location.latitude
                    val longitude = location.longitude
                    textview.text = "Latitude: $latitude, Longitude: $longitude"
                    Toast.makeText(
                        this,
                        "Latitude: $latitude, Longitude: $longitude",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    // Location data is not available, handle this case
                    Toast.makeText(this, "Location data not available", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onPermissionsDenied(requestCode: Int, perms: List<String>) {
        Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
    }

    override fun onPermissionsGranted(requestCode: Int, perms: List<String>) {
        locationWorker() //// if you don't want worker you can you requestLocation()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    private fun locationWorker(){
        val locationWorkRequest = OneTimeWorkRequest.Builder(LocationWorker::class.java).build()

        // Enqueue the work request with WorkManager
        WorkManager.getInstance(this).enqueue(locationWorkRequest)

        // You can also observe the work's status if needed
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(locationWorkRequest.id)
            .observe(this) { workInfo ->
                if (workInfo != null && workInfo.state.isFinished) {
                    // The worker has completed successfully
                    val outputData = workInfo.outputData
                    val latitude = outputData.getDouble(LATITUDE,0.0)
                    val longitude = outputData.getDouble(LONGITUDE,0.0)

                    // Log messages for debugging
                    Log.d("MainActivity", "Worker completed successfully")
                    Log.d("MainActivity", "Latitude: $latitude, Longitude: $longitude")

                    runOnUiThread {
                        textview.text = "Latitude: $latitude, Longitude: $longitude"
                    }

                    // Display toast message
                    Toast.makeText(
                        this,
                        "Latitude: $latitude\nLongitude: $longitude",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }
}
