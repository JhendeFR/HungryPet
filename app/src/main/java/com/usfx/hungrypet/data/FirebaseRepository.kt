package com.usfx.hungrypet.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirebaseRepository {
    // Apuntamos a la base de datos (asegúrate de que la URL coincide con la de tu ESP32 si es necesario,
    // aunque si vinculaste la app en el Paso 1, Firebase.database toma la URL por defecto)
    private val database = FirebaseDatabase.getInstance().reference

    // Usamos callbackFlow para convertir los listeners asíncronos de Firebase en un Flow de Kotlin
    fun getCameraStream(): Flow<String> = callbackFlow {
        val streamReference = database.child("camara/stream")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Obtenemos el string base64 de Firebase
                val base64String = snapshot.getValue(String::class.java)
                if (base64String != null) {
                    trySend(base64String) // Lo enviamos al Flow
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Opcional: Manejar errores
                println("Error en Firebase: ${error.message}")
            }
        }

        streamReference.addValueEventListener(listener)

        // Cuando se cancela la corrutina (ej. se cierra la pantalla), quitamos el listener para ahorrar recursos
        awaitClose { streamReference.removeEventListener(listener) }
    }
}