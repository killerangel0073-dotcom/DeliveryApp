const admin = require('firebase-admin');

// Inicializar una sola vez para todo el proyecto
if (admin.apps.length === 0) {
    admin.initializeApp();
}

// IMPORTACIONES DE FUNCIONES
const { registrarVenta } = require('./registrarVenta');
const { enviarNotificacion } = require('./enviarNotificacion');
const { enviarNotificacionUniversal } = require('./enviarNotificacionUniversal');
const { onOrdenTransferenciaAceptadaConDetalle } = require('./onOrdenTransferenciaAceptadaConDetalle');
const { notificarNuevaVenta } = require('./notificarNuevaVenta');
const { procesarAjusteInventario } = require('./procesarAjusteInventario');
const { auditarCambiosInventario } = require('./auditarCambiosInventario');
const { anularVenta } = require('./anularVenta'); // 🛡️ NUEVA

// EXPORTACIONES OFICIALES
exports.registrarVenta = registrarVenta;
exports.enviarNotificacion = enviarNotificacion;
exports.enviarNotificacionUniversal = enviarNotificacionUniversal;
exports.onOrdenTransferenciaAceptadaConDetalle = onOrdenTransferenciaAceptadaConDetalle;
exports.procesarAjusteInventario = procesarAjusteInventario;
exports.notificarNuevaVenta = notificarNuevaVenta;
exports.auditarCambiosInventario = auditarCambiosInventario;
exports.anularVenta = anularVenta; // 🛡️ NUEVA
