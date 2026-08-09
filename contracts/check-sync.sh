#!/usr/bin/env bash
# Verifie que contracts/rabbitmq/*.schema.json est identique entre ce repo
# (kbeauty-ai-core-service, source de verite car producteur des evenements)
# et kbeauty-shop (copie committee separement, cf. contracts/README.md).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OTHER_REPO="${1:-$HERE/../../kbeauty-shop}"

if [ ! -d "$OTHER_REPO/contracts/rabbitmq" ]; then
    echo "Introuvable : $OTHER_REPO/contracts/rabbitmq (chemin vers kbeauty-shop correct ?)" >&2
    exit 1
fi

status=0
for schema in "$HERE"/rabbitmq/*.schema.json; do
    name="$(basename "$schema")"
    other="$OTHER_REPO/contracts/rabbitmq/$name"

    if [ ! -f "$other" ]; then
        echo "MANQUANT cote kbeauty-shop : $name"
        status=1
        continue
    fi

    if diff -q "$schema" "$other" > /dev/null; then
        echo "OK : $name synchronise"
    else
        echo "DESYNCHRONISE : $name differe entre les deux repos"
        status=1
    fi
done

exit $status
