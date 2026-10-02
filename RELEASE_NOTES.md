# Control Financiero 2.3.0

Esta version simplifica el registro de movimientos y da protagonismo al dato que realmente explica cada operacion: la nota.

## Novedades

- Formulario de movimientos reorganizado con solo los campos esenciales: tipo, fecha, importe, tipo de cuenta, cuenta y nota.
- La nota ahora es obligatoria, mas grande y visible en cada transaccion.
- Tipos de cuenta y cuentas mas visuales, con colores y jerarquia clara.
- La administracion se concentra en tipos de cuenta; las categorias dejan de formar parte del flujo visible.
- Tarjetas de transacciones mas vivas, con iconos y colores diferenciados para ingresos, gastos y transferencias.
- Campos de importe, selectores y botones modernizados tanto en Android como en la web.
- Busqueda enfocada en notas, cuentas, fechas, tipos e importes.
- Vista previa de importacion y mensajes de sincronizacion mas simples.
- Version mostrada en la web corregida y alineada con la APK.

## Compatibilidad

- Las categorias y descripciones existentes se conservan internamente al editar, importar, exportar y sincronizar; esta actualizacion no elimina esos datos.
- Se mantienen los respaldos MMBAK, CSV, JSON y XLSX.
- El identificador y la firma son los mismos de las versiones anteriores, por lo que puede instalarse sobre `2.2.0` sin borrar los datos locales.

## Instalacion

1. Descarga `Control-Financiero-release-signed-v2.3.0.apk`.
2. Instalala sobre la version anterior.
3. Abre la aplicacion y conserva tu sesion y datos locales.

## Requisitos

- Android 10 o posterior.
- La sincronizacion con Supabase continua siendo opcional.
