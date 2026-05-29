package com.gruposanangel.delivery.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.animation.core.Animatable
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.firebase.Timestamp
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.*

data class VendedorUbicacion(
    val ruta: String,
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
    val speed: Double,
    val battery: Int,
    val status: String,
    val timestamp: Timestamp
)

data class Cliente(
    val id: String,
    val nombreNegocio: String,
    val ubicacionLat: Double,
    val ubicacionLng: Double,
    val valor: String,
    val nombreDueno: String?,
    val telefono: String?,
    val fotoUrl: String?
)

class AnimatableMarker(initialLat: Double, initialLng: Double, initialRadius: Float) {
    val animLat = Animatable(initialLat.toFloat())
    val animLng = Animatable(initialLng.toFloat())
    val animRotation = Animatable(0f)
    val animRadius = Animatable(initialRadius)
    val animHaloColorFactor = Animatable((initialRadius / 100f).coerceIn(0f, 1f))
}

fun bitmapDescriptorFromPng(context: Context, resId: Int, width: Int, height: Int): BitmapDescriptor {
    val bitmap = BitmapFactory.decodeResource(context.resources, resId)
    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false)
    return BitmapDescriptorFactory.fromBitmap(scaledBitmap)
}

// 🟢 FUNCIÓN ORIGINAL (No se toca, evita errores en otras pantallas del proyecto)
fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor {
    val vectorDrawable = ContextCompat.getDrawable(context, vectorResId)
    vectorDrawable!!.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
    val bitmap = Bitmap.createBitmap(vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    vectorDrawable.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

// 🚀 NUEVA SOBRECARGA (Exclusiva para redimensionar los iconos del mapa)
fun bitmapDescriptorFromVector(context: Context, vectorResId: Int, width: Int, height: Int): BitmapDescriptor {
    val vectorDrawable = ContextCompat.getDrawable(context, vectorResId) ?: return BitmapDescriptorFactory.defaultMarker()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    vectorDrawable.setBounds(0, 0, width, height)
    vectorDrawable.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

fun calcularAngulo(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
    val dLon = Math.toRadians(lng2 - lng1)
    val y = sin(dLon) * cos(Math.toRadians(lat2))
    val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
            sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(dLon)
    val brng = Math.toDegrees(atan2(y, x)).toFloat()
    return (brng + 360) % 360
}

fun tiempoTranscurrido(timestamp: Timestamp): String {
    val ahora = Date()
    val tiempoMs = ahora.time - timestamp.toDate().time
    val minutos = TimeUnit.MILLISECONDS.toMinutes(tiempoMs)
    val horas = TimeUnit.MILLISECONDS.toHours(tiempoMs)
    val dias = TimeUnit.MILLISECONDS.toDays(tiempoMs)
    return when {
        minutos < 1 -> "justo ahora"
        minutos < 60 -> "hace $minutos min"
        horas < 24 -> "hace $horas h"
        else -> "hace $dias d"
    }
}

val darkMapStyleJson = """
[
  {"featureType": "all","elementType": "labels.text.fill","stylers":[{"color":"#f0f0f0"}]},
  {"featureType": "all","elementType": "labels.text.stroke","stylers":[{"color":"#181818"}]},

  {"featureType": "landscape","elementType": "geometry","stylers":[{"color":"#222222"}]},

  {"featureType": "poi","elementType": "geometry","stylers":[{"color":"#2b2b2b"}]},
  {"featureType": "poi","elementType": "labels.text.fill","stylers":[{"color":"#ffffff"}]},

  {"featureType": "road","elementType": "geometry","stylers":[{"color":"#333333"}]},
  {"featureType": "road.highway","elementType": "geometry","stylers":[{"color":"#8a1e1e"}]},
  {"featureType": "road.highway","elementType": "geometry.stroke","stylers":[{"color":"#c74444"}]},

  {"featureType": "water","elementType": "geometry","stylers":[{"color":"#191919"}]}
]
""".trimIndent()
