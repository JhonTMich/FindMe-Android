package com.example.findme.domain.model

object Registration {
    fun isEmail(value: String) = value.trim().length <= 254 && Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(value.trim())
    fun error(name: String, email: String, username: String, password: String, confirmation: String): String? = when {
        name.trim().length !in 1..80 || name.trim().equals("Mi cuenta", true) -> "Introduce tu nombre, de 1 a 80 caracteres."
        !isEmail(email) -> "Introduce un correo válido."
        !Username.isValid(username) -> "Usa entre 3 y 24 letras sin tildes, números o guion bajo para tu usuario."
        password.length !in 8..128 -> "La contraseña debe tener entre 8 y 128 caracteres."
        password != confirmation -> "Las contraseñas no coinciden."
        else -> null
    }
}
