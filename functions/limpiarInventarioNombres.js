const { onRequest } = require('firebase-functions/v2/https');
const admin = require('firebase-admin');
const db = admin.firestore();

/**
 * Cloud Function HTTP para normalizar todos los nombres y precios en inventarioStock (2da Gen)
 * basándose en el catálogo maestro de productos.
 */
exports.normalizarNombresInventario = onRequest({ timeoutSeconds: 540, memory: '256MiB' }, async (req, res) => {
    try {
        console.log('🚀 Iniciando normalización global de inventario...');

        // 1. Obtener todo el catálogo maestro
        const productosSnap = await db.collection('producto').get();
        const catalogo = {};
        productosSnap.forEach(doc => {
            catalogo[doc.id] = doc.data();
        });

        // 2. Obtener todo el inventario de stock
        const stockSnap = await db.collection('inventarioStock').get();
        let actualizados = 0;

        // Procesar en batches de 500 (límite de Firestore)
        let batch = db.batch();
        let batchCount = 0;

        for (const stockDoc of stockSnap.docs) {
            const stockData = stockDoc.data();
            const productId = stockData.productoId;
            const productoMaestro = catalogo[productId];

            if (productoMaestro) {
                // Verificar si hay discrepancia
                if (stockData.productoNombre !== productoMaestro.nombre ||
                    stockData.precioUnitario !== productoMaestro.precio) {

                    batch.update(stockDoc.ref, {
                        productoNombre: productoMaestro.nombre,
                        precioUnitario: productoMaestro.precio,
                        ultimaActualizacion: admin.firestore.FieldValue.serverTimestamp()
                    });

                    actualizados++;
                    batchCount++;

                    // Si llegamos al límite del batch, commitear y empezar uno nuevo
                    if (batchCount >= 400) {
                        await batch.commit();
                        batch = db.batch();
                        batchCount = 0;
                    }
                }
            } else {
                console.warn(`⚠️ Stock encontrado para producto inexistente en catálogo: ${productId}`);
            }
        }

        if (batchCount > 0) {
            await batch.commit();
        }

        console.log(`✅ Normalización completada. ${actualizados} registros actualizados.`);
        res.status(200).send({
            success: true,
            message: `Inventario normalizado. Se corrigieron ${actualizados} registros.`,
            totalStockDocs: stockSnap.size
        });

    } catch (error) {
        console.error('❌ Error en normalización:', error);
        res.status(500).send({ success: false, error: error.message });
    }
});
