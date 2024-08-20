package com.example.weatherapp.models

import java.io.Serializable

data class Main(
    val temp: Double,
    val pressure: Double,
    val humidity: Double,
    val temp_min: Double,
    val temp_max: Double,
    val sea_level: Double,
    val gmd_level: Double
): Serializable