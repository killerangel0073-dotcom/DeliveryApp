const admin = require('firebase-admin');

async function ejecutarTransferenciaConDetalle(db, ordenId, ordenData) {
  console.log('🚚 Ejecutando transferencia:', ordenId);

  // 🔐 BLOQUEO ANTI-DOBLE EJECUCIÓN
  if (ordenData.transferenciaEjecutada) {
    console.log('⚠️ Orden ya ejecutada:', ordenId);
    return;
  }

  if (!ordenData.productos || ordenData.productos.length === 0) {
    throw new Error('La orden no tiene productos');
  }

  const batch = db.batch();
  const ahora = admin.firestore.FieldValue.serverTimestamp();

  // 🔁 RECORRER PRODUCTOS
  for (const p of ordenData.productos) {
    const productoId = p.productoId;
    const cantidad = Number(p.cantidad);

    if (!productoId || cantidad <= 0) {
      throw new Error(`Producto inválido en orden ${ordenId}`);
    }

    // 🔻 ORIGEN
    const stockOrigenRef = db
      .collection('inventarioStock')
      .doc(`${productoId}_${ordenData.origen}`);

    // 🔺 DESTINO
    const stockDestinoRef = db
      .collection('inventarioStock')
      .doc(`${productoId}_${ordenData.destino}`);

    const [origenSnap, destinoSnap] = await Promise.all([
      stockOrigenRef.get(),
      stockDestinoRef.get()
    ]);

    if (ordenData.origen !== 'Compra Producto') {
      if (!origenSnap.exists) {
        throw new Error(`Stock origen no existe (${productoId})`);
      }

      const stockActual = origenSnap.get('cantidad') || 0;
      if (stockActual < cantidad) {
        throw new Error(`Stock insuficiente en origen (${productoId})`);
      }

      batch.update(stockOrigenRef, {
        cantidad: stockActual - cantidad,
        ultimaActualizacion: ahora
      });
    }





    // 🔺 DESTINO (FORMA CORRECTA Y CONSISTENTE)
    if (destinoSnap.exists) {
      const stockDestino = destinoSnap.get('cantidad') || 0;

      batch.update(stockDestinoRef, {
        cantidad: stockDestino + cantidad,
        ultimaActualizacion: ahora
      });

    } else {
      batch.set(stockDestinoRef, {
        // 🔑 IDENTIDAD
        productoRef: db.collection('producto').doc(productoId),
        productoId: productoId,

        // 🔑 INFO PRODUCTO
        productoNombre: p.nombre ?? null,
        precioUnitario: Number(p.precioUnitario ?? p.precio ?? 0),

        // 🔑 INFO ALMACÉN
        almacenNombre: ordenData.destino,
        almacenRef: db.collection('almacenes').doc(ordenData.destino),

        // 🔑 STOCK
        cantidad: cantidad,
        ultimaActualizacion: ahora
      });
    }






    // 📦 MOVIMIENTO
    const tipoMovimiento =
      ordenData.tipo === 'COMPRA'
        ? 'COMPRA'
        : ordenData.destino.startsWith('Vendedor')
          ? 'TRANSFERENCIA_VENDEDOR'
          : 'TRANSFERENCIA_INTERNA';



    batch.set(db.collection('movimientosStock').doc(), {
      tipoMovimiento,
      productoId,
      cantidad,
      nombreProducto: p.nombre ?? null,
      origen: ordenData.origen,
      destino: ordenData.destino,
      ordenId,
      timestamp: ahora


    });


  } // ✅ AQUÍ CIERRA EL FOR

  // ✅ ACTUALIZAR ORDEN (UNA SOLA VEZ)
  batch.update(db.collection('ordenesTransferencia').doc(ordenId), {
    estado: 'COMPLETADA',
    transferenciaEjecutada: true,
    transferenciaEjecutadaAt: ahora
  });

  // ✅ COMMIT ÚNICO
  await batch.commit();
  console.log('✅ Transferencia completada:', ordenId);
}

module.exports = {
  ejecutarTransferenciaConDetalle
};
