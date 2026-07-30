package com.gruposanangel.delivery.utilidades

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
    onUpdateMeta: (Double) -> Unit,
    onUpdateClientes: (Int) -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }
    var showMetaDialog by remember { mutableStateOf(false) }
    var showClientesDialog by remember { mutableStateOf(false) }

    // --- ANIMACIONES ---
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerTranslate"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulse"
    )

    val porcentajeObjetivo = (avance / metaDelDia).coerceIn(0.0, 1.0).toFloat()
    val animatedProgress = animateFloatAsState(
        targetValue = porcentajeObjetivo,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "progreso"
    )

    // Lógica de Negocio
    val falta = (metaDelDia - avance).coerceAtLeast(0.0)
    val ticketPromedio = if (clientesVisitados > 0) avance / clientesVisitados else 0.0
    val totalFaltantes = (totalClientes - clientesVisitados).coerceAtLeast(0)
    val ticketNecesario = if (totalFaltantes > 0) falta / totalFaltantes else 0.0

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth(0.95f).wrapContentHeight().shadow(12.dp, RoundedCornerShape(28.dp), ambientColor = DelisaRed.copy(0.4f)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface)
        ) {
            Column(modifier = Modifier.background(Brush.verticalGradient(listOf(DelisaRed, DelisaRedDark))).padding(18.dp)) {
                // HEADER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("PROGRESO DEL DÍA", color = Color.White.copy(0.7f), fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                        Text(
                            currencyFormat.format(avance),
                            color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = Shadow(color = Color.White.copy(alpha = glowAlpha), blurRadius = 15f)
                            )
                        )
                    }
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.scale(pulseScale)) {
                        Canvas(modifier = Modifier.size(52.dp)) {
                            drawArc(Color.White.copy(0.2f), 0f, 360f, false, style = Stroke(8f))
                            drawArc(Color.White, -90f, 360f * animatedProgress.value, false, style = Stroke(8f, cap = StrokeCap.Round))
                        }
                        Text("${(porcentajeObjetivo * 100).toInt()}%", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // BARRA DE PROGRESO
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Box(modifier = Modifier.clickable { showMetaDialog = true }) {
                            Text(currencyFormat.format(metaDelDia), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        }
                        Text("RESTA: ${currencyFormat.format(falta)}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(22.dp).clip(CircleShape).background(Color.Black.copy(0.2f))) {
                        Box(
                            modifier = Modifier.fillMaxWidth(animatedProgress.value).fillMaxHeight().clip(CircleShape)
                                .background(Brush.horizontalGradient(listOf(Color.White.copy(0.7f), Color.White, Color.White.copy(0.7f))))
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color.Transparent, Color.White.copy(0.4f), Color.Transparent),
                                        start = Offset(shimmerTranslate - 200, 0f), end = Offset(shimmerTranslate, 0f)
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // PANEL DE ESTADÍSTICAS
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(0.12f)).padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f).clickable { showClientesDialog = true }) {
                        StatItem("CLIENTES", "$clientesVisitados/$totalClientes")
                    }
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.White.copy(0.2f)))
                    Box(modifier = Modifier.weight(1f)) {
                        StatItem("TICKET PROM.", currencyFormat.format(ticketPromedio))
                    }
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.White.copy(0.2f)))
                    Box(modifier = Modifier.weight(1f)) {
                        if (avance >= metaDelDia) StatItem("ESTADO", "LOGRADO", isSuccess = true)
                        else StatItem("OBJETIVO", currencyFormat.format(ticketNecesario), isWarning = true)
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
fun StatItem(label: String, value: String, isSuccess: Boolean = false, isWarning: Boolean = false) {
    val textColor = when {
        isSuccess -> Color.White
        isWarning -> Color(0xFFE0E0E0)
        else -> Color.White
    }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(0.8f), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 1)
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
            Text(
                text = title,
                color = DelisaRed,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
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
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DelisaRed,
                        focusedLabelColor = DelisaRed,
                        cursorColor = DelisaRed
                    )
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val finalVal = if (esDinero) (text.toDoubleOrNull() ?: 0.0) / 100.0
                        else (text.toDoubleOrNull() ?: 0.0)
                        onConfirm(finalVal.toString())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DelisaRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.2f)
                ) {
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

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun MedidorMetaPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            MedidorDeMetaPremium(
                metaDelDia = 15000.0,
                totalClientes = 40,
                clientesVisitados = 12,
                avance = 4500.0,
                onUpdateMeta = {},
                onUpdateClientes = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EditValueDialogPreview() {
    MaterialTheme {
        // Mostramos el diálogo de Meta como ejemplo
        EditValueDialog(
            title = "Ajustar Meta",
            initialValue = "15000.0",
            onDismiss = {},
            onConfirm = {}
        )
    }
}