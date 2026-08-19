# Control Financiero 2.1.0

Actualización centrada en la protección de los datos locales y una sincronización más predecible en Android y web.

## Novedades

- Vista previa antes de importar: muestra cuentas, categorías, movimientos, transferencias, periodo e importes.
- Detección y omisión de movimientos exactamente duplicados dentro del respaldo.
- Copia de recuperación automática antes de importar y opción `Deshacer última importación`.
- Revisión de integridad para detectar base dañada, duplicados, cuentas faltantes, datos inválidos y transferencias incompletas.
- Reparación segura de duplicados y movimientos cuya cuenta ya no existe.
- Protección de conflictos de Supabase: la aplicación pide elegir entre la copia local y la nube en vez de sobrescribir cambios silenciosamente.
- Importación y validación web de respaldos JSON con la misma vista previa y recuperación.
- Nueva lógica de importación e integridad desarrollada en Kotlin como parte de la migración gradual.
- Traducciones completas de estas funciones en español, inglés, portugués y francés.

## Instalación

1. Descarga `Control-Financiero-release-signed-v2.1.0.apk`.
2. Instálala sobre la versión anterior para conservar los datos y la sesión local.
3. Antes de importar un archivo importante, revisa el resumen presentado por la aplicación.

## Requisitos

- Android 10 o posterior.
- La sincronización con Supabase continúa siendo opcional.

El identificador y la firma se mantienen para que Android reconozca esta versión como una actualización compatible.
