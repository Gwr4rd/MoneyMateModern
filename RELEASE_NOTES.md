# Control Financiero 2.5.0

Navegación en pastillas y nuevas vistas de Transacciones y Estado en Android y web.

## Novedades

- Diario muestra el historial agrupado por fecha. Semanal, Mensual y Anual muestran los ingresos, gastos y transferencias del periodo.
- Total muestra los importes acumulados como texto, con un color para cada tipo de movimiento.
- Estado muestra dos gráficos circulares lado a lado y compara ingresos y gastos con barras horizontales para cada cuenta.
- Los respaldos JSON conservan las transferencias y sus identificadores entre Android y web. Se validan antes de reemplazar datos.
- Los movimientos con identificadores distintos se conservan aunque coincidan en importe, cuenta, nota y hora. Las cuentas con historial se pueden ocultar.
- La sincronización comprueba la versión de la copia de Supabase antes de escribir y avisa cuando hay cambios en conflicto.

## Compatibilidad

- Mantiene el identificador y el certificado de firma de `2.4.0` para actualizar sobre la instalación existente.
- La sincronización usa la tabla existente de Supabase.

## Instalación

Descarga `Control-Financiero-release-signed-v2.5.0.apk` e instálalo sobre la versión anterior.

## Requisitos

- Android 10 o posterior.
