const { onDocumentUpdated } = require('firebase-functions/v2/firestore');
const admin = require('firebase-admin');
const { ejecutarTransferenciaConDetalle } = require('./ejecutarTransferenciaConDetalle');

const onOrdenTransferenciaAceptadaConDetalle = onDocumentUpdated(
  'ordenesTransferencia/{ordenId}',
  async (event) => {
    const beforeData = event.data.before?.data() || null;
    const afterData = event.data.after?.data() || null;
    if (!afterData) return;

    if ((!beforeData || beforeData.estado !== 'ACEPTADA') && afterData.estado === 'ACEPTADA') {
      const ordenId = event.params.ordenId;
      try {
        await ejecutarTransferenciaConDetalle(admin.firestore(), ordenId, afterData);
        console.log(`✅ Transferencia detallada de orden ${ordenId} completada`);
      } catch (error) {
        console.error(`❌ Error procesando orden ${ordenId}:`, error);
        try {
          await admin.firestore().collection('ordenesTransferencia').doc(ordenId).update({
            transferenciaError: true,
            transferenciaErrorMensaje: error.message,
            transferenciaErrorTimestamp: admin.firestore.FieldValue.serverTimestamp()
          });
        } catch (e) {
          console.error('No se pudo marcar la orden con el error:', e);
        }
      }
    }
  }
);

module.exports = { onOrdenTransferenciaAceptadaConDetalle };
