# MoneyMate Modern 1.2.0

Aplicación de finanzas personales moderna, sin anuncios y compatible con Android 10 o superior.

## Cambios de esta versión

- La sesión de Supabase permanece iniciada en Android y web.
- Android renueva el acceso con el `refresh token`; la contraseña no se guarda.
- Los cambios locales se suben automáticamente y la nube se comprueba cada 30 segundos.
- Se mantiene la subida y descarga manual para elegir una copia de forma explícita.
- Nuevo manual de Supabase integrado en Android y web.
- El script SQL seguro puede copiarse directamente desde el manual de la aplicación.
- El flujo de configuración sigue el orden correcto: conexión, cuenta y sincronización.

## Funciones incluidas

- Transacciones, cuentas y categorías editables.
- Búsqueda por cuenta, nota, descripción, fecha, categoría e importe.
- Estado diario, semanal, mensual, anual o completo.
- Reportes XLS y XLSX.
- Importación MMBAK, CSV, JSON y XLSX.
- Sincronización privada entre Android y web mediante Supabase.
- Aplicación web responsive preparada para Netlify.

## Instalación Android

1. Descarga `MoneyMateModern-release-signed-v1.2.0.apk`.
2. Permite la instalación desde el navegador o gestor de archivos.
3. Abre el APK y confirma la instalación.

## Requisitos

- Android 10 o posterior.
- La sincronización necesita un proyecto de Supabase configurado por el usuario.

La aplicación no contiene anuncios. La sincronización solo se activa cuando el usuario configura su propio proyecto de Supabase.

Al actualizar desde `1.1.1` se debe iniciar sesión una última vez. Desde ese momento la sesión queda guardada y se renueva automáticamente.
