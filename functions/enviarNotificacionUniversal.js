const functions = require("firebase-functions");
const admin = require("firebase-admin");

const enviarNotificacionUniversal = functions.https.onRequest(async (req, res) => {
    try {
        let {
            tokens,
            titulo = "Notificación",
            mensaje = "",
            imagen = "",
            ruta = "",
            velocidad = "",
            ventaId = "",
            estilo = "bigtext"
        } = req.body;

        // Normalizar tokens (puede ser uno solo o arreglo)
        if (!tokens) {
            return res.status(400).send("Falta 'tokens'");
        }

        if (typeof tokens === "string") {
            tokens = [tokens];
        }

        if (!Array.isArray(tokens) || tokens.length === 0) {
            return res.status(400).send("Tokens inválidos");
        }

        // 🔥 Construcción del mensaje multilínea
        let mensajeFinal = mensaje;

        if (ruta) mensajeFinal += `\n🛣 Ruta: ${ruta}`;
        if (velocidad) mensajeFinal += `\n⚡ Velocidad: ${velocidad} km/h`;

        console.log("📨 Enviando notificación multilínea:");
        console.log(mensajeFinal);

        // 🔥 Creamos los mensajes para cada token
        const messages = tokens.map((token) => ({
            token,

            // NO usamos "notification", para evitar recorte en Android
            android: {
                priority: "high",
                notification: {
                    channelId: "default_channel",
                    priority: "high",
                    ...(imagen ? { imageUrl: imagen } : {}),
                },
            },

            // Todo via DATA para BigTextStyle
            data: {
                titulo,
                mensaje: mensajeFinal,
                estilo,
                ...(imagen ? { imagen } : {}),
                ...(ventaId ? { ventaId: String(ventaId) } : {}),
                click_action: "FLUTTER_NOTIFICATION_CLICK",
            }
        }));

        // Enviar todas
        const responses = await admin.messaging().sendAll(messages);

        console.log(`✔️ Enviados: ${responses.successCount}`);
        console.log(`❌ Fallas: ${responses.failureCount}`);

        res.status(200).json({
            success: true,
            enviados: responses.successCount,
            fallas: responses.failureCount
        });

    } catch (error) {
        console.error("🔥 Error enviando notificación universal:", error);
        res.status(500).send(error.message);
    }
});

module.exports = { enviarNotificacionUniversal };
