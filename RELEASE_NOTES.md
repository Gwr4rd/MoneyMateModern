# MoneyMate Modern 1.3.1

Aplicación de finanzas personales moderna, sin anuncios y compatible con Android 10 o superior.

## Cambios de esta versión

- Las cuentas con saldo calculado `0.00` se ocultan automáticamente en Android y web.
- Las cuentas vacías permanecen disponibles mediante la opción `Mostrar cuentas ocultas`.
- Una cuenta vuelve a mostrarse automáticamente cuando recibe un movimiento y deja de tener saldo cero.
- El ocultamiento automático no modifica la cuenta ni impide editarla, eliminarla o usarla en nuevos movimientos.

## Funciones incluidas

- Transacciones, cuentas y categorías editables.
- Búsqueda por cuenta, nota, descripción, fecha, categoría e importe.
- Estado diario, semanal, mensual, anual o completo.
- Reportes XLS y XLSX.
- Importación MMBAK, CSV, JSON y XLSX.
- Sincronización privada entre Android y web mediante Supabase.
- Aplicación web responsive preparada para Netlify.

## Instalación Android

1. Descarga `MoneyMateModern-release-signed-v1.3.1.apk`.
2. Permite la instalación desde el navegador o gestor de archivos.
3. Abre el APK y confirma la instalación.

## Requisitos

- Android 10 o posterior.
- La sincronización necesita un proyecto de Supabase configurado por el usuario.

La aplicación no contiene anuncios. La sincronización solo se activa cuando el usuario configura su propio proyecto de Supabase.

Al actualizar desde una versión anterior a `1.2.0` se debe iniciar sesión una última vez. Desde ese momento la sesión queda guardada y se renueva automáticamente.
