package com.usfx.hungrypet.network

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// 1. Interfaz con los endpoints
interface Esp32Api {
    // Endpoint para activar los servos
    @GET("/dispense")
    suspend fun dispenseFood(@Query("amount") amount: Int): Response<Unit>
}

// 2. Cliente Retrofit en el mismo archivo
object RetrofitClient {
    private const val BASE_URL = "http://192.168.1.100/" // Reemplaza con la IP de tu ESP32

    val apiService: Esp32Api by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Esp32Api::class.java)
    }
}
