# Subir MoneyMate Modern a GitHub desde la web

Este procedimiento no usa ZIP, Git por terminal ni archivos comprimidos.

## Carpeta que se debe cargar

Usa solamente la carpeta preparada:

```text
MoneyMateModern-GitHub-v1.4.2
```

Esta carpeta excluye compilaciones, dependencias descargadas, configuraciones
locales, credenciales, APK y claves de firma.

La version preparada contiene 73 archivos. Puede cargarse en una sola tanda:
ningun archivo se acerca al limite de 25 MiB de la carga web de GitHub.

## 1. Crear el repositorio

1. Inicia sesion en [GitHub](https://github.com/).
2. Pulsa el boton `+` de la esquina superior derecha.
3. Selecciona `New repository`.
4. En `Repository name` escribe `MoneyMateModern`.
5. Elige `Public` o `Private`.
6. No marques `Add a README file`.
7. No agregues `.gitignore` ni licencia. El proyecto ya contiene esos archivos.
8. Pulsa `Create repository`.

## 2. Cargar los archivos sin comprimir

1. Abre `MoneyMateModern-GitHub-v1.4.2` en el Explorador de Windows.
2. Entra en la carpeta y presiona `Ctrl + A` para seleccionar todo su contenido.
3. En el repositorio vacio de GitHub pulsa `uploading an existing file`.
4. Si el repositorio ya muestra archivos, usa `Add file > Upload files`.
5. Arrastra la seleccion del Explorador al area de carga del navegador.
6. Espera hasta que GitHub muestre todos los archivos como cargados.

No arrastres la carpeta contenedora completa. Se debe arrastrar su contenido
para que `app`, `web`, `gradle` y `README.md` queden en la raiz del repositorio.

## 3. Revisar y confirmar

Antes de guardar, comprueba que aparezcan al menos estos elementos:

```text
.github
app
docs
gradle
supabase
web
.gitignore
build.gradle
gradlew
gradlew.bat
netlify.toml
README.md
RELEASE_NOTES.md
settings.gradle
```

1. En `Commit message` escribe `Publicar MoneyMate Modern 1.4.2`.
2. Selecciona `Commit directly to the main branch`.
3. Pulsa `Commit changes`.
4. Espera a que GitHub vuelva a la pagina principal del repositorio.

## 4. Verificar la publicacion

1. Comprueba que `README.md` se muestre en la pagina principal.
2. Abre la pestana `Actions`.
3. Verifica que el flujo de compilacion de Android y web se haya iniciado.
4. No subas el APK al codigo fuente. Se adjunta despues en `Releases`.

## Archivos que nunca deben subirse

```text
local.properties
web/.env
.gradle/
app/build/
web/node_modules/
web/dist/
*.jks
*.keystore
*.apk
```

Referencias oficiales:

- [Crear un repositorio](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-new-repository)
- [Subir un proyecto desde el navegador](https://docs.github.com/en/get-started/start-your-journey/uploading-a-project-to-github)
- [Agregar archivos a un repositorio](https://docs.github.com/en/repositories/working-with-files/managing-files/adding-a-file-to-a-repository)
