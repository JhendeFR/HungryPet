package com.usfx.hungrypet.ui.components

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.Executors

@OptIn(ExperimentalGetImage::class)
@Composable
fun PhoneCameraView(
    onDetectionUpdate: (String) -> Unit // Callback para enviar el texto detectado a la interfaz
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Creamos un hilo secundario (Executor) para que la IA no congele la pantalla principal (UI)
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // 1. Inicializamos ML Kit. Threshold 0.60f significa que solo nos avisará
    // si está al menos 60% seguro de lo que está viendo.
    val imageLabeler = remember {
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.60f)
                .build()
        )
    }

    // Limpieza de memoria: Cuando cerramos esta pantalla, apagamos la IA y la cámara
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            imageLabeler.close()
        }
    }

    // AndroidView permite usar vistas clásicas (PreviewView de CameraX) dentro de Jetpack Compose
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                // 2. Vista previa: Lo que el usuario ve en la pantalla
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // 3. Analizador: Toma foto por foto (frames) de la cámara en tiempo real
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // Evita cuellos de botella procesando solo la foto más reciente
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                // Preparamos la imagen para ML Kit considerando la rotación del teléfono
                                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                                // 4. Mandamos la imagen al cerebro de ML Kit
                                imageLabeler.process(image)
                                    .addOnSuccessListener { labels ->
                                        var detectedText = "Buscando mascotas..."

                                        // 5. ML Kit nos devuelve una lista de etiquetas (ej: "Chair", "Dog", "Table")
                                        for (label in labels) {
                                            val text = label.text.lowercase()
                                            val confidence = (label.confidence * 100).toInt()

                                            // Comprobamos si la etiqueta coincide con nuestros objetivos
                                            if (text.contains("dog") || text.contains("puppy") || text.contains("canine")) {
                                                detectedText = "¡PERRO DETECTADO! ($confidence%) 🐶"
                                                break // Detenemos el ciclo si ya hallamos un perro
                                            } else if (text.contains("cat") || text.contains("kitten") || text.contains("feline")) {
                                                detectedText = "¡GATO DETECTADO! ($confidence%) 🐱"
                                                break
                                            } else if (text.contains("pet") || text.contains("animal")) {
                                                detectedText = "Mascota o Animal ($confidence%) 🐾"
                                            }
                                        }
                                        // Actualizamos el estado en Jetpack Compose
                                        onDetectionUpdate(detectedText)
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("MLKit", "Error en la detección", e)
                                    }
                                    .addOnCompleteListener {
                                        // MUY IMPORTANTE: Liberar la imagen actual para que la cámara pueda capturar la siguiente
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }
                    }

                // Seleccionamos la cámara trasera por defecto
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    // Desvincula cualquier uso previo y vincula la cámara a nuestra pantalla
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )
                } catch (exc: Exception) {
                    Log.e("Cámara", "Error al iniciar cámara", exc)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}