# Control Financiero 2.4.0

Una interfaz mas directa para registrar y consultar el dinero, sin quitar las funciones de respaldo, sincronizacion ni exportacion.

## Novedades

- Nuevo movimiento mas breve: importe, cuenta y nota obligatoria a la vista; fecha, hora y descripcion se pueden desplegar. La ultima cuenta usada se recuerda en el dispositivo.
- Cuentas activas primero, identificadas por su tipo; las transferencias siguen permitiendo mover dinero entre efectivo y bancos.
- Transacciones con la nota como titulo principal, filtro de periodo compacto y acciones de copiar, editar y eliminar agrupadas.
- Estado con ingresos, gastos y balance visibles, mas opcion de alternar entre distribucion circular y barras por cuenta.
- Menu reorganizado en Datos, Sincronizar, Organizar cuentas, Preferencias y Acerca de.
- Se corrige la fecha local inicial en la web para zonas horarias al oeste de UTC.

## Compatibilidad

- Mantiene el mismo identificador y certificado de firma de `2.3.1`, para actualizar sin borrar datos ni la sesion local.
- Conserva la importacion y exportacion MMBAK, CSV y JSON, el reporte XLSX y la sincronizacion opcional con Supabase.

## Instalacion

Descarga `Control-Financiero-release-signed-v2.4.0.apk` e instalalo sobre la version anterior.

## Requisitos

- Android 10 o posterior.
