package com.gruposanangel.delivery.utilidades

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gruposanangel.delivery.ui.theme.*
import java.text.NumberFormat
import java.util.*
import androidx.compose.ui.draw.shadow

/**
 * Lógica de persistencia de metas
 */
class PreferenciasMetas(context: Context) {
    private val prefs = context.getSharedPreferences("metas_prefs", Context.MODE_PRIVATE)
    private val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun obtenerValores(metaDefault: Double, clientesDefault: Int): Pair<Double, Int> {
        val fechaHoy = sdf.format(Date())
        val ultimaFecha = prefs.getString("ultima_fecha", "")
        if (fechaHoy != ultimaFecha) return Pair(metaDefault, clientesDefault)

        val meta = prefs.getFloat("meta_guardada", metaDefault.toFloat()).toDouble()
        val clientes = prefs.getInt("clientes_guardados", clientesDefault)
        return Pair(meta, clientes)
    }

    fun guardarValores(meta: Double, clientes: Int) {
        val fechaHoy = sdf.format(Date())
        prefs.edit().apply {
            putString("ultima_fecha", fechaHoy)
            putFloat("meta_guardada", meta.toFloat())
            putInt("clientes_guardados", clientes)
            apply()
        }
    }
}

@Composable
fun MedidorDeMetaPremium(
    metaDelDia: Double,
    totalClientes: Int,
    clientesVisitados: Int,
    avance: Double,
    numDias: Int = 1,
    isLoading: Boolean = false,
    onUpdateMeta: (Double) -> Unit,
    onUpdateClientes: (Int) -> Unit,
    onCalendarClick: (() -> Unit)? = null,
    fechaInicio: Long? = null,
    fechaFin: Long? = null
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    var showMetaDialog by remember { mutableStateOf(false) }
    var showClientesDialog by remember { mutableStateOf(false) }

    val esLogrado = avance >= metaDelDia && !isLoading

    // --- LÓGICA DE TÍTULO DINÁMICO ---
    val tituloLabel = remember(fechaInicio, fechaFin, esLogrado, numDias) {
        if (esLogrado) return@remember "¡META ALCANZADA!"
        
        if (fechaInicio == null || fechaFin == null || fechaInicio == 0L) {
            return@remember if (numDias > 1) "PROGRESO DEL PERIODO" else "PROGRESO DEL DÍA"
        }

        val calInicio = Calendar.getInstance().apply { timeInMillis = fechaInicio }
        val calFin = Calendar.getInstance().apply { timeInMillis = fechaFin }
        val calHoy = Calendar.getInstance()

        val esMismoDia = calInicio.get(Calendar.YEAR) == calFin.get(Calendar.YEAR) &&
                        calInicio.get(Calendar.DAY_OF_YEAR) == calFin.get(Calendar.DAY_OF_YEAR)

        if (esMismoDia) {
            val esHoy = calInicio.get(Calendar.YEAR) == calHoy.get(Calendar.YEAR) &&
                       calInicio.get(Calendar.DAY_OF_YEAR) == calHoy.get(Calendar.DAY_OF_YEAR)
            
            if (esHoy) "VENTA DEL DÍA"
            else {
                val sdfDia = java.text.SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "MX"))
                sdfDia.format(calInicio.time).lowercase().replaceFirstChar { it.uppercase() }
            }
        } else {
            val sdfPeriodo = java.text.SimpleDateFormat("d 'de' MMM", Locale("es", "MX"))
            val inicioStr = sdfPeriodo.format(calInicio.time).replace(".", "")
            val finStr = sdfPeriodo.format(calFin.time).replace(".", "")
            "$inicioStr al $finStr".uppercase()
        }
    }

    // --- ANIMACIONES ---
    val infiniteTransition = rememberInfiniteTransition(label = "premiumEffects")
    
    // 1. Sheen (Brillo diagonal)
    val lightSheenOffset by infiniteTransition.animateFloat(
        initialValue = -1500f, targetValue = 1500f,
        animationSpec = infiniteRepeatable(tween(3500, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "sheen"
    )

    // 2. Respiración enérgica
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "glow"
    )

    val porcentajeReal = if (metaDelDia > 0) (avance / metaDelDia).toFloat() else 0f
    val animatedProgress = animateFloatAsState(
        targetValue = porcentajeReal.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "progreso"
    )

    val falta = (metaDelDia - avance).coerceAtLeast(0.0)
    val ticketPromedio = if (clientesVisitados > 0) avance / clientesVisitados else 0.0
    val totalFaltantes = (totalClientes - clientesVisitados).coerceAtLeast(0)
    val ticketNecesario = if (totalFaltantes > 0) falta / totalFaltantes else 0.0

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // HALO TRASERO (Backlight)
        if (esLogrado) {
            val haloColor = if (isDark) Color.White.copy(0.4f) else DelisaRed.copy(0.6f)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(130.dp)
                    .shadow(elevation = 20.dp, shape = RoundedCornerShape(24.dp))
                    .background(haloColor, RoundedCornerShape(24.dp))
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .shadow(elevation = if (esLogrado) 12.dp else 6.dp, shape = RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        // FONDO BASE
                        val gradient = if (esLogrado) {
                            Brush.verticalGradient(listOf(Color(0xFFE30613), Color(0xFF900000)))
                        } else {
                            Brush.verticalGradient(listOf(DelisaRed, DelisaRedDark))
                        }
                        drawRect(brush = gradient)
                        
                        // ✨ REFLEXIÓN DE LUZ PREMIUM
                        if (esLogrado) {
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color.Transparent, Color.White.copy(0.1f), Color.White.copy(0.3f), Color.White.copy(0.1f), Color.Transparent),
                                    start = Offset(lightSheenOffset, 0f),
                                    end = Offset(lightSheenOffset + 500f, size.height)
                                )
                            )
                        }
                        
                        drawContent()
                    }
                    .padding(12.dp)
            ) {
                Column {
                    // HEADER
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.align(Alignment.CenterStart).padding(end = 75.dp)) {
                            Text(tituloLabel, color = Color.White.copy(0.8f), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) 
                            
                            AnimatedContent(
                                targetState = avance,
                                transitionSpec = {
                                    (slideInVertically { height -> height } + fadeIn() togetherWith
                                     slideOutVertically { height -> -height } + fadeOut()).using(SizeTransform(clip = false))
                                }, label = "montoAnim"
                            ) { targetAvance ->
                                Text(
                                    currencyFormat.format(targetAvance),
                                    color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black,
                                    style = androidx.compose.ui.text.TextStyle(
                                        shadow = if (esLogrado) Shadow(color = Color.Black.copy(0.4f), offset = Offset(0f, 4f), blurRadius = 8f) else null
                                    )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.align(Alignment.TopEnd),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Box(
                                contentAlignment = Alignment.Center, 
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .scale(if (esLogrado) breathingScale else 1f)
                            ) {
                                Canvas(modifier = Modifier.size(54.dp)) { 
                                    drawArc(Color.White.copy(0.2f), 0f, 360f, false, style = Stroke(6f))
                                    drawArc(Color.White, -90f, 360f * animatedProgress.value, false, style = Stroke(6f, cap = StrokeCap.Round))
                                    
                                    if (esLogrado) {
                                        drawArc(
                                            color = Color.White.copy(alpha = glowAlpha * 0.4f),
                                            startAngle = 0f,
                                            sweepAngle = 360f,
                                            useCenter = false,
                                            style = Stroke(width = 12f)
                                        )
                                    }
                                }
                                AnimatedContent(
                                    targetState = (porcentajeReal * 100).toInt(),
                                    transitionSpec = {
                                        (slideInVertically { height -> height } + fadeIn() togetherWith
                                         slideOutVertically { height -> -height } + fadeOut()).using(SizeTransform(clip = false))
                                    }, label = "pctAnim"
                                ) { targetPct ->
                                    Text("$targetPct%", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                                }
                            }
                            
                            if (onCalendarClick != null) {
                                IconButton(
                                    onClick = onCalendarClick,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .offset(x = 10.dp, y = (-10).dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.CalendarMonth, 
                                        contentDescription = "Calendario", 
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.clickable { showMetaDialog = true }) {
                                Text("META: ${currencyFormat.format(metaDelDia)}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("FALTA: ${currencyFormat.format(falta)}", color = Color.White.copy(0.9f), fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(Color.Black.copy(0.2f))) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress.value)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(Brush.horizontalGradient(listOf(Color.White.copy(0.7f), Color.White, Color.White.copy(0.7f))))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(0.12f)).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f).clickable { showClientesDialog = true }) {
                            StatItem("CLIENTES", "$clientesVisitados/$totalClientes")
                        }
                        Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color.White.copy(0.2f)))
                        Box(modifier = Modifier.weight(1f)) {
                            StatItem("TICKET PROM.", currencyFormat.format(ticketPromedio))
                        }
                        Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color.White.copy(0.2f)))
                        Box(modifier = Modifier.weight(1f)) {
                            if (avance >= metaDelDia) StatItem("ESTADO", "LOGRADO")
                            else StatItem("OBJETIVO", currencyFormat.format(ticketNecesario))
                        }
                    }
                }
            }
        }
    }

    if (showMetaDialog) {
        EditValueDialog("Ajustar Meta", metaDelDia.toString(), { showMetaDialog = false }) {
            onUpdateMeta(it.toDoubleOrNull() ?: metaDelDia); showMetaDialog = false
        }
    }
    if (showClientesDialog) {
        EditValueDialog("Total Clientes", totalClientes.toString(), { showClientesDialog = false }) {
            onUpdateClientes(it.toDoubleOrNull()?.toInt() ?: totalClientes); showClientesDialog = false
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditValueDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val esDinero = title.contains("Meta", true)
    var text by remember {
        mutableStateOf(
            if (esDinero) String.format("%.0f", (initialValue.toDoubleOrNull() ?: 0.0) * 100)
            else initialValue.replace(Regex("[^0-9]"), "")
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(title, color = DelisaRed, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        val onlyDigits = it.filter { c -> c.isDigit() }
                        if (onlyDigits.length <= 9) text = onlyDigits
                    },
                    visualTransformation = if (esDinero) MoneyVisualTransformation() else VisualTransformation.None,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DelisaRed, cursorColor = DelisaRed)
                )
            }
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold) }
                Button(onClick = {
                    val finalVal = if (esDinero) (text.toDoubleOrNull() ?: 0.0) / 100.0 else (text.toDoubleOrNull() ?: 0.0)
                    onConfirm(finalVal.toString())
                }, colors = ButtonDefaults.buttonColors(containerColor = DelisaRed), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1.2f)) {
                    Text("GUARDAR", fontWeight = FontWeight.Black, color = Color.White)
                }
            }
        }
    )
}

class MoneyVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val input = text.text
        val doubleValue = input.toDoubleOrNull() ?: 0.0
        val formatted = NumberFormat.getCurrencyInstance(Locale("es", "MX")).format(doubleValue / 100.0)
        return TransformedText(AnnotatedString(formatted), object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = formatted.length
            override fun transformedToOriginal(offset: Int): Int = input.length
        })
    }
}
