const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require('firebase-admin');

/**
 * Cloud Function para procesar ajustes de inventario (Cambios, Devoluciones y Arqueos)
 * Trigger: onCreate en ajustes_inventario/{ajusteId}
 */
exports.procesarAjusteInventario = onDocumentCreated('ajustes_inventario/{ajusteId}', async (event) => {
    const db = admin.firestore();
    const data = event.data.data();
    const ajusteId = event.params.ajusteId;

    if (!data) return null;

    const { productoId, cantidad, tipo, almacenNombre, vendedorId, nombreProducto, referenciaId, metodoAuditoria, cantidadFisica, almacenDestino } = data;

    if (!productoId || !tipo || !almacenNombre) {
      console.error('⚠️ Datos incompletos en ajuste:', ajusteId);
      return null;
    }

    const stockId = `${productoId}_${almacenNombre}`.trim();
    const stockRef = db.collection('inventarioStock').doc(stockId);

    console.log(`📦 Procesando ajuste para: ${stockId} | Tipo: ${tipo} | Método: ${metodoAuditoria}`);

    try {
      await db.runTransaction(async (transaction) => {
        const stockSnap = await transaction.get(stockRef);
        let cantidadActual = stockSnap.exists ? (stockSnap.data().cantidad || 0) : 0;
        let nuevaCantidad = cantidadActual;
        let actualizaStockBueno = false;

        // 1. LÓGICA DE LIQUIDACIÓN (Retorno total a bodega)
        if (metodoAuditoria === 'LIQUIDACION') {
            console.log(`🚚 Procesando LIQUIDACIÓN para ${stockId}. Origen -> 0.`);
            nuevaCantidad = 0; // El camión queda vacío
            actualizaStockBueno = true;

            // Sumar al almacén destino (Huasteca)
            const destino = almacenDestino || "Almacen Huasteca";
            const stockDestinoId = `${productoId}_${destino}`.trim();
            const stockDestinoRef = db.collection('inventarioStock').doc(stockDestinoId);

            // Usamos increment para que sea atómico en el destino
            transaction.set(stockDestinoRef, {
                productoId: productoId,
                productoNombre: nombreProducto || 'Producto',
                almacenNombre: destino,
                cantidad: admin.firestore.FieldValue.increment(cantidadFisica || 0),
                ultimaActualizacion: admin.firestore.FieldValue.serverTimestamp()
            }, { merge: true });

            console.log(`✅ Sumado ${cantidadFisica} a ${stockDestinoId}`);
        }
        // 2. LÓGICA DE ARQUEO O AJUSTES NORMALES
        else {
            switch (tipo) {
              case 'CARGA_INVENTARIO':
              case 'ENTRADA_CAMBIO_BUENO':
              case 'AJUSTE_ARQUEO_SOBRANTE':
                nuevaCantidad = cantidadActual + cantidad;
                actualizaStockBueno = true;
                break;
              case 'AJUSTE_ARQUEO_FALTANTE':
              case 'SALIDA_CAMBIO_BUENO':
              case 'SALIDA_REPOSICION_BUENO':
                nuevaCantidad = Math.max(0, cantidadActual - (cantidad || 0));
                actualizaStockBueno = true;
                break;
              case 'ENTRADA_MALO_DEVOLUCION':
                // Lógica de dañado (opcional, mantener si existe)
                const danadoRef = db.collection('inventarioDanado').doc(stockId);
                const danadoSnap = await transaction.get(danadoRef);
                const danadoActual = danadoSnap.exists ? (danadoSnap.data().cantidad || 0) : 0;
                transaction.set(danadoRef, {
                    cantidad: danadoActual + cantidad,
                    ultimaActualizacion: admin.firestore.FieldValue.serverTimestamp(),
                    almacenNombre: almacenNombre,
                    productoId: productoId,
                    productoNombre: nombreProducto || 'Producto'
                }, { merge: true });
                break;
            }
        }

        if (actualizaStockBueno) {
          transaction.set(stockRef, {
            cantidad: nuevaCantidad,
            ultimaActualizacion: admin.firestore.FieldValue.serverTimestamp(),
            almacenNombre: almacenNombre.trim(),
            productoId: productoId,
            productoNombre: nombreProducto || 'Producto',
            productoRef: db.collection('producto').doc(productoId),
            almacenRef: db.collection('almacenes').doc(almacenNombre.trim())
          }, { merge: true });
        }
      });

      console.log(`✅ Inventario ajustado exitosamente para: ${stockId}`);
    } catch (error) {
      console.error(`❌ Error en transacción de ajuste:`, error.message);
    }
    return null;
  });
