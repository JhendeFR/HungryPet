package com.usfx.hungrypet.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feeding_logs")
data class FeedingLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fechaHora: String,
    val tipo: String, // "Manual", "Programado", "Automático IA"
    val resultadoDeteccion: String, // "Perro", "Gato"
    val cantidad: Int //Cantidad de alimento dispensado
)