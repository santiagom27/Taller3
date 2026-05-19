package com.example.taller3.model

data class Usuario(
    val nombre: String = "",
    val email: String = "",
    val telefono: String = "",
    val enLinea: Boolean = false,
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    val fotoPerfil: String = ""
)