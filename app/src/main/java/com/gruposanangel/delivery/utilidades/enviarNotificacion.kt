package com.gruposanangel.delivery.utilidades

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

fun enviarNotificacion(
    token: String, 
    titulo: String, 
    mensaje: String, 
    imagen: String? = null,
    tipo: String? = null,
    idExtra: String? = null
) {

    val client = OkHttpClient()

    val jsonBuilder = StringBuilder()
    jsonBuilder.append("{")
    jsonBuilder.append("\"token\": \"$token\",")
    jsonBuilder.append("\"titulo\": \"$titulo\",")
    jsonBuilder.append("\"mensaje\": \"$mensaje\"")
    if (imagen != null) jsonBuilder.append(", \"imagen\": \"$imagen\"")
    if (idExtra != null) jsonBuilder.append(", \"ventaId\": \"$idExtra\"") // Usamos ventaId porque la función JS lo soporta
    if (tipo != null) jsonBuilder.append(", \"tipo\": \"$tipo\"")
    jsonBuilder.append("}")

    val json = jsonBuilder.toString()

    val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaType())

    val request = Request.Builder()
        .url("https://us-central1-appventas--san-angel.cloudfunctions.net/enviarNotificacion")
        .post(requestBody)
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("Notificacion", "Error enviando notificación", e)
        }

        override fun onResponse(call: Call, response: Response) {
            Log.d("Notificacion", "Respuesta: ${response.body?.string()}")
        }
    })
}