# MoneyMate Modern 1.1.1

Aplicación de finanzas personales moderna, sin anuncios y compatible con Android 10 o superior.

## Cambios de esta versión

- Las transferencias se muestran en una sola fila con texto azul, sin fondo ni sombreado azul.
- Nuevo editor de movimientos en pantalla completa con encabezado y flecha atrás.
- Al copiar un movimiento se puede elegir entre la fecha de hoy y la fecha original antes de editar importe, fecha y demás campos.
- Eliminar un movimiento requiere una confirmación explícita.
- Nuevo icono de modo oscuro con luna y nube.
- Se corrigen cuentas duplicadas durante la importación.
- El respaldo de prueba pasa de dos filas `BCP/Yape Tarjeta` a una sola cuenta, conservando sus movimientos.
- Supabase se configura en dos pasos: primero URL y clave pública; después inicio de sesión o creación de cuenta.
- La web permite guardar la conexión de Supabase desde la propia interfaz.

## Funciones incluidas

- Transacciones, cuentas y categorías editables.
- Búsqueda por cuenta, nota, descripción, fecha, categoría e importe.
- Estado diario, semanal, mensual, anual o completo.
- Reportes XLS y XLSX.
- Importación MMBAK, CSV, JSON y XLSX.
- Sincronización privada entre Android y web mediante Supabase.
- Aplicación web responsive preparada para Netlify.

## Instalación Android

1. Descarga `MoneyMateModern-release-signed-v1.1.1.apk`.
2. Permite la instalación desde el navegador o gestor de archivos.
3. Abre el APK y confirma la instalación.

## Requisitos

- Android 10 o posterior.
- La sincronización necesita un proyecto de Supabase configurado por el usuario.

La aplicación no contiene anuncios. Los datos se guardan localmente y solo se envían a Supabase cuando el usuario lo solicita.
