package com.gruposanangel.delivery.ui.screens

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.data.*
import com.gruposanangel.delivery.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.core.content.FileProvider
import androidx.compose.foundation.isSystemInDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaArqueo(navController: NavController) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val firebaseDataSource = FirebaseDataSource()
    val repoUsuario = RepositoryUsuario(firebaseDataSource, db.usuarioDao())
    val inventarioRepo = RepositoryInventario(firebaseDataSource, db.productoDao(), db.VentaDao(), db.movimientoInventarioDao())
    val ventaRepo = com.gruposanangel.delivery.VentaRepository(db.VentaDao(), db.productoDao())
    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

    val viewModel: ArqueoViewModel = viewModel(
        factory = ArqueoViewModelFactory(db.VentaDao(), db.productoDao(), inventarioRepo, repoUsuario, ventaRepo, uid)
    )

    val uiState by viewModel.uiState.collectAsState()
    var tabIndex by remember { mutableIntStateOf(0) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    val isDark = ThemeConfig.isActuallyDark

    LaunchedEffect(uiState.reporteGuardado) {
        if (uiState.reporteGuardado) {
            showSuccessDialog = true
        }
    }

    DeliveryTheme(darkTheme = isDark) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Column {
                            Text("ARQUEO: ${uiState.almacenAuditado}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Vendedor: ${uiState.nombreVendedor}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Rounded.ArrowBackIosNew, null, modifier = Modifier.size(20.dp), tint = DelisaRed)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refrescarDatos() }) {
                            Icon(Icons.Rounded.Refresh, "Refrescar", tint = DelisaRed)
                        }
                        if (tabIndex == 2) {
                            IconButton(onClick = { 
                                val yaEstaAutocompletado = uiState.productosArqueo.all { it.stockReal == it.stockTeorico.toString() }
                                uiState.productosArqueo.forEach { 
                                    viewModel.actualizarStockReal(it.id, if (yaEstaAutocompletado) "" else it.stockTeorico.toString()) 
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.FlashOn, 
                                    contentDescription = "Autocompletar", 
                                    tint = DelisaBlue
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
                TabRow(
                    selectedTabIndex = tabIndex, 
                    containerColor = MaterialTheme.colorScheme.surface, 
                    contentColor = DelisaRed,
                    indicator = { tabPositions ->
                        if (tabIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[tabIndex]),
                                color = DelisaRed
                            )
                        }
                    },
                    divider = {}
                ) {
                    Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("RESUMEN", fontWeight = FontWeight.Black, fontSize = 12.sp) })
                    Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("BITÁCORA", fontWeight = FontWeight.Black, fontSize = 12.sp) })
                    Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("CONTEO FÍSICO", fontWeight = FontWeight.Black, fontSize = 12.sp) })
                }
                when (tabIndex) {
                    0 -> ResumenTab(uiState)
                    1 -> BitacoraMovimientosTab(uiState)
                    2 -> ArqueoFisicoTab(uiState, { id, valStr -> viewModel.actualizarStockReal(id, valStr) }, { showAuthDialog = true })
                }
            }
        }

        // 🔥 OVERLAY DE CARGA GLOBAL
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = DelisaRed)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Procesando Auditoría...",
                        fontWeight = FontWeight.Bold,
                        color = DelisaRed
                    )
                }
            }
        }
    }

    if (showAuthDialog) {
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!uiState.isLoading) showAuthDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Autorización de Cierre", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Para cerrar el inventario y actualizar el stock, un supervisor debe ingresar su contraseña.", color = MaterialTheme.colorScheme.onSurface)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña de Autorización") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        isError = uiState.errorAutorizacion != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DelisaRed,
                            focusedLabelColor = DelisaRed
                        )
                    )
                    if (uiState.errorAutorizacion != null) {
                        Text(uiState.errorAutorizacion!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    if (uiState.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DelisaRed)
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.autorizarCierre(password) },
                    enabled = password.length >= 4 && !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = DelisaRed),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("CONFIRMAR", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showAuthDialog = false }, enabled = !uiState.isLoading) { 
                    Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant) 
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { 
                showSuccessDialog = false
                val file = GenerarPDFArqueo(context, uiState)
                mostrarReporteArqueo(context, file)
                navController.popBackStack()
            },
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Verified, 
                    contentDescription = null, 
                    tint = DelisaGreenDark,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { Text("Arqueo Finalizado", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface) },
            text = { 
                Text(
                    text = "El inventario ha sido actualizado y el arqueo se ha cerrado correctamente.\n\nA continuación se generará el reporte PDF de auditoría.", 
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                ) 
            },
            confirmButton = {
                Button(
                    onClick = { 
                        showSuccessDialog = false
                        val file = GenerarPDFArqueo(context, uiState)
                        mostrarReporteArqueo(context, file)
                        navController.popBackStack() 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DelisaRed),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("VER REPORTE Y FINALIZAR", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }
}

@Composable
fun BitacoraMovimientosTab(state: ArqueoUiState) {
    val scrollState = rememberScrollState()
    val nf = NumberFormat.getCurrencyInstance(Locale("es", "MX"))

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // --- CABECERA DE LA TABLA ---
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 10.dp)
        ) {
            TableCell("PRODUCTO", 180.dp, isHeader = true, color = DelisaRed)
            
            // INICIAL CON VALOR
            val totalValorInicial = state.productosArqueo.sumOf { it.stockInicialBitacora * it.precio }
            Column(Modifier.width(70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("INICIAL", fontSize = 10.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Text(nf.format(totalValorInicial), fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }

            listOf("LUN", "MAR", "MIE", "JUE", "VIE", "SAB", "DOM").forEachIndexed { index, dia ->
                val totalDia = state.productosArqueo.sumOf { it.cargasPorDia[index] * it.precio }
                Column(Modifier.width(70.dp).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dia, fontSize = 10.sp, fontWeight = FontWeight.Black, color = DelisaRed)
                    Text(nf.format(totalDia), fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
            }
            
            // ARQUEO CON VALOR
            val totalValorArqueo = state.productosArqueo.sumOf { (it.stockReal.toIntOrNull() ?: 0) * it.precio }
            Column(Modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ARQUEO", fontSize = 10.sp, fontWeight = FontWeight.Black, color = DelisaRed)
                Text(nf.format(totalValorArqueo), fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }

            // DIFERENCIA CON VALOR
            val totalValorDif = state.productosArqueo.sumOf { it.difBitacora * it.precio }
            Column(Modifier.width(70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("DIF", fontSize = 10.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Text(nf.format(totalValorDif), fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(state.productosArqueo, key = { _, producto -> producto.id }) { index, producto ->
                val backgroundColor = if (index % 2 != 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                Row(
                    modifier = Modifier
                        .horizontalScroll(scrollState)
                        .background(backgroundColor)
                        .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    TableCell(producto.nombre, 180.dp, textAlign = TextAlign.Start, isBold = true, color = MaterialTheme.colorScheme.onSurface)
                    TableCell("${producto.stockInicialBitacora}", 70.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    producto.cargasPorDia.forEach { cant ->
                        TableCell(if (cant > 0) "$cant" else "-", 70.dp, color = if (cant > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                    
                    TableCell(if (producto.stockReal.isEmpty()) "0" else producto.stockReal, 80.dp, isBold = true, color = DelisaRed)

                    val colorDif = when {
                        producto.difBitacora < 0 -> DelisaRed
                        producto.difBitacora > 0 -> DelisaGreenDark
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    TableCell("${producto.difBitacora}", 70.dp, color = colorDif, isBold = true)
                }
            }
        }
    }
}

@Composable
fun TableCell(text: String, width: androidx.compose.ui.unit.Dp, isHeader: Boolean = false, textAlign: TextAlign = TextAlign.Center, color: Color = Color.Black, isBold: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.width(width).padding(8.dp),
        fontSize = if (isHeader) 11.sp else 12.sp,
        fontWeight = if (isHeader || isBold) FontWeight.Black else FontWeight.Normal,
        textAlign = textAlign,
        color = if (isHeader) MaterialTheme.colorScheme.onSurfaceVariant else color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun ResumenTab(state: ArqueoUiState) {
    val nf = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    val totalRealContado = state.productosArqueo.sumOf { it.stockReal.toIntOrNull() ?: 0 }
    val diferenciaAuditoria = totalRealContado - state.saldoTeoricoCalculado
    val hayDiferencia = totalRealContado > 0 && diferenciaAuditoria != 0

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (hayDiferencia) MaterialTheme.colorScheme.errorContainer 
                                    else if (totalRealContado > 0) DelisaGreen.copy(alpha = 0.1f) 
                                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (hayDiferencia) Icons.Rounded.ReportProblem else Icons.Rounded.VerifiedUser, 
                        contentDescription = null, 
                        tint = if (hayDiferencia) DelisaRed else if (totalRealContado > 0) DelisaGreenDark else MaterialTheme.colorScheme.onSurfaceVariant, 
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (hayDiferencia) "DIFERENCIA DETECTADA EN AUDITORÍA" 
                               else if (totalRealContado > 0) "INVENTARIO CONCILIADO" 
                               else "AUDITORÍA EN PROCESO", 
                        fontWeight = FontWeight.Black, 
                        fontSize = 14.sp, 
                        textAlign = TextAlign.Center, 
                        color = if (hayDiferencia) DelisaRed else if (totalRealContado > 0) DelisaGreenDark else MaterialTheme.colorScheme.onSurfaceVariant, 
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            Text("AUDITORÍA DE INVENTARIO (PIEZAS)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(24.dp)), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        AuditoriaMiniItem("Inicial", "${state.stockInicial}", Icons.Rounded.History, MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(10.dp))
                        AuditoriaMiniItem("Cargas", "${state.totalCargasSemana}", Icons.Rounded.FileDownload, DelisaBlue)
                        Icon(Icons.Rounded.Remove, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(10.dp))
                        AuditoriaMiniItem("Ventas", "${state.totalVentasUnidades}", Icons.Rounded.LocalShipping, DelisaRed)
                        Icon(Icons.Rounded.Remove, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(10.dp))
                        AuditoriaMiniItem("Devol.", "${state.totalDevoluciones}", Icons.Rounded.AssignmentReturn, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    Text("${state.saldoTeoricoCalculado}", fontSize = 32.sp, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                    Text("SALDO TEÓRICO FINAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }

        item {
            Text("AUDITORÍA DE VALOR (PESOS $)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }

        item {
            Card(modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(24.dp)), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        AuditoriaMiniItem("V. Inicial", nf.format(state.valorStockInicial), Icons.Rounded.Inventory2, MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(10.dp))
                        AuditoriaMiniItem("V. Cargas", nf.format(state.valorCargasSemana), Icons.Rounded.AddShoppingCart, DelisaBlue)
                        Icon(Icons.Rounded.Remove, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(10.dp))
                        AuditoriaMiniItem("V. Ventas", nf.format(state.valorVentasSemana), Icons.Rounded.PointOfSale, DelisaRed)
                        Icon(Icons.Rounded.Remove, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(10.dp))
                        AuditoriaMiniItem("V. Devol.", nf.format(state.valorDevolucionesSemana), Icons.Rounded.Restore, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    Text(nf.format(state.saldoValorTeoricoCalculado), fontSize = 32.sp, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                    Text("VALOR TEÓRICO EN MERCANCÍA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun ArqueoFisicoTab(state: ArqueoUiState, onUpdateReal: (String, String) -> Unit, onPreSave: () -> Unit) {
    val nf = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    val totalDiferenciaPesos = state.productosArqueo.sumOf { it.valorDiferencia }
    
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp).shadow(4.dp, RoundedCornerShape(16.dp)),
            color = when { 
                totalDiferenciaPesos < 0 -> MaterialTheme.colorScheme.errorContainer
                totalDiferenciaPesos > 0 -> WarningOrange.copy(alpha = 0.1f)
                else -> DelisaGreen.copy(alpha = 0.1f)
            },
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, when { 
                totalDiferenciaPesos < 0 -> DelisaRed
                totalDiferenciaPesos > 0 -> WarningOrange
                else -> DelisaGreenDark 
            }.copy(0.3f))
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (totalDiferenciaPesos < 0) Icons.Rounded.RemoveCircle 
                                 else if (totalDiferenciaPesos > 0) Icons.Rounded.AddCircle 
                                 else Icons.Rounded.CheckCircle, 
                    contentDescription = null, 
                    tint = if (totalDiferenciaPesos < 0) DelisaRed else if (totalDiferenciaPesos > 0) WarningOrange else DelisaGreenDark
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(text = if (totalDiferenciaPesos < 0) "FALTANTE A DESCONTAR" else if (totalDiferenciaPesos > 0) "DISCREPANCIA (SOBRANTE)" else "INVENTARIO COMPLETO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = if (totalDiferenciaPesos < 0) "Le faltan: ${nf.format(kotlin.math.abs(totalDiferenciaPesos))}" else if (totalDiferenciaPesos > 0) "Exceso: ${nf.format(totalDiferenciaPesos)}" else "Todo cuadrado", fontSize = 18.sp, fontWeight = FontWeight.Black, color = if (totalDiferenciaPesos < 0) DelisaRed else if (totalDiferenciaPesos > 0) WarningOrange else DelisaGreenDark)
                }
            }
        }

        val productosPorCategoria = remember(state.productosArqueo) {
            state.productosArqueo.groupBy { it.categoria }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp), 
            // verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            productosPorCategoria.forEach { (categoria, productos) ->
                item(key = "header_$categoria") {
                    CategoryHeader(categoria)
                }
                items(productos, key = { it.id }) { producto ->
                    ItemProductoArqueoPremium(producto, onUpdateReal)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shadowElevation = 20.dp, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
            Button(
                onClick = onPreSave, 
                modifier = Modifier.fillMaxWidth().padding(24.dp).height(58.dp), 
                shape = RoundedCornerShape(18.dp), 
                colors = ButtonDefaults.buttonColors(containerColor = DelisaRed)
            ) {
                Text("FINALIZAR Y CERRAR SEMANA", fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}

@Composable
fun ItemProductoArqueoPremium(producto: ProductoArqueo, onUpdateReal: (String, String) -> Unit) {
    val diff = producto.diferencia
    val colorFondo = when { 
        producto.stockReal.isEmpty() -> MaterialTheme.colorScheme.surface
        diff == 0 -> DelisaGreen.copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.errorContainer 
    }
    Card(modifier = Modifier.shadow(1.dp, RoundedCornerShape(24.dp)), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = colorFondo)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = producto.imagenUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)), placeholder = painterResource(R.drawable.repartidor))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(producto.nombre, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, color = MaterialTheme.colorScheme.onSurface)
                Text("En Camioneta: ${producto.stockTeorico} pzas", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(95.dp)) {
                OutlinedTextField(
                    value = producto.stockReal,
                    onValueChange = { if (it.length <= 4) onUpdateReal(producto.id, it) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    placeholder = { Text("REAL", fontSize = 10.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DelisaRed,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                if (producto.stockReal.isNotEmpty()) {
                    val nf = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
                    Text(text = if (diff > 0) "+$diff (${nf.format(producto.valorDiferencia)})" else if (diff < 0) "$diff (${nf.format(producto.valorDiferencia)})" else "OK", color = if (diff >= 0) DelisaGreenDark else DelisaRed, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun AuditoriaMiniItem(label: String, valor: String, icono: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.background(color.copy(0.1f), CircleShape).padding(8.dp)) { Icon(icono, null, tint = color, modifier = Modifier.size(18.dp)) }
        Text(valor, fontWeight = FontWeight.Black, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
    }
}

fun GenerarPDFArqueo(context: Context, state: ArqueoUiState): File {
    val pdfDocument = PdfDocument()
    val pageWidth = 842 // Paisaje (Landscape) para que quepa la bitácora
    val pageHeight = 595 
    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas
    val nf = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    
    val pTitle = Paint().apply { textSize = 18f; isFakeBoldText = true; color = android.graphics.Color.BLACK }
    val pSub = Paint().apply { textSize = 9f; color = android.graphics.Color.DKGRAY }
    val pText = Paint().apply { textSize = 7f; color = android.graphics.Color.BLACK }
    val pBold = Paint().apply { textSize = 7f; isFakeBoldText = true; color = android.graphics.Color.BLACK }
    val pHeader = Paint().apply { textSize = 7f; isFakeBoldText = true; color = android.graphics.Color.WHITE }
    val pLine = Paint().apply { strokeWidth = 0.3f; color = android.graphics.Color.LTGRAY; style = Paint.Style.STROKE }
    val pFillHeader = Paint().apply { color = android.graphics.Color.rgb(183, 28, 28); style = Paint.Style.FILL }

    val logo = context.getDrawable(R.drawable.logo)
    logo?.let { it.setBounds(40, 20, 120, 70); it.draw(canvas) }

    var y = 45f
    canvas.drawText("BITÁCORA DE AUDITORÍA Y ARQUEO SEMANAL", 140f, y, pTitle); y += 20f
    canvas.drawText("VENDEDOR: ${state.nombreVendedor.uppercase()} | AUTORIZADO POR: ${state.autorizadoPor?.uppercase() ?: "PENDIENTE"}", 140f, y, pSub); y += 15f
    canvas.drawText("FECHA EMISIÓN: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date())}", 140f, y, pSub); y += 30f

    // --- TABLA DE MOVIMIENTOS (EXCEL STYLE) ---
    val startX = 40f
    val colW = listOf(140f, 50f, 40f, 40f, 40f, 40f, 40f, 40f, 40f, 50f, 50f)
    val headers = listOf("PRODUCTO", "INICIAL", "LUN", "MAR", "MIE", "JUE", "VIE", "SAB", "DOM", "ARQUEO", "DIFF")
    
    // Draw Headers
    canvas.drawRect(startX, y, 790f, y + 18f, pFillHeader)
    var curX = startX
    headers.forEachIndexed { i, h ->
        canvas.drawText(h, curX + 5f, y + 12f, pHeader)
        curX += colW[i]
    }
    y += 18f

    state.productosArqueo.forEach { p ->
        if (y > 540f) return@forEach
        curX = startX
        
        // Celdas de datos
        val vals = listOf(
            p.nombre.take(30), 
            "${p.stockInicialBitacora}",
            "${p.cargasPorDia[0]}", "${p.cargasPorDia[1]}", "${p.cargasPorDia[2]}", 
            "${p.cargasPorDia[3]}", "${p.cargasPorDia[4]}", "${p.cargasPorDia[5]}", "${p.cargasPorDia[6]}",
            if(p.stockReal.isEmpty()) "0" else p.stockReal,
            "${p.difBitacora}"
        )

        vals.forEachIndexed { i, v ->
            val paint = if (i == 10 && p.difBitacora != 0) {
                Paint(pText).apply { color = if(p.difBitacora < 0) android.graphics.Color.RED else android.graphics.Color.rgb(0,120,0); isFakeBoldText = true }
            } else pText
            canvas.drawText(v, curX + 5f, y + 12f, paint)
            canvas.drawRect(curX, y, curX + colW[i], y + 16f, pLine)
            curX += colW[i]
        }
        y += 16f
    }

    // --- RESUMEN FINAL ---
    y += 30f
    val totalDif = state.productosArqueo.sumOf { it.valorDiferencia }
    val veredicto = if(totalDif < 0) "FALTANTE: ${nf.format(totalDif)}" else if(totalDif > 0) "SOBRANTE: ${nf.format(totalDif)}" else "INVENTARIO CONCILIADO"
    canvas.drawText("VEREDICTO FINAL: $veredicto", startX, y, pTitle.apply { textSize = 14f })

    pdfDocument.finishPage(page)
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "arqueo_bitacora_${System.currentTimeMillis()}.pdf")
    pdfDocument.writeTo(FileOutputStream(file)); pdfDocument.close()
    return file
}

fun mostrarReporteArqueo(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Abrir Reporte de Arqueo"))
    } catch (e: Exception) {
        Toast.makeText(context, "No se pudo abrir el PDF", Toast.LENGTH_SHORT).show()
    }
}
