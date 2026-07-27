# MoneyMate Modern

Aplicacion de finanzas personales sin anuncios para Android 10 o superior, acompañada por una aplicacion web responsive.

## Funciones principales

- `Transacciones`, `Estado` y `Cuentas` con interfaz clara y modo oscuro mate.
- Icono del cerdito en el lanzador y en la web.
- Gastos, ingresos y transferencias editables y copiables.
- Transferencias agrupadas visualmente en una sola fila, con texto azul y sin sombreado.
- Busqueda por cuenta, categoria, notas, descripcion, fechas, tipo e importe.
- Estado diario, semanal, mensual, anual o completo con graficas animadas.
- Cuentas separadas en `Efectivo` y `Cuentas de Banco`.
- Cuentas y categorias personalizables, ocultables y recuperables.
- Monedas por pais, incluido Sol peruano (`PEN`, `S/`).
- Importacion y exportacion MMBAK, CSV, JSON y XLSX.
- Reportes XLS y XLSX por semana, mes, año o todo.
- Sincronizacion opcional Android/web con sesion persistente y renovación segura del acceso.
- Sincronizacion automatica al modificar datos y comprobacion periodica de cambios en la nube.
- Manual de Supabase integrado con el script SQL listo para copiar.
- Deduplicacion de cuentas por nombre al importar y al actualizar bases existentes.

## Compatibilidad de datos

El importador abre respaldos `.mmbak` SQLite en modo de solo lectura, adapta nombres de tablas y columnas conocidos y conserva cuentas, categorias, notas, fechas y transferencias. Los pares de una transferencia se mantienen internamente para calcular saldos, pero la interfaz y los reportes muestran una sola operacion.

Tambien se admite `Registro Contable` en XLSX. Las fechas seriales de Excel se convierten a ISO (`AAAA-MM-DD`).

## Estructura

- `app/`: aplicacion Android nativa.
- `web/`: aplicacion React/Vite.
- `supabase/schema.sql`: tabla, permisos y politicas RLS.
- `docs/CONFIGURACION_Y_PUBLICACION.md`: Supabase, Netlify, GitHub y Releases.
- `RELEASE_NOTES.md`: texto preparado para GitHub Release.

## Compilar Android

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

Android Studio tambien puede abrir esta carpeta directamente. El proyecto usa `minSdk 29`, `targetSdk 35` y Java 17 para Gradle.

## Ejecutar la web

```bash
cd web
pnpm install
pnpm run dev
```

Para produccion:

```bash
pnpm run build
```

Consulta la [guia de configuracion y publicacion](docs/CONFIGURACION_Y_PUBLICACION.md).
