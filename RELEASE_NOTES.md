# MoneyMate Modern 1.4.0

Aplicación de finanzas personales moderna, sin anuncios y compatible con Android 10 o superior.

## Cambios de esta versión

- Nuevo autocompletado de notas en Android y web: al escribir se sugieren textos utilizados en movimientos anteriores.
- Las sugerencias se ordenan por uso reciente, no distinguen mayúsculas y evitan notas duplicadas.
- El formulario de movimientos usa un selector visible para `Ingreso`, `Gasto` y `Transferencia`, inspirado en el flujo de la aplicación de referencia.
- Fecha y hora se presentan juntas y se eligen mediante controles nativos en Android.
- Los gastos e ingresos muestran únicamente cuenta y categoría; las transferencias cambian automáticamente a cuenta origen y destino.
- El formulario tiene mejor separación, jerarquía tipográfica y adaptación a pantallas móviles.
- Al iniciar una transferencia se propone una cuenta destino distinta de la cuenta origen.

## Funciones incluidas

- Transacciones, cuentas y categorías editables.
- Búsqueda por cuenta, nota, descripción, fecha, categoría e importe.
- Estado diario, semanal, mensual, anual o completo.
- Reportes XLS y XLSX.
- Importación MMBAK, CSV, JSON y XLSX.
- Sincronización privada entre Android y web mediante Supabase.
- Aplicación web responsive preparada para Netlify.

## Instalación Android

1. Descarga `MoneyMateModern-release-signed-v1.4.0.apk`.
2. Permite la instalación desde el navegador o gestor de archivos.
3. Abre el APK y confirma la instalación.

## Requisitos

- Android 10 o posterior.
- La sincronización necesita un proyecto de Supabase configurado por el usuario.

La aplicación no contiene anuncios. La sincronización solo se activa cuando el usuario configura su propio proyecto de Supabase.

Al actualizar desde una versión anterior a `1.2.0` se debe iniciar sesión una última vez. Desde ese momento la sesión queda guardada y se renueva automáticamente.
