#!/usr/bin/env bash
# Descarga los jars contra los que se compila pero que no se publican como dependencia.
#
# BetterModel no tiene su API en un repositorio Maven publico (solo en GitHub Packages, que
# exige token). El jar de release trae dentro el modulo 'bettermodel-api' con las clases
# que usa CustomCore, asi que se compila directamente contra el.
#
# IMPORTANTE: de este jar SOLO se usan las clases de la API. No se copia ningun .bbmodel ni
# pack de recursos al servidor: BetterModel genera su pack a partir de sus propios modelos, y
# un archivo de mas cambiaria su hash y romperia el SHA1 que anuncia AutoPack.
#
# Uso:  ./scripts/install-libs.sh
#       (o a mano: mvn install:install-file -Dfile=libs/... -DgroupId=... ...)
set -euo pipefail

cd "$(dirname "$0")/.."

BM_VERSION="3.5.1"
BM_FILE="libs/bettermodel-${BM_VERSION}-paper.jar"
BM_URL="https://github.com/toxicity188/BetterModel/releases/download/${BM_VERSION}/bettermodel-${BM_VERSION}-paper.jar"

if [[ ! -f "$BM_FILE" ]]; then
    echo "Descargando BetterModel ${BM_VERSION}..."
    curl -fsSL --create-dirs -o "$BM_FILE" "$BM_URL"
fi

mvn -B -q install:install-file \
    -Dfile="$BM_FILE" \
    -DgroupId=kr.toxicity \
    -DartifactId=bettermodel \
    -Dversion="$BM_VERSION" \
    -Dpackaging=jar \
    -DgeneratePom=true

echo "Listo: ${BM_FILE} instalado como kr.toxicity:bettermodel:${BM_VERSION}"
