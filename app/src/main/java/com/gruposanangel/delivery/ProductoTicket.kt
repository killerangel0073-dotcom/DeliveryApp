import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class ProductoTicketDetalle(
    val nombre: String,
    val cantidad: Int,
    val precio: Double,
    val categoria: String = "General"
) : Parcelable

@Parcelize
data class TicketVentaCompleto(
    val numeroTicket: String,
    val cliente: String,
    val total: Double,
    val fecha: Date,
    val sincronizado: Boolean,
    val fotoCliente: String = "",
    val productos: List<ProductoTicketDetalle> = emptyList(),
    val vendedorNombre: String = "Vendedor",
    val fueraDeRango: Boolean = false,
    val fotoEvidenciaUrl: String? = null,
    val estado: String = "pagada",
    val motivoCancelacion: String? = null,
    val motivoVisita: String? = null,
    val origenDatos: String = "NUBE" // 🔥 Nuevo: "LOCAL" o "NUBE"
) : Parcelable
