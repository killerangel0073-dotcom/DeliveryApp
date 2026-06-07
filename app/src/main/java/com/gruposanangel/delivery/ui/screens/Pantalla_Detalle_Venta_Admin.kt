package com.gruposanangel.delivery.ui.screens

import ProductoTicketDetalle
import TicketVentaCompleto
import android.bluetooth.BluetoothDevice
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.FirebaseDataSource
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.utilidades.ImprimirTicket58mmCompleto
import com.gruposanangel.delivery.data.MovimientoInventarioEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Pantalla_Detalle_Venta_Admin(
    navController: NavController,
    ticketId: String,
    impresoraBluetooth: BluetoothDevice? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = AppDatabase.getDatabase(context)
    val firebaseDataSource = FirebaseDataSource()
    val inventarioRepo = RepositoryInventario(firebaseDataSource, db.productoDao(), db.VentaDao(), db.movimientoInventarioDao())
    val ventaRepository = VentaRepository(db.VentaDao())
    val repoUsuario = RepositoryUsuario(firebaseDataSource, db.usuarioDao())

    val viewModel: VentaViewModel = viewModel(
        factory = VentaViewModelFactory(
            repositoryInventario = inventarioRepo,
            ventaRepository = ventaRepository,
            repositoryUsuario = repoUsuario
        )
    )

    var ticketState by remember { mutableStateOf<TicketVentaCompleto?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // --- ESTADOS PARA AJUSTES ---
    var showBottomSheet by remember { mutableStateOf(false) }
    var tipoAjuste by remember { mutableStateOf("") } // "CAMBIO" o "DEVOLUCION"
    var productoRecibido by remember { mutableStateOf<Plantilla_Producto?>(null) }
    var productoEntregado by remember { mutableStateOf<Plantilla_Producto?>(null) }
    var cantidadAjuste by remember { mutableStateOf(1) }
    var motivoAjuste by remember { mutableStateOf("") }
    val inventoryState by viewModel.uiState.collectAsState()

    LaunchedEffect(ticketId) {
        isLoading = true
        ticketState = viewModel.obtenerTicketCompleto(ticketId)
        isLoading = false
    }

    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    val formatoFecha = SimpleDateFormat("EEEE, d 'de' MMMM 'del' yyyy", Locale.forLanguageTag("es-MX"))
    val formatoHora = SimpleDateFormat("hh:mm a", Locale.forLanguageTag("es-MX"))

    val onImprimir: () -> Unit = {
        if (impresoraBluetooth == null) {
            Toast.makeText(context, "No hay impresora configurada", Toast.LENGTH_SHORT).show()
        } else {
            scope.launch(Dispatchers.IO) {
                try {
                    val ventaEntity = viewModel.obtenerVentaPorId(ticketId)
                    val detalles = viewModel.obtenerDetallesDeVenta(ticketId)
                    val productosParaImprimir = detalles.map { d ->
                        Plantilla_Producto(d.productoId, d.nombre, d.precio, d.cantidad)
                    }
                    val usuario = if (ventaEntity?.vendedorId != null) {
                        db.usuarioDao().obtenerPorId(ventaEntity.vendedorId)
                    } else null
                    
                    val nombreVendedorFinal = usuario?.nombre ?: ticketState?.vendedorNombre ?: "Vendedor"
                    
                    ImprimirTicket58mmCompleto(
                        device = impresoraBluetooth,
                        context = context,
                        logoDrawableId = R.drawable.logo,
                        cliente = ventaEntity?.clienteNombre ?: ticketState?.cliente ?: "",
                        productos = productosParaImprimir,
                        ventaId = ventaEntity?.id,
                        fechaVenta = ventaEntity?.fecha?.let { Date(it) } ?: ticketState?.fecha ?: Date(),
                        totalVenta = ventaEntity?.total ?: ticketState?.total ?: 0.0,
                        vendedorNombre = nombreVendedorFinal,
                        metodoPago = ventaEntity?.metodoPago ?: "",

                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Imprimiendo...", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    val folio = ticketState?.numeroTicket?.takeLast(6)?.uppercase() ?: ""
                    Text("Detalle Venta #$folio", fontWeight = FontWeight.Black) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.Red)
                    }
                },
                actions = {
                    if (ticketState != null) {
                        IconButton(onClick = onImprimir) {
                            Icon(Icons.Default.Print, "Imprimir", tint = Color.Red)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            if (ticketState != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 16.dp,
                    color = Color.White
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        TotalCard(ticketState!!.total, formatoMoneda)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onImprimir,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Icon(Icons.Default.Print, null)
                            Spacer(Modifier.width(8.dp))
                            Text("IMPRIMIR TICKET", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.Red)
            }
        } else if (ticketState == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No se encontró la venta")
            }
        } else {
            val ticket = ticketState!!
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8F9FA))
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 🔹 CARD RESUMEN CABECERA
                item {
                    HeaderVentaCard(ticket, formatoMoneda, formatoFecha, formatoHora)
                }

                // 🔹 ACCIONES DE AJUSTE (AUDITORÍA)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { tipoAjuste = "CAMBIO"; showBottomSheet = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                        ) {
                            Icon(Icons.Default.Sync, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("CAMBIO", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { tipoAjuste = "DEVOLUCION"; showBottomSheet = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF9800))
                        ) {
                            Icon(Icons.Default.AssignmentReturn, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("DEVOLUCIÓN", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 🔹 SECCIÓN PRODUCTOS
                item {
                    Text(
                        "Resumen de Productos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.DarkGray
                    )
                }

                items(ticket.productos) { producto ->
                    CardProductoVendido(producto, formatoMoneda)
                }
                
                item {
                    Spacer(Modifier.height(120.dp)) // Espacio para el bottom bar que ahora es más grande
                }
            }
        }
    }

    // 🔹 BOTTOM SHEET PARA AJUSTES
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (tipoAjuste == "CAMBIO") "REGISTRAR CAMBIO" else "REGISTRAR DEVOLUCIÓN",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = if (tipoAjuste == "CAMBIO") Color.Red else Color(0xFFFF9800)
                )
                Spacer(Modifier.height(16.dp))

                // Selector de Producto RECIBIDO
                Text("Producto a quitar (Cliente entrega):", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.align(Alignment.Start))
                var expandedRecibido by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expandedRecibido = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(productoRecibido?.nombre ?: "Seleccionar producto...", color = Color.DarkGray)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(
                        expanded = expandedRecibido,
                        onDismissRequest = { expandedRecibido = false },
                        modifier = Modifier.fillMaxWidth(0.9f).background(Color.White)
                    ) {
                        inventoryState.catalogoCompleto.forEach { prod ->
                            DropdownMenuItem(
                                text = { Text(prod.nombre) },
                                onClick = { 
                                    productoRecibido = prod
                                    expandedRecibido = false
                                    // Si el precio cambia, limpiamos el entregado para obligar a re-seleccionar
                                    if (productoEntregado?.precio != prod.precio) {
                                        productoEntregado = null
                                        cantidadAjuste = 1
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Selector de Producto ENTREGADO (Filtrado por Precio)
                val habilitarEntregado = productoRecibido != null
                val productosMismoPrecio = inventoryState.productosEnCarrito.filter { 
                    it.precio == productoRecibido?.precio && it.id != productoRecibido?.id 
                }

                Text(
                    text = "Producto a dejar (Vendedor entrega):", 
                    fontSize = 12.sp, 
                    color = if (habilitarEntregado) Color.Gray else Color.LightGray, 
                    modifier = Modifier.align(Alignment.Start)
                )
                var expandedEntregado by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expandedEntregado = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = habilitarEntregado,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        val label = if (!habilitarEntregado) "Primero selecciona producto a quitar" 
                                   else if (productosMismoPrecio.isEmpty()) "No hay productos del mismo precio"
                                   else productoEntregado?.nombre ?: "Seleccionar reemplazo..."
                        
                        Text(label, color = if (habilitarEntregado) Color.DarkGray else Color.Gray, maxLines = 1)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    
                    if (productosMismoPrecio.isNotEmpty()) {
                        DropdownMenu(
                            expanded = expandedEntregado,
                            onDismissRequest = { expandedEntregado = false },
                            modifier = Modifier.fillMaxWidth(0.9f).background(Color.White)
                        ) {
                            productosMismoPrecio.forEach { prod ->
                                DropdownMenuItem(
                                    text = { Text("${prod.nombre} (Stock: ${prod.cantidadDisponible})") },
                                    onClick = { 
                                        productoEntregado = prod
                                        expandedEntregado = false
                                        // Ajustar cantidad si excede el nuevo stock
                                        if (cantidadAjuste > prod.cantidadDisponible) {
                                            cantidadAjuste = prod.cantidadDisponible.coerceAtLeast(1)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Cantidad (Topeada por Stock del Entregado)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cantidad:", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(16.dp))
                    IconButton(onClick = { if (cantidadAjuste > 1) cantidadAjuste-- }) { Icon(Icons.Default.Remove, null) }
                    Text("$cantidadAjuste", fontSize = 18.sp, fontWeight = FontWeight.Black)
                    IconButton(
                        onClick = { 
                            val stockMax = productoEntregado?.cantidadDisponible ?: Int.MAX_VALUE
                            if (cantidadAjuste < stockMax) cantidadAjuste++ 
                        },
                        enabled = productoEntregado != null && cantidadAjuste < (productoEntregado?.cantidadDisponible ?: 0)
                    ) { 
                        Icon(
                            Icons.Default.Add, 
                            null, 
                            tint = if (productoEntregado != null && cantidadAjuste < productoEntregado!!.cantidadDisponible) Color.Red else Color.Gray
                        ) 
                    }
                }
                
                if (productoEntregado != null) {
                    Text(
                        "Stock disponible: ${productoEntregado!!.cantidadDisponible}", 
                        fontSize = 11.sp, 
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Motivo
                OutlinedTextField(
                    value = motivoAjuste,
                    onValueChange = { motivoAjuste = it },
                    label = { Text(if (tipoAjuste == "CAMBIO") "Nota adicional" else "Motivo de la devolución") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (productoRecibido != null && productoEntregado != null) {
                            if (cantidadAjuste > (productoEntregado?.cantidadDisponible ?: 0)) {
                                Toast.makeText(context, "No tienes suficiente stock", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val ventaEntity = ticketState
                            viewModel.registrarAjusteDoble(
                                ticketId = ticketId,
                                clienteId = ventaEntity?.numeroTicket,
                                productoEntra = productoRecibido!!,
                                productoSale = productoEntregado!!,
                                cantidad = cantidadAjuste,
                                tipoOperacion = tipoAjuste,
                                motivo = motivoAjuste,
                                onSuccess = {
                                    showBottomSheet = false
                                    productoRecibido = null
                                    productoEntregado = null
                                    cantidadAjuste = 1
                                    motivoAjuste = ""
                                    Toast.makeText(context, "Operación registrada exitosamente", Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            Toast.makeText(context, "Selecciona ambos productos", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (tipoAjuste == "CAMBIO") Color.Red else Color(0xFFFF9800)
                    )
                ) {
                    Text("CONFIRMAR OPERACIÓN", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun HeaderVentaCard(
    ticket: TicketVentaCompleto,
    fMoneda: NumberFormat,
    fFecha: SimpleDateFormat,
    fHora: SimpleDateFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!ticket.fotoCliente.isNullOrEmpty()) {
                    AsyncImage(
                        model = ticket.fotoCliente,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(85.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(85.dp),
                        shape = CircleShape,
                        color = Color.Red.copy(alpha = 0.1f)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            null,
                            tint = Color.Red,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        "TICKET #${ticket.numeroTicket.takeLast(6).uppercase()}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Text(
                        ticket.cliente,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Color(0xFFF1F2F6))
            Spacer(Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                InfoCol("FECHA", fFecha.format(ticket.fecha).uppercase())
                InfoCol("HORA", fHora.format(ticket.fecha).uppercase())
            }
        }
    }
}

@Composable
fun CardProductoVendido(producto: ProductoTicketDetalle, fMoneda: NumberFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF8F9FA),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "${producto.cantidad}",
                        fontWeight = FontWeight.Black,
                        color = Color.Red,
                        fontSize = 16.sp
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(producto.nombre, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "P. Unitario: ${fMoneda.format(producto.precio)}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            Text(
                fMoneda.format(producto.precio * producto.cantidad),
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun TotalCard(total: Double, fMoneda: NumberFormat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "TOTAL PAGADO: ",
            color = Color.Gray,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Text(
            fMoneda.format(total),
            color = Color.Red,
            fontWeight = FontWeight.Black,
            fontSize = 26.sp
        )
    }
}

@Composable
fun InfoCol(label: String, value: String) {
    Column {
        Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Black)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.DarkGray)
    }
}
