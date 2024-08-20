package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActionBar
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.LocationManager
import android.location.LocationRequest
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    private var mSupportActionBar: ActionBar? = null

    private lateinit var mSharedPreferences: SharedPreferences

    private var mProgressDialog: Dialog? = null

    private var binding: ActivityMainBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setSupportActionBar(binding?.toolbar)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupUI()

        if (!isLocationEnabled()){
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }
        else{
            Dexter.withContext(this).withPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).
            withListener(object: MultiplePermissionsListener{
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report!!.areAllPermissionsGranted()){
                        requestLocationData()
                    }
                    if (report.isAnyPermissionPermanentlyDenied){
                        Toast.makeText(this@MainActivity, "You've denied location permission", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    showRationalDialogForPermission()
                }

            }).onSameThread().check()
        }

    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        val mLocationRequest = com.google.android.gms.location.LocationRequest()
        mLocationRequest.priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper())

    }

    private val mLocationCallback = object: LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation= locationResult.lastLocation
            val latitude = mLastLocation!!.latitude
            val longitude = mLastLocation!!.longitude
            Log.i("LatLng", "$latitude $longitude")
            getLocationWeatherDetails(latitude, longitude)
            mFusedLocationClient.removeLocationUpdates(this)
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double){
        if (Constants.isNetworkAvailable(this)){
            val retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL).
                    addConverterFactory(GsonConverterFactory.create()).build()

            val service: WeatherService = retrofit.create(WeatherService::class.java)
            val listCall: Call<WeatherResponse> = service.getWeather(latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID)
            showCustomDialog()
            listCall.enqueue(object: Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful){
                        hideCustomDialog()
                        val weatherList = response.body()

                        val weatherResponseJsonString = Gson().toJson(weatherList)

                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        setupUI()
                        Log.i("Response res", "$weatherList")
                    }
                    else{
                        var rc = response.code()
                        when(rc){
                            400 -> Log.e("Error", "400")
                            404 -> Log.e("Error", "404")
                            else -> Log.e("Error", "Generic")
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Error", t.message.toString())
                    hideCustomDialog()
                }

            })
        }
        else{
            Toast.makeText(this@MainActivity, "No internet", Toast.LENGTH_LONG).show()
        }
    }

    private fun showRationalDialogForPermission(){
        AlertDialog.Builder(this).setMessage("Turn the permission on").setPositiveButton("Go to settings"){
            _,_ ->
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }catch (e: ActivityNotFoundException){
                e.printStackTrace()
            }
        }.setNegativeButton("Cancel"){
            dialog, _ ->
            dialog.dismiss()
        }.show()
    }

    private fun isLocationEnabled(): Boolean{
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showCustomDialog(){
        mProgressDialog = Dialog(this)

        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    private fun hideCustomDialog(){
        if (mProgressDialog != null){
            mProgressDialog!!.dismiss()
        }
    }

    private fun setupUI() {
        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")

        if (!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)
            Log.i("WeatherName", weatherList.weather[0].toString())
            binding?.tvMain?.text = weatherList.weather[0].main
            binding?.tvMainDescription?.text = weatherList.weather[0].description
            binding?.tvTemp?.text =
                weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
            Log.i("Sunset", weatherList.sys.sunset.toString())
            Log.i("Sunrise", weatherList.sys.sunrise.toString())
            binding?.tvSunrise?.text = unixTime(weatherList.sys.sunrise)
            binding?.tvSunset?.text = unixTime(weatherList.sys.sunset)
            binding?.tvHumidityDescription?.text = weatherList.main.humidity.toString()
            binding?.tvTempMin?.text = weatherList.main.temp_min.toString()
            binding?.tvTempMax?.text = weatherList.main.temp_max.toString()
            binding?.tvWind?.text = weatherList.wind.speed.toString()
            binding?.tvLocation?.text = weatherList.name
            binding?.tvLocationDescription?.text = weatherList.sys.country

            when(weatherList.weather[0].icon){
                "01d" -> binding?.ivMain?.setImageResource(R.drawable.sunny)
                "02d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "03d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "04d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "04n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "10d" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                "11d" -> binding?.ivMain?.setImageResource(R.drawable.storm)
                "13d" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                "01n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "02n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "03n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "10n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "11n" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                "13n" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
            }
        }


    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh -> {
                requestLocationData()
                Log.i("Refresh", "refresh")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun getUnit(value: String): String {
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value){
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex: Long): String{
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm")
        sdf.timeZone = TimeZone.getDefault()

        return sdf.format(date)
    }
}