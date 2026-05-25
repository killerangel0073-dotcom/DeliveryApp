const functions = require('firebase-functions');
const admin = require('firebase-admin');
const db = admin.firestore();

/**
 * Cloud Function HTTP para registrar una venta completa en Firestore con Idempotencia.
 */
exports.registrarVenta = functions.https.onRequest(async (req, res) => {
  try {
    if (req.method !== 'POST') {
      return res.status(405).send({ error: 'Método no permitido' });
    }

    const {
      ventaLocalId, // Este ahora es el UUID generado por el móvil
      clienteId,
      clienteNombre,
      productos,
      metodoPago,
      vendedorId,
      almacenVendedorId
    } = req.body;

    if (!ventaLocalId || !clienteId || !clienteNombre || !Array.isArray(productos) || productos.length === 0 || !metodoPago || !vendedorId || !almacenVendedorId) {
      return res.status(400).send({ error: 'Datos de venta incompletos' });
    }

    const almacenIdLimpio = almacenVendedorId.trim();

    // 🔥 BLINDAJE DE IDEMPOTENCIA: Usamos el ID local como ID de documento en Firestore.
    // Si la función se reintenta con el mismo UUID, Firestore simplemente sobrescribirá (o fallará la transacción si el stock cambió),
    // pero JAMÁS creará un ticket duplicado con distinto ID.
    const ventaRef = db.collection('ventas').doc(ventaLocalId);
    console.log(`🚀 Iniciando registro de venta con ID Idempotente=${ventaLocalId}`);

    const ventaId = await db.runTransaction(async (transaction) => {
      // 1. Verificar si la venta ya existe (para no descontar stock dos veces)
      const ventaExistente = await transaction.get(ventaRef);
      if (ventaExistente.exists) {
        console.log(`⚠️ La venta ${ventaLocalId} ya fue registrada anteriormente. Retornando ID existente.`);
        return ventaLocalId;
      }

      const ahora = admin.firestore.FieldValue.serverTimestamp();

      // 🔹 Leer stocks y productos
      const lecturas = productos.map(p => {
        const productIdLimpio = p.id.split('_')[0];
        const stockRef = db.collection('inventarioStock').doc(`${productIdLimpio}_${almacenIdLimpio}`);
        const productoRef = db.collection('producto').doc(productIdLimpio);
        return { producto: p, productIdLimpio, stockRef, productoRef, promStock: transaction.get(stockRef), promProducto: transaction.get(productoRef) };
      });

      const resultados = await Promise.all(
        lecturas.map(async l => ({
          ...l,
          stockSnap: await l.promStock,
          productoSnap: await l.promProducto
        }))
      );

      // 🔹 Validar stocks
      resultados.forEach(r => {
        if (!r.stockSnap.exists) {
          throw new Error(`Stock no existe para ${r.producto.nombre}`);
        }
        const stockActual = r.stockSnap.data().cantidad || 0;
        if (stockActual < r.producto.cantidad) {
          throw new Error(`Stock insuficiente para ${r.producto.nombre}`);
        }
      });

      // 🔹 Crear venta
      const total = productos.reduce((acc, p) => acc + p.precio * p.cantidad, 0);
      const totalPiezas = productos.reduce((acc, p) => acc + p.cantidad, 0);

      transaction.set(ventaRef, {
        clienteId,
        clienteNombre,
        localId: ventaLocalId,
        total,
        totalPiezas,
        fecha: ahora,
        metodoPago,
        vendedorId,
        sincronizado: true,
        estado: 'pagada',
        comentarios: 'Registro Idempotente'
      });

      // 🔹 Agregar productos y actualizar stock
      resultados.forEach(r => {
        const { producto, productIdLimpio, stockRef, productoRef } = r;

        transaction.set(ventaRef.collection('productos').doc(productIdLimpio), {
          nombre: producto.nombre,
          precio: producto.precio,
          cantidad: producto.cantidad,
          imagenUrl: producto.imagenUrl || ''
        });

        const stockActual = r.stockSnap.data().cantidad;
        transaction.update(stockRef, {
          cantidad: stockActual - producto.cantidad,
          ultimaActualizacion: ahora
        });

        transaction.set(db.collection('movimientosStock').doc(), {
          tipoMovimiento: 'VENTA',
          productoRef,
          productoNombre: producto.nombre,
          precioUnitario: producto.precio,
          cantidad: producto.cantidad,
          almacenRef: db.collection('almacenes').doc(almacenIdLimpio),
          almacenNombre: almacenIdLimpio,
          timestamp: ahora,
          vendedorId,
          clienteId,
          ventaId: ventaRef.id
        });
      });

      return ventaRef.id;
    });

    res.status(200).send({ success: true, ventaId });

  } catch (error) {
    console.error('❌ Error registrando venta:', error);
    res.status(500).send({ success: false, error: error.message });
  }
});
