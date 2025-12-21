const functions = require('firebase-functions');
const admin = require('firebase-admin');
const db = admin.firestore();

/**
 * FUNCIÓN UNIVERSAL DE NOTIFICACIONES
 * Acepta:
 *  - token
 *  - titulo
 *  - mensaje
 *  - imagen (opcional)
 *  - estilo (normal | bigtext)
 *  - data extra (opcional)
 *  - ventaId (opcional)
 */
const enviarNotificacion = functions.https.onRequest(async (req, res) => {
  try {
    const { token, titulo, mensaje, imagen, estilo, ventaId, dataExtra } = req.body;

    if (!token || !titulo || !mensaje) {
      return res.status(400).send("Faltan datos: token, titulo o mensaje");
    }

    // -----------------------------
    // DEFINIR NOTIFICATION.STYLE
    // -----------------------------
    let androidNotificationOptions = {
      channelId: "default_channel",
      defaultSound: true,
      priority: "high",
    };

    // Si trae imagen agregarla
    if (imagen) {
      androidNotificationOptions.imageUrl = imagen;
    }

    // Si trae estilo = bigtext → habilitar bigPicture / bigText según Firebase
    let notificationPayload = {
      title: titulo,
      body: mensaje,
    };

    if (imagen) {
      notificationPayload.imageUrl = imagen;
    }

    // -----------------------------
    // CONSTRUIR DATA
    // -----------------------------
    const dataPayload = {
      titulo,
      mensaje,
      click_action: "OPEN_TICKET_DETAIL",
      estilo: estilo || "normal",
      ...(ventaId ? { ventaId: String(ventaId) } : {}),
      ...(imagen ? { imagen } : {}),
      ...(dataExtra ? dataExtra : {}),
    };

    // -----------------------------
    // MENSAJE FINAL UNIVERSAL
    // -----------------------------
    const message = {
      token,
      notification: notificationPayload,
      android: {
        priority: "high",
        notification: androidNotificationOptions,
      },
      data: dataPayload,
    };

    // ENVIAR
    const response = await admin.messaging().send(message);
    console.log("📨 Notificación enviada:", response);

    res.status(200).send(`Notificación Universal enviada correctamente: ${response}`);
  } catch (error) {
    console.error("❌ Error enviando notificación universal:", error);
    res.status(500).send(`Error enviando notificación universal: ${error}`);
  }
});

module.exports = { enviarNotificacion };
