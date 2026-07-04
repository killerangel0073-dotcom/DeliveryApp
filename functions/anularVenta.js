const functions = require('firebase-functions');
const admin = require('firebase-admin');
const db = admin.firestore();

/**
 * Cloud Function para Anular/Cancelar una venta.
 * Realiza la devolución de stock al vendedor de forma atómica.
 */
const anularVenta = functions.https.onRequest(async (req, res) => {
    try {
        if (req.method !== 'POST') {
            return res.status(405).send({ error: 'Método no permitido' });
        }

        const { ventaId, motivo, adminNombre, adminUid } = req.body;

        if (!ventaId || !motivo) {
            return res.status(400).send({ error: 'Faltan datos obligatorios (ventaId, motivo)' });
        }

        const ventaRef = db.collection('ventas').doc(ventaId);

        const result = await db.runTransaction(async (transaction) => {
            const ventaSnap = await transaction.get(ventaRef);

            if (!ventaSnap.exists) {
                throw new Error('La venta no existe');
            }

            const ventaData = ventaSnap.data();

            if (ventaData.estado === 'CANCELADA') {
                throw new Error('Esta venta ya ha sido cancelada anteriormente');
            }

            const vendedorId = ventaData.vendedorId;
            const clienteNombre = ventaData.clienteNombre;
            let almacenId = ventaData.almacenId;

            // 🔥 PLAN B: Si la venta no traía el almacén, buscamos siguiendo el rastro del perfil
            if (!almacenId) {
                console.log(`🔎 Buscando almacén de respaldo para el vendedor ${vendedorId}`);
                const userSnap = await transaction.get(db.collection('users').doc(vendedorId));

                if (userSnap.exists) {
                    const userData = userSnap.data();

                    if (userData.rutaAsignada) {
                        const rutaSnap = await transaction.get(userData.rutaAsignada);
                        if (rutaSnap.exists) {
                            const rutaData = rutaSnap.data();
                            // Intentamos obtener el ID del almacén de todas las formas posibles
                            almacenId = rutaData.almacenNombre ||
                                        (rutaData.almacenAsignado ? rutaData.almacenAsignado.id : null) ||
                                        rutaSnap.id;
                        }
                    }

                    // Si aún no hay, intentamos campo directo en usuario
                    if (!almacenId) almacenId = userData.ultimoAlmacenNombre;
                }
            }

            if (!almacenId) {
                throw new Error('La venta no tiene un almacén asociado y no se encontró uno en el perfil del vendedor');
            }

            // 1. Obtener los productos de la venta (subcolección)
            const productosSnap = await transaction.get(ventaRef.collection('productos'));

            if (productosSnap.empty) {
                console.warn(`⚠️ La venta ${ventaId} no tiene productos registrados.`);
            }

            const ahora = admin.firestore.FieldValue.serverTimestamp();

            // 2. Devolver stock e insertar movimientos
            productosSnap.forEach((doc) => {
                const p = doc.data();
                const productId = doc.id; // ID del producto base
                const cantidadVendida = p.cantidad || 0;

                // Referencia al stock del vendedor
                const stockRef = db.collection('inventarioStock').doc(`${productId}_${almacenId}`);

                // Actualizar stock (incrementar)
                transaction.update(stockRef, {
                    cantidad: admin.firestore.FieldValue.increment(cantidadVendida),
                    ultimaActualizacion: ahora
                });

                // Registrar movimiento de entrada por cancelación
                const movRef = db.collection('movimientosStock').doc();
                transaction.set(movRef, {
                    tipoMovimiento: 'ENTRADA_POR_CANCELACION',
                    productoRef: db.collection('producto').doc(productId),
                    productoNombre: p.nombre,
                    cantidad: cantidadVendida,
                    almacenRef: db.collection('almacenes').doc(almacenId),
                    almacenNombre: almacenId,
                    timestamp: ahora,
                    vendedorId: vendedorId,
                    adminId: adminUid || 'admin',
                    ventaId: ventaId,
                    motivo: motivo
                });
            });

            // 3. Marcar la venta como CANCELADA
            transaction.update(ventaRef, {
                estado: 'CANCELADA',
                motivoCancelacion: motivo,
                canceladoPorNombre: adminNombre || 'Administrador',
                canceladoPorUid: adminUid || '',
                fechaCancelacion: ahora,
                lastModified: Date.now()
            });

            return { vendedorId, total: ventaData.total, clienteNombre };
        });

        // 4. Notificar al vendedor (Fuera de la transacción para no retrasarla)
        try {
            const userSnap = await db.collection('users').doc(result.vendedorId).get();
            if (userSnap.exists) {
                const userData = userSnap.data();
                const tokens = userData.fcmTokens || [];

                if (tokens.length > 0) {
                    const payload = {
                        notification: {
                            title: '⚠️ Venta Cancelada',
                            body: `La venta a ${result.clienteNombre} por $${result.total} ha sido anulada por el administrador.`
                        },
                        data: {
                            tipo: 'VENTA_CANCELADA',
                            ventaId: ventaId,
                            click_action: 'FLUTTER_NOTIFICATION_CLICK'
                        }
                    };
                    await admin.messaging().sendEachForMulticast({ tokens, ...payload });
                }
            }
        } catch (err) {
            console.error('Error enviando notificación de cancelación:', err);
        }

        res.status(200).send({ success: true, message: 'Venta anulada y stock devuelto exitosamente' });

    } catch (error) {
        console.error('❌ Error anulando venta:', error);
        res.status(500).send({ success: false, error: error.message });
    }
});

module.exports = { anularVenta };
