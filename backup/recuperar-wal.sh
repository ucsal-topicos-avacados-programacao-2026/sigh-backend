#!/bin/sh
# restore_command da restauração (RNF-007): decifra o segmento de WAL pedido pelo servidor, conferindo
# a integridade. Uso, pelo servidor: recuperar-wal.sh <pasta-dos-segmentos> %f %p
# - segmento que não existe: código 1, que é como o servidor sabe que chegou ao fim do WAL;
# - qualquer outra falha (pasta inacessível, chave ausente ou fraca, segmento alterado, chave errada):
#   código 126, que faz o servidor interromper a restauração, em vez de terminá-la ali e perder em
#   silêncio o que vinha depois.
set -eu

pasta="$1"; nome="$2"; destino="$3"
if [ ! -d "$pasta" ] || [ ! -r "$pasta" ] || [ ! -x "$pasta" ]; then
    echo "[sigh-backup] pasta de WAL $pasta inacessível; restauração interrompida" >&2
    exit 126
fi
[ -e "$pasta/$nome.gpg" ] || exit 1
if ! ( . "$(dirname "$0")/cripto.sh" && gpg_decifrar "$pasta/$nome.gpg" 2>/dev/null | gzip -dc > "$destino.parcial" ); then
    echo "[sigh-backup] segmento $nome não pôde ser decifrado (arquivo alterado, chave errada ou ausente);" \
        "restauração interrompida" >&2
    rm -f "$destino.parcial"
    exit 126
fi
mv "$destino.parcial" "$destino"
