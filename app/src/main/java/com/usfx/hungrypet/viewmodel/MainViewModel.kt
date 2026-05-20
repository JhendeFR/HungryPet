package com.usfx.hungrypet.viewmodel
import android.R
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.usfx.hungrypet.data.AppDatabase
import com.usfx.hungrypet.data.FeedingLog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
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

    val logs: StateFlow<List<FeedingLog>> = dao.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // --- NUEVO CÓDIGO PARA FIREBASE Y CAMARA ---
    private val firebaseRepository = FirebaseRepository()

    // Estado que contendrá el frame de la cámara listo para dibujar
    private val _cameraFrame = MutableStateFlow<Bitmap?>(null)
    val cameraFrame: StateFlow<Bitmap?> = _cameraFrame.asStateFlow()

    // Controla si estamos escuchando el stream o no
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    fun toggleCameraStream() {
        if (_isStreaming.value) {
            // Detener el stream (simplemente cambiamos el estado, la UI dejará de pedirlo)
            _isStreaming.value = false
            _cameraFrame.value = null // Limpiamos la imagen
        } else {
            // Iniciar el stream
            _isStreaming.value = true
            viewModelScope.launch {
                firebaseRepository.getCameraStream().collect { base64String ->
                    if (_isStreaming.value) {
                        // Decodificamos solo si seguimos en estado de streaming
                        _cameraFrame.value = decodeBase64ToBitmap(base64String)
                    }
                }
            }
        }
    }

    private fun decodeBase64ToBitmap(base64Str: String): Bitmap? {
        return try {
            // Decodificar Base64 a un arreglo de bytes
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            // Convertir el arreglo de bytes en un Bitmap
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun dispenseFoodManual(amount: Int) {
        viewModelScope.launch {
            // Descomentar cuando el ESP32 esté en red:
            // RetrofitClient.apiService.dispenseFood(amount)
            saveLog("Manual", "N/A", amount)
            sendNotification("Alimentación Manual", "Se han dispensado ${amount}g.")
        }
    }

    // Simulación del disparador del sensor de proximidad + TFLite
    fun handleAutoDetection(detectedLabel: String) {
        if (detectedLabel == "Perro" || detectedLabel == "Gato") {
            viewModelScope.launch {
                val amount = if (detectedLabel == "Perro") 50 else 30
                // RetrofitClient.apiService.dispenseFood(amount)
                saveLog("Automático", detectedLabel, amount)
                sendNotification("Mascota detectada: $detectedLabel", "Dispensando ${amount}g.")
            }
        } else {
            // Es otro objeto, no se activan los servos.
            println("Objeto detectado ignorado: $detectedLabel")
        }
    }

    private suspend fun saveLog(tipo: String, deteccion: String, cantidad: Int) {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val currentDate = sdf.format(Date())
        dao.insertLog(FeedingLog(0, currentDate, tipo, deteccion, cantidad))
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
            .setSmallIcon(R.drawable.ic_dialog_info) // Cambiar por tu icono
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}