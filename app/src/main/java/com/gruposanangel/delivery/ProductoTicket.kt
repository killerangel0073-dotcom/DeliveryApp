import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class ProductoTicketDetalle(
    val nombre: String,
    val cantidad: Int,
    val precio: Double
) : Parcelable

@Parcelize
data class TicketVentaCompleto(
    val numeroTicket: String,
    val cliente: String,
    val total: Double,
    val fecha: Date,
    val sincronizado: Boolean,
    val fotoCliente: String = "",
    val productos: List<ProductoTicketDetalle> = emptyList()
) : Parcelable
