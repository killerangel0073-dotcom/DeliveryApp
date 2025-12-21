const { onDocumentUpdated } = require('firebase-functions/v2/firestore');
const admin = require('firebase-admin');
const { ejecutarTransferenciaConDetalle } = require('./ejecutarTransferenciaConDetalle');

const onOrdenTransferenciaAceptadaConDetalle = onDocumentUpdated(
  'ordenesTransferencia/{ordenId}',
  async (event) => {
    const beforeData = event.data.before?.data() || null;
    const afterData = event.data.after?.data() || null;
    if (!afterData) return;

    if (
      (!beforeData || beforeData.estado !== 'ACEPTADA') &&
      afterData.estado === 'ACEPTADA'
    ) {
      const ordenId = event.params.ordenId;

      try {
        await ejecutarTransferenciaConDetalle(
          admin.firestore(),
          ordenId,
          afterData
        );
        console.log(`✅ Orden ${ordenId} procesada`);


      }

     catch (error) {
       console.error(`❌ Error en orden ${ordenId}:`, error);

       await admin.firestore()
         .collection('ordenesTransferencia')
         .doc(ordenId)
         .update({
           estado: "ERROR",
           transferenciaError: true,
           transferenciaErrorMensaje: error.message,
           transferenciaErrorAt: admin.firestore.FieldValue.serverTimestamp()
         });
     }



    }
  }
);

module.exports = { onOrdenTransferenciaAceptadaConDetalle };
