# MoneyMate Modern 1.3.0

Aplicación de finanzas personales moderna, sin anuncios y compatible con Android 10 o superior.

## Cambios de esta versión

- Android adopta la estética limpia de la aplicación web, con controles mejor separados, tipografía más clara y una paleta verde consistente.
- Transacciones incorpora los periodos Anual, Mensual, Semanal, Diario y Total.
- Las notas se muestran siempre como una línea destacada en gastos, ingresos y transferencias.
- El manual de Supabase evita recortes de texto y separa claramente títulos, instrucciones y acciones.
- Comienza la migración segura a Kotlin: la actividad de entrada y la lógica de periodos ya son Kotlin; el importador de respaldos continúa en Java para conservar compatibilidad.
- La web permite crear, editar, ocultar, mostrar y eliminar cuentas.
- La web permite editar, copiar y eliminar transacciones.
- La web incorpora modo claro u oscuro y selección de moneda por país.

## Funciones incluidas

- Transacciones, cuentas y categorías editables.
- Búsqueda por cuenta, nota, descripción, fecha, categoría e importe.
- Estado diario, semanal, mensual, anual o completo.
- Reportes XLS y XLSX.
- Importación MMBAK, CSV, JSON y XLSX.
- Sincronización privada entre Android y web mediante Supabase.
- Aplicación web responsive preparada para Netlify.

## Instalación Android

1. Descarga `MoneyMateModern-release-signed-v1.3.0.apk`.
2. Permite la instalación desde el navegador o gestor de archivos.
3. Abre el APK y confirma la instalación.

## Requisitos

- Android 10 o posterior.
- La sincronización necesita un proyecto de Supabase configurado por el usuario.

La aplicación no contiene anuncios. La sincronización solo se activa cuando el usuario configura su propio proyecto de Supabase.

Al actualizar desde una versión anterior a `1.2.0` se debe iniciar sesión una última vez. Desde ese momento la sesión queda guardada y se renueva automáticamente.
