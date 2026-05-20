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

    // --- ESTADOS DE LA CÁMARA ESP32 ---
    private val _cameraFrame = MutableStateFlow<Bitmap?>(null)
    val cameraFrame: StateFlow<Bitmap?> = _cameraFrame.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    // --- NUEVOS ESTADOS PARA LA IA DEL ESP32 ---
    private val _isEsp32AiActive = MutableStateFlow(false)
    val isEsp32AiActive: StateFlow<Boolean> = _isEsp32AiActive.asStateFlow()

    private val _esp32DetectionResult = MutableStateFlow("IA en reposo")
    val esp32DetectionResult: StateFlow<String> = _esp32DetectionResult.asStateFlow()

    // Inicializamos ML Kit aquí para que analice los Bitmaps provenientes de Firebase
    private val imageLabeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.60f)
            .build()
    )

    // Variable de control para evitar múltiples raciones por una misma detección continua
    private var lastFeededTime: Long = 0

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

                        // SI LA IA DEL ESP32 ESTÁ ACTIVA Y LLEGA UNA IMAGEN, LA ANALIZAMOS
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
        if (!_isEsp32AiActive.value) {
            _esp32DetectionResult.value = "IA Desactivada"
        } else {
            _esp32DetectionResult.value = "Analizando stream del ESP32..."
            // Si ya hay una imagen en pantalla, la procesamos inmediatamente
            _cameraFrame.value?.let { analyzeEsp32Frame(it) }
        }
    }

    private fun analyzeEsp32Frame(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)

        imageLabeler.process(image)
            .addOnSuccessListener { labels ->
                var detectedText = "Buscando mascotas..."
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
                    } else if (text.contains("pet") || text.contains("animal")) {
                        detectedText = "Mascota detectada ($confidence%) 🐾"
                        animalDetected = "Mascota"
                    }
                }

                _esp32DetectionResult.value = detectedText

                // LÓGICA DE AUTOMATIZACIÓN REAL: Dispensar si se confirma Perro o Gato
                if (animalDetected == "Perro" || animalDetected == "Gato") {
                    val currentTime = System.currentTimeMillis()
                    // Ventana de tiempo (1 minuto) para evitar que dispense sin parar con el mismo frame
                    if (currentTime - lastFeededTime > 60000) {
                        lastFeededTime = currentTime
                        executeFeederAutomation(animalDetected)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("MLKit_ESP32", "Error al procesar frame de Firebase", e)
            }
    }

    private fun executeFeederAutomation(mascota: String) {
        val amount = if (mascota == "Perro") 50 else 30
        viewModelScope.launch {
            try {
                // Aquí se conectará con tus servos reales a través de Retrofit
                // RetrofitClient.apiService.dispenseFood(amount)

                saveLog("Automático", mascota, amount)
                sendNotification("Alimentador Automático", "Se detectó un $mascota. Dispensados ${amount}g.")
            } catch (e: Exception) {
                Log.e("Retrofit", "Error al enviar comando al dispensador", e)
            }
        }
    }

    fun dispenseFoodManual(amount: Int) {
        viewModelScope.launch {
            // RetrofitClient.apiService.dispenseFood(amount)
            saveLog("Manual", "N/A", amount)
            sendNotification("Alimentación Manual", "Se han dispensado ${amount}g.")
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
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun sendNotification(title: String, message: String) {
        val context = getApplication<Application>().applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "hungrypet_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "HungryPet Notificaciones", NotificationManager.IMPORTANCE_DEFAULT)
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