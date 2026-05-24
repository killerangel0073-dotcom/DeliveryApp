const admin = require('firebase-admin');

// Inicializar una sola vez para todo el proyecto
if (admin.apps.length === 0) {
    admin.initializeApp();
}

const { registrarVenta } = require('./registrarVenta');
const { enviarNotificacion } = require('./enviarNotificacion');
const { enviarNotificacionUniversal } = require('./enviarNotificacionUniversal');
const { onOrdenTransferenciaAceptadaConDetalle } = require('./onOrdenTransferenciaAceptadaConDetalle');

// Importar la nueva función
const { notificarNuevaVenta } = require('./notificarNuevaVenta');

// Exportaciones
exports.registrarVenta = registrarVenta;
exports.enviarNotificacion = enviarNotificacion;
exports.enviarNotificacionUniversal = enviarNotificacionUniversal;
exports.onOrdenTransferenciaAceptadaConDetalle = onOrdenTransferenciaAceptadaConDetalle;

// Exportación de la nueva función
exports.notificarNuevaVenta = notificarNuevaVenta;