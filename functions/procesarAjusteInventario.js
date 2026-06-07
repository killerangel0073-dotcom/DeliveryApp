const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require('firebase-admin');
const db = admin.firestore();

/**
 * Cloud Function para procesar ajustes de inventario (Cambios y Devoluciones)
 * Trigger: onCreate en ajustes_inventario/{ajusteId}
 */
exports.procesarAjusteInventario = onDocumentCreated('ajustes_inventario/{ajusteId}', async (event) => {
    const data = event.data.data();
    const ajusteId = event.params.ajusteId;

    if (!data) {
        console.error('⚠️ No hay datos en el evento:', ajusteId);
        return null;
    }

    const { productoId, cantidad, tipo, almacenNombre, vendedorId, nombreProducto } = data;

    if (!productoId || !cantidad || !tipo || !almacenNombre) {
      console.error('⚠️ Datos incompletos en ajuste:', ajusteId);
      return null;
    }

    const stockId = `${productoId}_${almacenNombre}`;
    const stockRef = db.collection('inventarioStock').doc(stockId);
    const danadoRef = db.collection('inventarioDanado').doc(stockId);
    const movRef = db.collection('movimientosStock').doc(ajusteId); // 🛡️ Idempotencia: usamos el ID del ajuste

    try {
      await db.runTransaction(async (transaction) => {
        const stockSnap = await transaction.get(stockRef);
        const danadoSnap = await transaction.get(danadoRef);

        let cantidadActual = 0;
        if (stockSnap.exists) {
          cantidadActual = stockSnap.data().cantidad || 0;
        }

        let cantidadDanadoActual = 0;
        if (danadoSnap.exists) {
          cantidadDanadoActual = danadoSnap.data().cantidad || 0;
        }

        let nuevaCantidad = cantidadActual;
        let nuevaCantidadDanado = cantidadDanadoActual;
        let actualizaStockBueno = false;
        let actualizaStockDanado = false;

        switch (tipo) {
          case 'ENTRADA_CAMBIO_BUENO':
            nuevaCantidad = cantidadActual + cantidad;
            actualizaStockBueno = true;
            break;
          case 'SALIDA_CAMBIO_BUENO':
          case 'SALIDA_REPOSICION_BUENO':
            nuevaCantidad = cantidadActual - cantidad;
            // 🛡️ Validación de Stock Insuficiente
            if (nuevaCantidad < 0) {
              throw new Error(`Stock insuficiente en ${almacenNombre} para ${nombreProducto || productoId}. Disponible: ${cantidadActual}`);
            }
            actualizaStockBueno = true;
            break;
          case 'ENTRADA_MALO_DEVOLUCION':
            nuevaCantidadDanado = cantidadDanadoActual + cantidad;
            actualizaStockDanado = true;
            break;
        }

        // 1. Actualizar Stock Bueno (si aplica)
        if (actualizaStockBueno) {
          transaction.set(stockRef, {
            cantidad: nuevaCantidad,
            ultimaActualizacion: admin.firestore.FieldValue.serverTimestamp(),
            almacenNombre: almacenNombre,
            productoId: productoId,
            productoNombre: nombreProducto || 'Producto'
          }, { merge: true });
        }

        // 2. Actualizar Stock Dañado (si aplica)
        if (actualizaStockDanado) {
          transaction.set(danadoRef, {
            cantidad: nuevaCantidadDanado,
            ultimaActualizacion: admin.firestore.FieldValue.serverTimestamp(),
            almacenNombre: almacenNombre,
            productoId: productoId,
            productoNombre: nombreProducto || 'Producto'
          }, { merge: true });
        }

        // 3. Registrar Movimiento General (Historial)
        transaction.set(movRef, {
          tipoMovimiento: tipo,
          productoId: productoId,
          productoNombre: nombreProducto || 'Producto',
          cantidad: cantidad,
          almacenNombre: almacenNombre,
          vendedorId: vendedorId,
          timestamp: admin.firestore.FieldValue.serverTimestamp(),
          referenciaId: data.referenciaId || ajusteId,
          idempotenciaKey: ajusteId
        });
      });

      console.log(`✅ Ajuste procesado exitosamente: ${ajusteId} (${tipo})`);
    } catch (error) {
      console.error(`❌ Error procesando ajuste ${ajusteId}:`, error.message);
    }
    return null;
  });
