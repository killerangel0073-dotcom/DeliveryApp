const functions = require('firebase-functions');
const admin = require('firebase-admin'); // NO initializeApp aquí
const db = admin.firestore();
/**
 * Cloud Function HTTP para registrar una venta completa en Firestore.
 *
 * Request body (JSON):
 * {
 *   "ventaLocalId": number,
 *   "clienteId": string,
 *   "clienteNombre": string,
 *   "productos": [{ id: string, nombre: string, precio: number, cantidad: number, imagenUrl?: string }],
 *   "metodoPago": string,
 *   "vendedorId": string,
 *   "almacenVendedorId": string
 * }
 */
exports.registrarVenta = functions.https.onRequest(async (req, res) => {
  try {
    if (req.method !== 'POST') {
      return res.status(405).send({ error: 'Método no permitido' });
    }

    const {
      ventaLocalId,
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
    const ventaRef = db.collection('ventas').doc();
    console.log(`🚀 Iniciando transacción ventaId=${ventaRef.id}`);

    const ventaId = await db.runTransaction(async (transaction) => {
      const ahora = admin.firestore.FieldValue.serverTimestamp();

      // 🔹 Leer todos los stocks
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
          console.error(`❌ Stock no existe para ${r.producto.nombre} en ${almacenIdLimpio}`);
          throw new Error(`Stock no existe para ${r.producto.nombre} en el almacén del vendedor`);
        }
        const stockActual = r.stockSnap.data().cantidad || 0;
        if (stockActual < r.producto.cantidad) {
          console.error(`⚠️ Stock insuficiente para ${r.producto.nombre}: actual=${stockActual}, requerido=${r.producto.cantidad}`);
          throw new Error(`Stock insuficiente para ${r.producto.nombre}`);
        }
        console.log(`🔍 Stock ok para ${r.producto.nombre}: ${stockActual} disponibles`);
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
        comentarios: ''
      });
      console.log(`✅ Venta creada en ${ventaRef.id}`);

      // 🔹 Agregar productos y actualizar stock
      resultados.forEach(r => {
        const { producto, productIdLimpio, stockRef, productoRef } = r;

        transaction.set(ventaRef.collection('productos').doc(productIdLimpio), {
          nombre: producto.nombre,
          precio: producto.precio,
          cantidad: producto.cantidad,
          imagenUrl: producto.imagenUrl || ''
        });
        console.log(`📦 Producto agregado a venta: ${producto.nombre} x${producto.cantidad}`);

        const stockActual = r.stockSnap.data().cantidad;
        transaction.update(stockRef, {
          cantidad: stockActual - producto.cantidad,
          ultimaActualizacion: ahora
        });
        console.log(`✅ Stock actualizado: ${producto.nombre} ${stockActual} → ${stockActual - producto.cantidad}`);

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
        console.log(`📝 Movimiento creado para ${producto.nombre}`);
      });

      return ventaRef.id;
    });

    console.log(`🎉 Transacción completada con éxito, ventaId=${ventaId}`);
    res.status(200).send({ success: true, ventaId });

  } catch (error) {
    console.error('❌ Error registrando venta:', error);
    res.status(500).send({ success: false, error: error.message });
  }
});
