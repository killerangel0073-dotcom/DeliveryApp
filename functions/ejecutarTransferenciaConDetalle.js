const functions = require('firebase-functions');
const admin = require('firebase-admin'); // NO initializeApp aquí
const db = admin.firestore();


/**
 * POST /registrarVenta
 * Body JSON:
 * {
 *   "ventaLocalId": number | string,
 *   "clienteId": string,
 *   "clienteNombre": string,
 *   "productos": [{ id: string, nombre: string, precio: number, cantidad: number, imagenUrl?: string }],
 *   "metodoPago": string,
 *   "vendedorId": string,
 *   "almacenVendedorId": string
 * }
 *
 * Optional protection:
 * - If you set functions config `ventas.api_key`, the client must send header `x-api-key: <that_key>`.
 * - If you don't set it, the function accepts unauthenticated calls (NOT recommended for prod).
 */
exports.registrarVenta = functions.https.onRequest(async (req, res) => {
  try {
    // CORS preflight simple handling (si llamas desde web/mobile nativo puede no ser necesario)
    if (req.method === 'OPTIONS') {
      res.set('Access-Control-Allow-Origin', '*');
      res.set('Access-Control-Allow-Methods', 'POST, OPTIONS');
      res.set('Access-Control-Allow-Headers', 'Content-Type, x-api-key');
      return res.status(204).send('');
    }
    res.set('Access-Control-Allow-Origin', '*');

    if (req.method !== 'POST') {
      return res.status(405).json({ success: false, error: 'Método no permitido. Use POST.' });
    }

    // --- Opcional: API Key protection (set via `firebase functions:config:set ventas.api_key="mi_clave"`) ---
    const configApiKey = functions.config && functions.config().ventas && functions.config().ventas.api_key;
    if (configApiKey) {
      const providedKey = req.get('x-api-key') || req.get('X-API-KEY') || '';
      if (!providedKey || providedKey !== configApiKey) {
        console.warn('Acceso denegado: API key inválida o ausente');
        return res.status(401).json({ success: false, error: 'API key inválida o ausente' });
      }
    }
    // Si no hay API key configurada, permitimos la llamada (NO RECOMENDADO) — el usuario pidió "sin auth"

    const {
      ventaLocalId,
      clienteId,
      clienteNombre,
      productos,
      metodoPago,
      vendedorId,
      almacenVendedorId
    } = req.body || {};

    // Validaciones básicas
    if (!ventaLocalId || !clienteId || !clienteNombre || !Array.isArray(productos) || productos.length === 0 || !metodoPago || !vendedorId || !almacenVendedorId) {
      return res.status(400).json({ success: false, error: 'Datos de venta incompletos' });
    }

    // Normalizaciones
    const almacenIdLimpio = String(almacenVendedorId).trim();
    const vendedorIdLimpio = String(vendedorId).trim();
    const safeVentaLocalId = String(ventaLocalId);
    // Idempotencia: generar ID determinista para evitar duplicados
    const ventaDocId = `${vendedorIdLimpio}_${safeVentaLocalId}`.replace(/\s+/g, '_');

    // Agregar/normalizar productos (agrupar por productId limpio)
    const productosMap = new Map();
    for (const p of productos) {
      if (!p || !p.id) {
        return res.status(400).json({ success: false, error: 'Producto con id inválido' });
      }
      const idLimpio = String(p.id).split('_')[0].trim();
      const cantidad = Number(p.cantidad) || 0;
      const precio = Number(p.precio) || 0;
      if (cantidad <= 0) {
        return res.status(400).json({ success: false, error: `Cantidad inválida para ${p.nombre || idLimpio}` });
      }
      if (precio < 0) {
        return res.status(400).json({ success: false, error: `Precio inválido para ${p.nombre || idLimpio}` });
      }

      if (productosMap.has(idLimpio)) {
        const ex = productosMap.get(idLimpio);
        ex.cantidad += cantidad;
        // we keep precio as provided (could average or decide business rule)
      } else {
        productosMap.set(idLimpio, {
          id: idLimpio,
          nombre: p.nombre || null,
          precio,
          cantidad,
          imagenUrl: p.imagenUrl || ''
        });
      }
    }

    const productosAgrupados = Array.from(productosMap.values());
    if (productosAgrupados.length === 0) {
      return res.status(400).json({ success: false, error: 'No hay productos válidos' });
    }

    const ventaRef = db.collection('ventas').doc(ventaDocId);
    console.log(`Solicitud de venta. ventaDocId=${ventaDocId} vendedor=${vendedorIdLimpio} almacen=${almacenIdLimpio}`);

    // Run transaction
    const ventaId = await db.runTransaction(async (transaction) => {
      const ahora = admin.firestore.FieldValue.serverTimestamp();

      // 1) Lectura: si ya existe esta venta (idempotencia)
      const ventaSnap = await transaction.get(ventaRef);
      if (ventaSnap.exists) {
        console.log(`Venta ya existe (idempotencia) ventaId=${ventaDocId}`);
        return ventaDocId;
      }

      // 2) Preparar lecturas de stock y producto
      const reads = productosAgrupados.map(p => {
        const stockRef = db.collection('inventarioStock').doc(`${p.id}_${almacenIdLimpio}`);
        const productoRef = db.collection('producto').doc(p.id);
        return {
          producto: p,
          stockRef,
          productoRef,
          promStock: transaction.get(stockRef),
          promProducto: transaction.get(productoRef)
        };
      });

      const resultados = await Promise.all(
        reads.map(async r => ({
          ...r,
          stockSnap: await r.promStock,
          productoSnap: await r.promProducto
        }))
      );

      // 3) Validaciones
      for (const r of resultados) {
        const { producto, stockSnap, productoSnap } = r;
        if (!productoSnap || !productoSnap.exists) {
          const msg = `Producto no existe: ${producto.id}`;
          console.error(msg);
          throw new functions.https.HttpsError('failed-precondition', msg);
        }
        if (!stockSnap || !stockSnap.exists) {
          const msg = `Stock no existe para ${producto.nombre || producto.id} en ${almacenIdLimpio}`;
          console.error(msg);
          throw new functions.https.HttpsError('failed-precondition', msg);
        }
        const stockActual = Number(stockSnap.get('cantidad') || 0);
        if (stockActual < producto.cantidad) {
          const msg = `Stock insuficiente para ${producto.nombre || producto.id} (disponible=${stockActual}, requerido=${producto.cantidad})`;
          console.error(msg);
          throw new functions.https.HttpsError('failed-precondition', msg);
        }
      }

      // 4) Totales
      const total = productosAgrupados.reduce((acc, p) => acc + (p.precio * p.cantidad), 0);
      const totalPiezas = productosAgrupados.reduce((acc, p) => acc + p.cantidad, 0);

      // 5) Escrituras
      transaction.set(ventaRef, {
        clienteId: String(clienteId),
        clienteNombre: String(clienteNombre),
        localId: safeVentaLocalId,
        total,
        totalPiezas,
        fecha: ahora,
        metodoPago: String(metodoPago),
        vendedorId: vendedorIdLimpio,
        sincronizado: true,
        estado: 'pagada',
        comentarios: ''
      });

      for (const r of resultados) {
        const { producto, stockSnap, stockRef, productoRef } = r;
        transaction.set(ventaRef.collection('productos').doc(producto.id), {
          nombre: producto.nombre || null,
          precio: producto.precio,
          cantidad: producto.cantidad,
          imagenUrl: producto.imagenUrl || ''
        });

        const stockActual = Number(stockSnap.get('cantidad') || 0);
        transaction.update(stockRef, {
          cantidad: stockActual - producto.cantidad,
          ultimaActualizacion: ahora
        });

        transaction.set(db.collection('movimientosStock').doc(), {
          tipoMovimiento: 'VENTA',
          productoRef: productoRef,
          productoNombre: producto.nombre || null,
          precioUnitario: producto.precio,
          cantidad: producto.cantidad,
          almacenRef: db.collection('almacenes').doc(almacenIdLimpio),
          almacenNombre: almacenIdLimpio,
          timestamp: ahora,
          vendedorId: vendedorIdLimpio,
          clienteId: String(clienteId),
          ventaId: ventaDocId
        });
      }

      return ventaDocId;
    });

    console.log(`Transacción exitosa, ventaId=${ventaId}`);
    return res.status(200).json({ success: true, ventaId });

  } catch (error) {
    if (error instanceof functions.https.HttpsError) {
      console.error('HttpsError:', error);
      return res.status(400).json({ success: false, error: error.message });
    }
    console.error('Error registrando venta (unexpected):', error);
    return res.status(500).json({ success: false, error: error.message || 'Error interno del servidor' });
  }
});
