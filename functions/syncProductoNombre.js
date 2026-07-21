const { onDocumentUpdated } = require('firebase-functions/v2/firestore');
const admin = require('firebase-admin');
const db = admin.firestore();

/**
 * Trigger que se activa al actualizar un producto en el catálogo maestro (2da Gen).
 * Propaga el cambio de nombre y precio a todos los registros de inventarioStock.
 */
exports.onProductoUpdated = onDocumentUpdated('producto/{productId}', async (event) => {
    const newValue = event.data.after.data();
    const previousValue = event.data.before.data();

    const productId = event.params.productId;
    const nuevoNombre = newValue.nombre;
    const nuevoPrecio = newValue.precio;

    // Solo actuar si el nombre o el precio cambiaron
    if (nuevoNombre === previousValue.nombre && nuevoPrecio === previousValue.precio) {
        return null;
    }

    console.log(`🔄 Detectado cambio en producto ${productId}. Propagando a inventarioStock...`);

    try {
        // Buscar todos los registros de stock que pertenezcan a este producto
        const stockSnapshot = await db.collection('inventarioStock')
            .where('productoId', '==', productId)
            .get();

        if (stockSnapshot.empty) {
            console.log('ℹ️ No hay registros en inventarioStock para este producto.');
            return null;
        }

        const batch = db.batch();
        stockSnapshot.docs.forEach(doc => {
            batch.update(doc.ref, {
                productoNombre: nuevoNombre,
                precioUnitario: nuevoPrecio,
                ultimaActualizacion: admin.firestore.FieldValue.serverTimestamp()
            });
        });

        await batch.commit();
        console.log(`✅ Se actualizaron ${stockSnapshot.size} registros de stock para el producto: ${nuevoNombre}`);
        return true;

    } catch (error) {
        console.error('❌ Error propagando cambios de producto:', error);
        return null;
    }
});
