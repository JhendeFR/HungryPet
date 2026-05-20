package com.usfx.hungrypet.viewmodel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.usfx.hungrypet.data.AppDatabase
import com.usfx.hungrypet.data.FeedingLog
import com.usfx.hungrypet.data.FirebaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).feedingLogDao()
    private val firebaseRepository = FirebaseRepository()

    val logs: StateFlow<List<FeedingLog>> = dao.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // --- ESTADOS DE LA PANTALLA DE INICIO (HOME) ---
    private val _homeDispenseAmount = MutableStateFlow(50)
    val homeDispenseAmount: StateFlow<Int> = _homeDispenseAmount.asStateFlow()

    // Resumen diario calculado reactivamente filtrando los logs por la fecha de hoy
    val dailyStats: StateFlow<Map<String, Int>> = logs.combine(_homeDispenseAmount) { list, _ ->
        val todayStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val todayLogs = list.filter { it.fechaHora.startsWith(todayStr) }
        mapOf(
            "Manual" to todayLogs.filter { it.tipo == "Manual" }.sumOf { it.cantidad },
            "Programado" to todayLogs.filter { it.tipo == "Programado" }.sumOf { it.cantidad },
            "Automático" to todayLogs.filter { it.tipo == "Automático" }.sumOf { it.cantidad }
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, mapOf("Manual" to 0, "Programado" to 0, "Automático" to 0))

    // --- ESTADOS DEL HARDWARE ESP32 & IA ---
    private val _cameraFrame = MutableStateFlow<Bitmap?>(null)
    val cameraFrame: StateFlow<Bitmap?> = _cameraFrame.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _isEsp32AiActive = MutableStateFlow(false)
    val isEsp32AiActive: StateFlow<Boolean> = _isEsp32AiActive.asStateFlow()

    private val _esp32DetectionResult = MutableStateFlow("Esperando activación de proximidad...")
    val esp32DetectionResult: StateFlow<String> = _esp32DetectionResult.asStateFlow()

    private val _isEsp32FlashOn = MutableStateFlow(false)
    val isEsp32FlashOn: StateFlow<Boolean> = _isEsp32FlashOn.asStateFlow()

    private val _aiDispenseAmount = MutableStateFlow(40)
    val aiDispenseAmount: StateFlow<Int> = _aiDispenseAmount.asStateFlow()

    private val imageLabeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.60f).build()
    )
    private var lastFeededTime: Long = 0

    fun setHomeDispenseAmount(amount: Int) { _homeDispenseAmount.value = amount }
    fun setAiDispenseAmount(amount: Int) { _aiDispenseAmount.value = amount }

    fun toggleEsp32Flash() {
        _isEsp32FlashOn.value = !_isEsp32FlashOn.value
        // Aquí se llamaría a Retrofit para encender/apagar físicamente el pin GPIO4 del ESP32
        // RetrofitClient.apiService.toggleFlash(_isEsp32FlashOn.value)
    }

    fun toggleCameraStream() {
        if (_isStreaming.value) {
            _isStreaming.value = false
            _isEsp32AiActive.value = false
            _cameraFrame.value = null
            _esp32DetectionResult.value = "IA en reposo"
        } else {
            _isStreaming.value = true
            viewModelScope.launch {
                firebaseRepository.getCameraStream().collect { base64String ->
                    if (_isStreaming.value) {
                        val bitmap = decodeBase64ToBitmap(base64String)
                        _cameraFrame.value = bitmap
                        if (bitmap != null && _isEsp32AiActive.value) {
                            analyzeEsp32Frame(bitmap)
                        }
                    }
                }
            }
        }
    }

    fun toggleEsp32Ai() {
        _isEsp32AiActive.value = !_isEsp32AiActive.value
        _esp32DetectionResult.value = if (_isEsp32AiActive.value) {
            "Escuchando ultrasonido HC-SR04..."
        } else {
            "IA Desactivada"
        }
    }

    private fun analyzeEsp32Frame(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        imageLabeler.process(image)
            .addOnSuccessListener { labels ->
                var detectedText = "Monitoreando entorno..."
                var animalDetected = "Otro"

                for (label in labels) {
                    val text = label.text.lowercase()
                    val confidence = (label.confidence * 100).toInt()

                    if (text.contains("dog") || text.contains("puppy") || text.contains("canine")) {
                        detectedText = "¡PERRO DETECTADO! ($confidence%) 🐶"
                        animalDetected = "Perro"
                        break
                    } else if (text.contains("cat") || text.contains("kitten") || text.contains("feline")) {
                        detectedText = "¡GATO DETECTADO! ($confidence%) 🐱"
                        animalDetected = "Gato"
                        break
                    }
                }

                _esp32DetectionResult.value = detectedText

                if ((animalDetected == "Perro" || animalDetected == "Gato")) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastFeededTime > 45000) { // Ventana de resguardo de 45 seg
                        lastFeededTime = currentTime
                        executeFeederAutomation(animalDetected)
                    }
                }
            }
            .addOnFailureListener { Log.e("MLKit_ESP32", "Inferencia fallida", it) }
    }

    private fun executeFeederAutomation(mascota: String) {
        val portion = _aiDispenseAmount.value
        viewModelScope.launch {
            // RetrofitClient.apiService.dispenseFood(portion)
            saveLog("Automático", mascota, portion)
            sendNotification("IA Automática", "Se identificó un $mascota. Dispensando ${portion}g.")
        }
    }

    fun dispenseFoodManual(amount: Int, onComplete: () -> Unit) {
        viewModelScope.launch {
            // RetrofitClient.apiService.dispenseFood(amount)
            saveLog("Manual", "N/A", amount)
            sendNotification("Alimentación Manual", "Dispensando ración de ${amount}g.")
            onComplete()
        }
    }

    private suspend fun saveLog(tipo: String, deteccion: String, cantidad: Int) {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val currentDate = sdf.format(Date())
        dao.insertLog(FeedingLog(0, currentDate, tipo, deteccion, cantidad))
    }

    private fun decodeBase64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) { null }
    }

    private fun sendNotification(title: String, message: String) {
        val context = getApplication<Application>().applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "hungrypet_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "HungryPet System", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}