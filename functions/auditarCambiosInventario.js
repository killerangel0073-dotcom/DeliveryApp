const { onDocumentUpdated } = require("firebase-functions/v2/firestore");
const admin = require('firebase-admin');
const db = admin.firestore();

/**
 * Cloud Function para Auditar cambios en el catálogo de inventario.
 * Detecta cambios en precios y stock realizados manualmente (fuera de ventas).
 */
exports.auditarCambiosInventario = onDocumentUpdated('inventarioStock/{stockId}', async (event) => {
    const dataAntes = event.data.before.data();
    const dataDespues = event.data.after.data();
    const stockId = event.params.stockId;

    if (!dataAntes || !dataDespues) return null;

    const cambios = {};
    const timestamp = admin.firestore.FieldValue.serverTimestamp();

    // 1. AUDITORÍA DE PRECIOS
    if (dataAntes.precioUnitario !== dataDespues.precioUnitario) {
        cambios.precio = {
            anterior: dataAntes.precioUnitario || 0,
            nuevo: dataDespues.precioUnitario || 0
        };
    }

    // 2. AUDITORÍA DE STOCK (Detectar ajustes manuales)
    // Nota: Si el cambio de stock es muy grande o no tiene una venta asociada, es sospechoso.
    if (dataAntes.cantidad !== dataDespues.cantidad) {
        cambios.stock = {
            anterior: dataAntes.cantidad || 0,
            nuevo: dataDespues.cantidad || 0,
            diferencia: (dataDespues.cantidad || 0) - (dataAntes.cantidad || 0)
        };
    }

    // Si hubo cambios relevantes, guardar en el Log de Auditoría
    if (Object.keys(cambios).length > 0) {
        try {
            await db.collection('auditoria_inventario_maestro').add({
                stockId: stockId,
                productoNombre: dataDespues.productoNombre || "Desconocido",
                almacenNombre: dataDespues.almacenNombre || "Desconocido",
                cambios: cambios,
                fecha: timestamp,
                // Intentamos identificar quién hizo el cambio si se subió via Dashboard/Admin
                adminReferencia: dataDespues.ultimaActualizacionPor || "Sistema/Manual"
            });
            console.log(`✅ Auditoría registrada para ${stockId}`);
        } catch (error) {
            console.error("❌ Error escribiendo auditoría:", error.message);
        }
    }

    return null;
});
