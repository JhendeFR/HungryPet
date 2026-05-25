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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.google.firebase.database.FirebaseDatabase

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

    private val _esp32DetectionResult = MutableStateFlow("Esperando cuadros...")
    val esp32DetectionResult: StateFlow<String> = _esp32DetectionResult.asStateFlow()

    private val _isEsp32FlashOn = MutableStateFlow(false)
    val isEsp32FlashOn: StateFlow<Boolean> = _isEsp32FlashOn.asStateFlow()

    private val _aiDispenseAmount = MutableStateFlow(40)
    val aiDispenseAmount: StateFlow<Int> = _aiDispenseAmount.asStateFlow()

    private var firebaseJob: Job? = null
    private var lastFeededTime: Long = 0

    // Motor de ML Kit
    private val imageLabeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.60f).build()
    )

    fun setHomeDispenseAmount(amount: Int) { _homeDispenseAmount.value = amount }
    fun setAiDispenseAmount(amount: Int) { _aiDispenseAmount.value = amount }

    fun toggleEsp32Flash() {
        _isEsp32FlashOn.value = !_isEsp32FlashOn.value
        // Aquí se llamaría a Retrofit para encender/apagar físicamente el pin GPIO4 del ESP32
        // RetrofitClient.apiService.toggleFlash(_isEsp32FlashOn.value)
    }

    // --- LÓGICA DE CONTROL FIREBASE/IA ---

    fun toggleCameraStream() {
        _isStreaming.value = !_isStreaming.value
        manageFirebaseListener()
    }

    fun toggleEsp32Ai() {
        _isEsp32AiActive.value = !_isEsp32AiActive.value
        manageFirebaseListener()
    }

    // Gestiona si el teléfono debe descargar imágenes de Firebase o no
    private fun manageFirebaseListener() {
        val needsConnection = _isStreaming.value || _isEsp32AiActive.value

        if (needsConnection && firebaseJob == null) {
            firebaseJob = viewModelScope.launch {
                firebaseRepository.getCameraStream().collect { base64String ->
                    val bitmap = decodeBase64ToBitmap(base64String)

                    if (bitmap != null) {
                        // Si el Live está activo, actualizamos la imagen en pantalla
                        if (_isStreaming.value) {
                            _cameraFrame.value = bitmap
                        }

                        // Si cualquiera está activo, inferimos la imagen.
                        // El booleano determina si se debe automatizar el dispensador o no.
                        analyzeEsp32Frame(bitmap, autoDispense = _isEsp32AiActive.value)
                    }
                }
            }
        } else if (!needsConnection && firebaseJob != null) {
            // Apagamos la conexión para ahorrar batería y datos
            firebaseJob?.cancel()
            firebaseJob = null
            _cameraFrame.value = null
            _esp32DetectionResult.value = "Sistema en reposo"
        }
    }

    private fun analyzeEsp32Frame(bitmap: Bitmap, autoDispense: Boolean) {
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

                // Solo dispensamos si la función IA está activa
                if (autoDispense && (animalDetected == "Perro" || animalDetected == "Gato")) {
                    val currentTime = System.currentTimeMillis()
                    // Ventana de 1 minuto para evitar que se vacíe el dispensador
                    if (currentTime - lastFeededTime > 60000) { // Ventana de 1 min
                        lastFeededTime = currentTime
                        executeFeederAutomation(animalDetected)
                    }
                }
            }
            .addOnFailureListener { Log.e("MLKit_ESP32", "Inferencia fallida", it) }
    }

    // --- LÓGICA DE BLUETOOTH (MOCK PARA LABS) ---

    fun sendBleCredentials(ssid: String, pass: String, mode: Int, onResult: (String) -> Unit) {
        // El ESP32 espera este formato exacto separado por punto y coma: "MiWifi;12345;1"
        // Si no hay wifi, se envía: ";;1" o ";;0"
        val payload = "${ssid.trim()};${pass.trim()};$mode"

        viewModelScope.launch {
            // Aquí llamarías a tu BluetoothGatt.writeCharacteristic() pasándole payload.toByteArray()
            onResult("Conectando BLE...")
            delay(1500) // Simulación de tiempo de conexión
            onResult("Trama enviada al ESP32: [$payload]")
        }
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
            // 1. Obtenemos la referencia al nodo exacto que tu ESP32 está escuchando
            val databaseRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("dispensador/activar")

            // 2. Cambiamos el valor a 1 para activar el mecanismo físico
            databaseRef.setValue(1)
                .addOnSuccessListener {
                    println("HungryPetLog: Comando enviado a Firebase con éxito.")
                }
                .addOnFailureListener { exception ->
                    println("HungryPetLog: Error al enviar comando: ${exception.message}")
                }

            // 3. Guardamos la ración en la base de datos local (RoomDB)
            saveLog("Manual", "N/A", amount)

            // 4. Disparamos la notificación push en el teléfono
            sendNotification("Alimentación Manual", "Se han dispensado ${amount}g.")

            // 5. Avisamos a la interfaz de usuario (HomeScreen) que ya terminamos para que quite el cartel de "Dispensando"
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