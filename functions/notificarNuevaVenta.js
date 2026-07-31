const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

const db = getFirestore();

exports.notificarNuevaVenta = onDocumentCreated("ventas/{ventaId}", async (event) => {
    const ventaIdFirestore = event.params.ventaId;
    const nuevaVenta = event.data.data();

    if (!nuevaVenta) {
        console.error(`[Venta ${ventaIdFirestore}] Error: No se encontraron datos en el documento.`);
        return null;
    }

    const { clienteId, vendedorId, total, clienteNombre } = nuevaVenta;
    const montoTotal = total || 0;

    const montoFormateado = new Intl.NumberFormat('es-MX', {
        style: 'currency',
        currency: 'MXN',
        minimumFractionDigits: 2
    }).format(montoTotal);

    try {
        // 1. OBTENER DATOS DEL VENDEDOR Y RUTA (Con Timeout de 5 segundos)
        let nombreRuta = "Ruta General";
        let nombreVendedor = "Vendedor";

        if (vendedorId) {
            const vendedorDoc = await db.collection("users").doc(vendedorId).get();
            if (vendedorDoc.exists) {
                const vData = vendedorDoc.data();
                nombreVendedor = vData.nombre || "Vendedor";

                const rutaRef = vData.rutaAsignada;
                if (rutaRef && typeof rutaRef.get === 'function') {
                    // Verificación de integridad: Solo consultamos si la referencia es válida
                    const rutaDoc = await rutaRef.get();
                    if (rutaDoc.exists && rutaDoc.data().activo !== false) {
                        nombreRuta = rutaDoc.data().nombre || "Ruta General";
                    }
                }
            }
        }

        // 2. DETERMINAR IMAGEN (Priorizar evidencia de visita, luego perfil de cliente)
        let urlImagenFinal = nuevaVenta.fotoEvidenciaVisita || "";

        if (!urlImagenFinal && clienteId) {
            const clienteDoc = await db.collection("clientes").doc(clienteId).get();
            if (clienteDoc.exists) {
                urlImagenFinal = clienteDoc.data().FotografiaCliente || "";
            }
        }

        // 3. BUSCAR DIRECTIVOS (CEO, Gerente General y Supervisor)
        const usuariosSnapshot = await db.collection("users")
            .where("puestoTrabajo", "in", ["CEO", "Gerente General", "Supervisor"])
            .where("activo", "==", true)
            .get();

        if (usuariosSnapshot.empty) {
            console.warn(`[Venta ${ventaIdFirestore}] No hay directivos activos para notificar.`);
            return null;
        }

        let tokens = [];
        usuariosSnapshot.forEach(doc => {
            const data = doc.data();
            if (Array.isArray(data.fcmTokens)) {
                data.fcmTokens.forEach(t => {
                    if (typeof t === 'string') tokens.push(t);
                    else if (t && t.token) tokens.push(t.token);
                });
            }
        });

        const tokensUnicos = [...new Set(tokens)].filter(t => t);
        if (tokensUnicos.length === 0) return null;

        // 4. CONSTRUCCIÓN DEL MENSAJE PREMIUM (Agrupando lo mejor de ambos mundos)
        const message = {
            notification: {
                title: `💰 VENTA: ${nombreRuta}`,
                body: `👤 Cliente: ${clienteNombre}\n💵 Total: ${montoFormateado}\n🚚 Vendedor: ${nombreVendedor}`,
            },
            data: {
                tipo: "VENTA_NUEVA",
                ventaId: ventaIdFirestore.toString(),
                ventaIdLocal: nuevaVenta.localId ? nuevaVenta.localId.toString() : "0",
                monto: montoTotal.toString(),
                nombreRuta: nombreRuta,
                vendedor: nombreVendedor,
                imagen: urlImagenFinal,
                click_action: "OPEN_VENTA_DETALLE"
            },
            android: {
                priority: "high",
                notification: {
                    channelId: "ventas_v3",
                    ...(urlImagenFinal ? { image: urlImagenFinal } : {})
                }
            },
            tokens: tokensUnicos,
        };

        const response = await getMessaging().sendEachForMulticast(message);
        console.log(`[Venta ${ventaIdFirestore}] Notificación enviada. Éxito: ${response.successCount}, Fallos: ${response.failureCount}`);

        return null;

    } catch (error) {
        // Log descriptivo para depuración rápida
        console.error(`[Venta ${ventaIdFirestore}] Error crítico en la ejecución:`, error);
        return null;
    }
});