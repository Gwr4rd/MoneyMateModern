# Configuracion y publicacion

## 1. Supabase

1. Crea un proyecto en [Supabase](https://supabase.com/dashboard).
2. Abre `SQL Editor`, pega el contenido de `supabase/schema.sql` y ejecutalo. Tambien puedes copiar el mismo script desde el manual integrado de Android o web.
3. En `Authentication > Providers > Email`, deja habilitado Email.
4. Crea un usuario en `Authentication > Users` o habilita el registro de usuarios.
5. Copia `Project URL` y la clave publica desde `Project Settings > API`.

La tabla usa Row Level Security. Cada usuario solo puede leer o modificar la fila cuyo `user_id` coincide con su sesion.

### Android

1. Abre el menu de tres puntos.
2. Entra a `Sincronizar`.
3. En `Configurar Supabase`, escribe solamente la URL del proyecto y la clave publica.
4. Pulsa `Guardar conexión`.
5. En la pantalla siguiente elige `Iniciar sesión` o `Crear cuenta`.
6. Escribe el correo y la contraseña.
7. Después de conectar la cuenta, usa `Subir copia local` o `Descargar copia de la nube` para elegir la copia inicial.

La contraseña no se guarda en las preferencias del dispositivo. Se guardan el identificador de usuario y los tokens de sesión dentro de las preferencias privadas de Android. El acceso se renueva automáticamente antes de vencer.

Al actualizar desde una version anterior a `1.2.0`, inicia sesion una ultima vez. Las versiones anteriores no conservaban el token necesario para renovar la sesion.

Después de la conexión inicial:

- Cada modificación local deja una subida pendiente y se sincroniza automáticamente.
- La aplicación comprueba cambios remotos cada 30 segundos mientras esta abierta.
- Si no hay red, la subida queda pendiente para el siguiente intento.
- `Manual y script SQL` abre la guia completa y permite copiar el script sin salir de la aplicación.

### Web

1. Abre la aplicación web y pulsa `Sincronizar`.
2. Escribe la URL del proyecto y la clave publica.
3. Pulsa `Guardar conexión`.
4. Inicia sesión o crea una cuenta.
5. Usa `Subir` o `Descargar` para elegir la copia inicial.

La configuración y la sesión se guardan en ese navegador. El cliente de Supabase renueva el acceso automáticamente. Los cambios locales se suben después de guardarlos y la aplicación comprueba la nube cada 30 segundos.

El botón `Manual y script SQL` muestra la guia completa y permite copiar el script seguro desde la web.

También puedes preconfigurar un despliegue copiando `web/.env.example` como `web/.env`:

```dotenv
VITE_SUPABASE_URL=https://TU_PROYECTO.supabase.co
VITE_SUPABASE_PUBLISHABLE_KEY=TU_CLAVE_PUBLICA
```

Después ejecuta `pnpm install` y `pnpm run build` dentro de `web`.

Las variables de Netlify son opcionales y funcionan como valores iniciales. La interfaz permite cambiarlos por navegador. Las variables `VITE_*` son publicas, por eso nunca se debe colocar una clave `service_role`.

Documentacion oficial:

- [Autenticacion de Supabase](https://supabase.com/docs/guides/auth)
- [Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security)
- [Cliente JavaScript](https://supabase.com/docs/reference/javascript/installing)

## 2. Publicar la web en Netlify

### Desde GitHub

1. Sube esta carpeta a un repositorio de GitHub.
2. En Netlify selecciona `Add new project > Import an existing project`.
3. Conecta GitHub y elige el repositorio.
4. Netlify detectara `netlify.toml`. La base es `web`, el comando es `pnpm run build` y la carpeta publicada es `web/dist`.
5. Opcionalmente, en `Project configuration > Environment variables` agrega valores iniciales:

```text
VITE_SUPABASE_URL
VITE_SUPABASE_PUBLISHABLE_KEY
```

6. Ejecuta el primer despliegue.

### Despliegue manual

1. Ejecuta `pnpm install` y `pnpm run build` dentro de `web`.
2. En Netlify abre `Deploys` y arrastra la carpeta `web/dist`.
3. Configura Supabase desde el botón `Sincronizar` de la propia web.

Referencias:

- [Publicar una aplicacion Vite](https://docs.netlify.com/build/frameworks/framework-setup-guides/vite/)
- [Variables de entorno en Netlify](https://docs.netlify.com/build/environment-variables/get-started/)

## 3. Subir el proyecto a GitHub desde la web

No se necesita ningun archivo comprimido. Usa la carpeta preparada
`MoneyMateModern-GitHub-v1.2.0` y sigue la guia
[`SUBIR_A_GITHUB_WEB.md`](SUBIR_A_GITHUB_WEB.md).

Resumen:

1. Crea un repositorio vacio en [GitHub](https://github.com/new).
2. No agregues README, licencia ni `.gitignore` al crearlo, porque ya existen en el proyecto.
3. Abre la carpeta `MoneyMateModern-GitHub-v1.2.0` en el Explorador de Windows.
4. Selecciona todo el contenido interior de la carpeta, no la carpeta contenedora.
5. En GitHub abre `Add file > Upload files` y arrastra la seleccion al navegador.
6. Espera a que termine la carga y confirma con `Commit changes`.

El flujo `.github/workflows/build.yml` verificara Android y web en cada cambio.

Referencias:

- [Crear un repositorio](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-new-repository)
- [Subir archivos desde la web](https://docs.github.com/en/repositories/working-with-files/managing-files/adding-a-file-to-a-repository)

## 4. Crear el Release de GitHub

1. Abre `Releases > Draft a new release`.
2. Crea la etiqueta `v1.2.0`.
3. Usa como titulo `MoneyMate Modern 1.2.0`.
4. Pega el contenido de `RELEASE_NOTES.md`.
5. Adjunta `MoneyMateModern-release-signed-v1.2.0.apk`.
6. Publica el release.

Referencia: [Administrar Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository).

## 5. Clave de firma

El APK entregado esta firmado para instalacion directa. Para futuras publicaciones conserva una clave propia fuera del repositorio. No subas archivos `.jks`, contraseñas ni claves `service_role` a GitHub.
