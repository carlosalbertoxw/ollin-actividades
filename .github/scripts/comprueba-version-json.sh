#!/usr/bin/env bash
#
# Comprueba que un version.json se lea en la app como se lee de verdad.
#
#   bash .github/scripts/comprueba-version-json.sh web/dist/version.json
#
# El version.json es un contrato con cada APK ya instalado, que trae compilada
# su manera de leerlo (ComprobadorActualizaciones.lee) y no se puede corregir
# desde fuera. Si algo no le gusta, la app descarta el archivo entero en
# silencio, y el aviso de version nueva —el unico canal de correcciones de una
# app que no esta en la tienda— se apaga en todas las instalaciones sin que
# nada falle. Por eso esto replica las reglas de la app, no unas parecidas:
#
#   - `version` es X.Y.Z (Version.de).
#   - `apk` empieza por DESCARGAS_OFICIALES y no trae `..`, `%2e` ni `\`
#     (esDescargaOficial). El prefijo ya fija esquema, host y que no haya
#     usuario ni puerto.
#
# Lo usan pruebas.yml, sobre el sitio de cada cambio, y sitio.yml, sobre el que
# se va a publicar con los datos de la release: es en ese donde un cambio de
# formato de GitHub apagaria el aviso.
#
# Si cambia DESCARGAS_OFICIALES en la app, cambia tambien aqui.

set -euo pipefail

archivo="${1:?Uso: comprueba-version-json.sh <version.json>}"

DESCARGAS_OFICIALES="https://github.com/carlosalbertoxw/ollin-actividades/releases/download/"

if ! jq -e --arg prefijo "$DESCARGAS_OFICIALES" '
  (.version | type == "string" and test("^[0-9]+\\.[0-9]+\\.[0-9]+$")) and
  (.apk | type == "string"
    and startswith($prefijo)
    and (contains("..") | not)
    and (test("%2e"; "i") | not)
    and (contains("\\") | not))
' "$archivo" > /dev/null; then
  echo "::error::$archivo no se leería en la app: version tiene que ser X.Y.Z y apk una descarga de $DESCARGAS_OFICIALES"
  echo "::error::La app descartaría el archivo entero y el aviso de versión nueva se apagaría en todas las instalaciones."
  cat "$archivo"
  exit 1
fi

echo "$archivo en regla:"
cat "$archivo"
