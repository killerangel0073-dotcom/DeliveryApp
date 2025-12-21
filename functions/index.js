const admin = require('firebase-admin');
admin.initializeApp();

const { registrarVenta } = require('./registrarVenta');

const { enviarNotificacion } = require('./enviarNotificacion'); // 1 token
const { enviarNotificacionUniversal } = require('./enviarNotificacionUniversal'); // varios tokens

const { onOrdenTransferenciaAceptadaConDetalle } =
  require('./onOrdenTransferenciaAceptadaConDetalle');

exports.registrarVenta = registrarVenta;

exports.enviarNotificacion = enviarNotificacion; // 1 token
exports.enviarNotificacionUniversal = enviarNotificacionUniversal; // varios tokens

exports.onOrdenTransferenciaAceptadaConDetalle = onOrdenTransferenciaAceptadaConDetalle;
