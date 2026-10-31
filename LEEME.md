# Estados HD — Cómo obtener el APK (gratis, 5 minutos)

1. Entra a https://github.com y crea una cuenta (si no tienes).
2. Botón **New repository** → nombre `EstadosHD` → **Private** → Create.
3. En el repositorio: **uploading an existing file** → arrastra TODO el contenido
   de esta carpeta (incluida la carpeta `.github`) → **Commit changes**.
   (Si el navegador no deja subir `.github`, crea el archivo
   `.github/workflows/build.yml` con **Add file → Create new file** y pega su contenido.)
4. Pestaña **Actions** → "Compilar APK Estados HD" → espera el check verde (~6-8 min).
5. Abre la ejecución → sección **Artifacts** → descarga **EstadosHD-APK** (zip con `EstadosHD.apk`).
6. Pasa el APK al celular, ábrelo y permite "Instalar apps de origen desconocido".

Requisitos: Android 10 o superior.
Firma fija incluida (app/estadoshd.jks): las nuevas versiones se instalan encima sin desinstalar.
