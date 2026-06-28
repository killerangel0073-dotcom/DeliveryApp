const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require('firebase-admin');
const db = admin.firestore();

/**
 * Cloud Function para procesar ajustes de inventario (Cambios, Devoluciones y Cargas Manuales)
 * Trigger: onCreate en ajustes_inventario/{ajusteId}
 */
exports.procesarAjusteInventario = onDocumentCreated('ajustes_inventario/{ajusteId}', async (event) => {
    const data = event.data.data();
    const ajusteId = event.params.ajusteId;

    if (!data) return null;

    const { productoId, cantidad, tipo, almacenNombre, vendedorId, nombreProducto, referenciaId } = data;

    if (!productoId || !cantidad || !tipo || !almacenNombre) {
      console.error('⚠️ Datos incompletos en ajuste:', ajusteId);
      return null;
    }

    const stockId = `${productoId}_${almacenNombre}`;
    const stockRef = db.collection('inventarioStock').doc(stockId);
    const danadoRef = db.collection('inventarioDanado').doc(stockId);

    // 🛡️ UNIFICACIÓN: Si es una carga manual, creamos o actualizamos el registro de "Orden" para el historial oficial
    if (tipo === 'CARGA_INVENTARIO' && referenciaId && referenciaId.startsWith('DIRECT_LOAD')) {
        try {
            const ordenRef = db.collection('ordenesTransferencia').doc(referenciaId);
            const productoData = {
                productoId: productoId,
                nombre: nombreProducto || "Producto",
                cantidad: cantidad
            };

            // Usamos .set con { merge: true } y FieldValue.arrayUnion
            // Esto permite que si la carga manual tiene 10 productos, se vayan sumando todos
            // al mismo documento de historial en lugar de solo registrar el primero.
            await ordenRef.set({
                tipo: "TRANSFERENCIA_VENDEDOR",
                origen: "CARGA MANUAL (OFFLINE)",
                destino: almacenNombre,
                estado: "ACEPTADA",
                vendedorId: vendedorId,
                timestamp: admin.firestore.FieldValue.serverTimestamp(),
                productos: admin.firestore.FieldValue.arrayUnion(productoData),
                esEmergencia: true
            }, { merge: true });

            console.log(`✅ Registro de orden actualizado/creado para carga manual: ${referenciaId}`);
        } catch (e) {
            console.error("Error gestionando orden espejo manual:", e.message);
        }
    }

    try {
      await db.runTransaction(async (transaction) => {
        const stockSnap = await transaction.get(stockRef);
        const danadoSnap = await transaction.get(danadoRef);

        let cantidadActual = stockSnap.exists ? (stockSnap.data().cantidad || 0) : 0;
        let cantidadDanadoActual = danadoSnap.exists ? (danadoSnap.data().cantidad || 0) : 0;

        let nuevaCantidad = cantidadActual;
        let nuevaCantidadDanado = cantidadDanadoActual;
        let actualizaStockBueno = false;
        let actualizaStockDanado = false;

        switch (tipo) {
          case 'CARGA_INVENTARIO':
          case 'ENTRADA_CAMBIO_BUENO':
          case 'AJUSTE_ARQUEO_SOBRANTE': // 🔥 Nueva lógica: Suma
            nuevaCantidad = cantidadActual + cantidad;
            actualizaStockBueno = true;
            break;
          case 'AJUSTE_ARQUEO_FALTANTE': // 🔥 Nueva lógica: Resta
            nuevaCantidad = Math.max(0, cantidadActual - cantidad);
            actualizaStockBueno = true;
            break;
          case 'SALIDA_CAMBIO_BUENO':
          case 'SALIDA_REPOSICION_BUENO':
            nuevaCantidad = Math.max(0, cantidadActual - cantidad);
            actualizaStockBueno = true;
            break;
          case 'ENTRADA_MALO_DEVOLUCION':
            nuevaCantidadDanado = cantidadDanadoActual + cantidad;
            actualizaStockDanado = true;
            break;
        }

        if (actualizaStockBueno) {
          transaction.set(stockRef, {
            cantidad: nuevaCantidad,
            ultimaActualizacion: admin.firestore.FieldValue.serverTimestamp(),
            almacenNombre: almacenNombre,
            productoId: productoId,
            productoNombre: nombreProducto || 'Producto'
          }, { merge: true });
        }

        if (actualizaStockDanado) {
          transaction.set(danadoRef, {
            cantidad: nuevaCantidadDanado,
            ultimaActualizacion: admin.firestore.FieldValue.serverTimestamp(),
            almacenNombre: almacenNombre,
            productoId: productoId,
            productoNombre: nombreProducto || 'Producto'
          }, { merge: true });
        }
      });

      console.log(`✅ Inventario actualizado para: ${stockId} (${tipo})`);
    } catch (error) {
      console.error(`❌ Error en transacción:`, error.message);
    }
    return null;
  });
