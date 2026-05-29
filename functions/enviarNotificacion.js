const functions = require('firebase-functions');
const admin = require('firebase-admin');

/**
 * FUNCIÓN DE NOTIFICACIONES UNIVERSAL Y ROBUSTA
 * Soporta:
 *  - token (String) o tokens (Array)
 *  - imagen (URL para miniatura y bigPicture)
 *  - ventaId (Data extra)
 */
const enviarNotificacion = functions.https.onRequest(async (req, res) => {
  try {
    const { token, tokens, titulo, mensaje, imagen, ventaId } = req.body;

    // 1. Normalizar destinatarios
    let targets = [];
    if (token) targets.push(token);
    if (tokens && Array.isArray(tokens)) targets = targets.concat(tokens);

    // Limpiar y quitar duplicados
    targets = [...new Set(targets)].filter(t => t && typeof t === 'string');

    if (targets.length === 0 || !titulo || !mensaje) {
      console.warn("⚠️ Solicitud rechazada: Faltan tokens, título o mensaje", req.body);
      return res.status(400).send("Faltan datos críticos (tokens, titulo o mensaje)");
    }

    // 2. Construir el paquete de notificación estándar
    const notification = {
      title: titulo,
      body: mensaje,
    };
    if (imagen) {
      notification.imageUrl = imagen;
    }

    // 3. Datos extra (Compatibilidad con el sistema de navegación de la App)
    const data = {
      titulo: titulo,
      mensaje: mensaje,
      imagen: imagen || "",
      ventaId: ventaId ? String(ventaId) : "",
      click_action: "OPEN_TICKET_DETAIL"
    };

    // 4. Opciones específicas de Android para que se vea Premium
    const android = {
      priority: "high",
      notification: {
        channelId: "default_channel",
        priority: "high",
        ...(imagen ? { imageUrl: imagen } : {})
      }
    };

    // 5. Envío según cantidad de destinos
    let response;
    if (targets.length === 1) {
      // Envío simple
      response = await admin.messaging().send({
        token: targets[0],
        notification,
        data,
        android
      });
      console.log("✅ Notificación individual enviada:", response);
    } else {
      // Envío múltiple eficiente
      response = await admin.messaging().sendEachForMulticast({
        tokens: targets,
        notification,
        data,
        android
      });
      console.log(`✅ Notificación masiva procesada: ${response.successCount} éxitos, ${response.failureCount} fallas`);
    }

    res.status(200).json({ success: true, response });

  } catch (error) {
    console.error("❌ Error crítico en Cloud Function:", error);
    res.status(500).send(`Error interno: ${error.message}`);
  }
});

module.exports = { enviarNotificacion };
