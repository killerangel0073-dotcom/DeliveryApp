# Plan de Ajuste de Arqueo y Liquidación

Este plan detalla los cambios necesarios para asegurar que la lógica de Arqueo y Liquidación cumpla estrictamente con los requisitos del negocio: el Arqueo solo ajusta el stock local del vendedor, mientras que la Liquidación vacía el stock del vendedor y lo transfiere al Almacén Huasteca.

## User Review Required

> [!IMPORTANT]
> Se ha detectado que actualmente la pantalla de Liquidación permite seleccionar cualquier almacén como destino. El plan propone forzar este destino a **"Almacen Huasteca"** de forma automática cuando se selecciona el modo Liquidación, para evitar errores operativos. ¿Es esto correcto o debería seguir permitiéndose la selección manual con Huasteca como default?

> [!WARNING]
> Se aplicará una mejora técnica para evitar errores de sincronización si un producto no existe previamente en el Almacén Huasteca (uso de `set` con `merge` en lugar de `update`).

## Proposed Changes

### [Component Name] UI y Lógica de Liquidación

#### [MODIFY] [Pantalla_Liquidacion_Directa.kt](file:///C:/APLICACIONES%20ANDROID/delivery/app/src/main/java/com/gruposanangel/delivery/ui/screens/Pantalla_Liquidacion_Directa.kt)
- Bloquear la selección del almacén de destino cuando el modo es `LIQUIDAR` (`retornarABodega = true`).
- Asegurar que el texto del destino muestre siempre "Almacen Huasteca" en este modo.
- Deshabilitar visualmente el selector de destino para indicar que es automático.

#### [MODIFY] [LiquidacionViewModel.kt](file:///C:/APLICACIONES%20ANDROID/delivery/app/src/main/java/com/gruposanangel/delivery/ui/screens/LiquidacionViewModel.kt)
- En `confirmarAuditoria`, asegurar que si `retornarABodega` es true, el destino final sea siempre `"Almacen Huasteca"`.
- Cambiar `batch.update` por un método más seguro (o asegurar la existencia del documento) al incrementar el stock en el almacén de destino, para evitar fallos si el producto es nuevo en ese almacén.

#### [MODIFY] [ArqueoViewModel.kt](file:///C:/APLICACIONES%20ANDROID/delivery/app/src/main/java/com/gruposanangel/delivery/ui/screens/ArqueoViewModel.kt)
- Revisar y asegurar que la lógica de `autorizarCierre` solo impacte el almacén auditado (vendedor), lo cual ya parece estar correcto pero se verificará minuciosamente durante la implementación.

## Verification Plan

### Automated Tests
- No hay pruebas unitarias automatizadas configuradas para estos ViewModels actualmente, por lo que la verificación será manual/visual en el dispositivo.

### Manual Verification
1. **Prueba de Arqueo:**
   - Realizar un arqueo en un vendedor.
   - Confirmar que el stock se ajusta en el almacén del vendedor y **no** se genera movimiento hacia Huasteca.
   - Verificar en Firebase que `inventarioStock` del vendedor tiene los valores contados.
2. **Prueba de Liquidación:**
   - Realizar una liquidación en un vendedor.
   - Confirmar que el stock del vendedor queda en 0.
   - Confirmar que el stock se suma al `Almacen Huasteca`.
   - Verificar que el selector de destino esté bloqueado durante el proceso.
