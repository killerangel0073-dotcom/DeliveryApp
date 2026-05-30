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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
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
    val inventarioRepo = RepositoryInventario(firebaseDataSource, db.productoDao(), db.VentaDao())
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
