# Plan de Implementación: Cargando Personalizado en Finanzas

Este plan detalla la creación de un indicador de carga personalizado para la pantalla de Finanzas (Resumen Operativo), reemplazando el spinner estándar por una animación alineada con la identidad de marca de "Delisa Botanas".

## Revisión del Usuario Requerida

> [!IMPORTANT]
> El nuevo cargador se mostrará tanto en la carga inicial como al actualizar el rango de fechas. He diseñado una animación que utiliza el logo de la app con un efecto de pulso y colores de la marca.

## Cambios Propuestos

### Componentes de UI

#### [NUEVO] [DelisaLoadingComponents.kt](file:///C:/APLICACIONES ANDROID/delivery/app/src/main/java/com/gruposanangel/delivery/ui/components/DelisaLoadingComponents.kt)
*   Creación de un archivo para componentes de carga reutilizables.
*   Implementación de `DelisaLoadingOverlay`: Un overlay a pantalla completa con el logo pulsante y un mensaje dinámico.
*   Implementación de `DelisaLogoPulse`: Una animación específica para el logo usando `InfiniteTransition`.

#### [MODIFICAR] [Pantalla_Resumen_Operativo.kt](file:///C:/APLICACIONES ANDROID/delivery/app/src/main/java/com/gruposanangel/delivery/ui/screens/Pantalla_Resumen_Operativo.kt)
*   Integrar `DelisaLoadingOverlay` para manejar tanto el estado `isLoading` (carga inicial) como `isFetchingVentas` (actualización de fechas).
*   Eliminar el uso de `CircularProgressIndicator` genérico.

## Plan de Verificación

### Verificación Manual
1.  Abrir la pantalla de Finanzas desde el Dashboard.
2.  Verificar que aparezca el nuevo cargador durante la carga inicial.
3.  Cambiar el rango de fechas usando el calendario.
4.  Confirmar que el cargador aparezca mientras se obtienen los nuevos datos de ventas.
5.  Generar un PDF para asegurar que el overlay de PDF no entre en conflicto con el de carga general.
