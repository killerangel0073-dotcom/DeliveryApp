const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onRequest } = require("firebase-functions/v2/https");
const admin = require('firebase-admin');

/**
 * LÓGICA CENTRALIZADA
 */
async function procesarActualizacionValorClientes() {
    const db = admin.firestore();
    const hoy = new Date();
    // 30 días atrás
    const hace30Dias = new Date();
    hace30Dias.setDate(hoy.getDate() - 30);
    const hace30DiasTimestamp = admin.firestore.Timestamp.fromDate(hace30Dias);

    console.log("Iniciando proceso de actualización de valor de clientes...");

    // 1. Obtener la configuración de umbrales
    const configDoc = await db.collection('config').doc('valor_clientes').get();
    if (!configDoc.exists) {
        throw new Error("No se encontró el documento 'config/valor_clientes'");
    }

    const { alto, medio, bajo } = configDoc.data();
    console.log(`Umbrales: Alto >= ${alto}, Medio >= ${medio}, Bajo < ${medio}`);

    // 2. Obtener todos los clientes activos
    const clientesSnap = await db.collection('clientes').where('activo', '==', true).get();

    const batchSize = 400;
    let batch = db.batch();
    let count = 0;
    let totalActualizados = 0;

    for (const clienteDoc of clientesSnap.docs) {
        const clienteId = clienteDoc.id;
        const dataCliente = clienteDoc.data();

        // 3. Sumar ventas pagadas de los últimos 30 días
        const ventasSnap = await db.collection('ventas')
            .where('clienteId', '==', clienteId)
            .where('estado', '==', 'pagada')
            .where('fecha', '>=', hace30DiasTimestamp)
            .get();

        let totalVentas = 0;
        ventasSnap.forEach(v => {
            totalVentas += (v.data().total || 0);
        });

        // 4. Determinar nuevo valor
        let nuevoValor = "bajo";
        if (totalVentas >= alto) {
            nuevoValor = "alto";
        } else if (totalVentas >= medio) {
            nuevoValor = "medio";
        }

        // 5. Actualizar si hay cambios
        if (dataCliente.valorCliente !== nuevoValor) {
            batch.update(clienteDoc.ref, {
                valorCliente: nuevoValor,
                lastModified: Date.now()
            });
            count++;
            totalActualizados++;

            if (count >= batchSize) {
                await batch.commit();
                batch = db.batch();
                count = 0;
            }
        }
    }

    if (count > 0) {
        await batch.commit();
    }

    return totalActualizados;
}

/**
 * 1. TAREA AUTOMÁTICA (1:00 AM) - Sintaxis v2
 */
exports.actualizarValorClientes = onSchedule({
    schedule: "0 1 * * *",
    timeZone: "America/Mexico_City",
    memory: "256MiB"
}, async (event) => {
    try {
        const total = await procesarActualizacionValorClientes();
        console.log(`Tarea programada exitosa. Clientes actualizados: ${total}`);
    } catch (error) {
        console.error("Error en tarea programada:", error);
    }
});
