# Impressora da cozinha

O terminal consulta a fila persistente da cozinha. Antes de emitir, solicita a atribuição exclusiva pelo Backend. Um novo evento ou uma reconexão apenas atualiza a fila. O trabalho físico é executado no computador da cozinha.

## Modos

- `ATLAS_PRINT_MODE=simulado` grava `<uuid>.txt` em `.local/comprovantes`, ou em `ATLAS_PRINT_OUTPUT`.
- `ATLAS_PRINT_MODE=sistema` usa a fila de impressão instalada no Windows. Informe em `ATLAS_PRINTER_NAME` o nome exato listado por `Get-Printer`. Selecione papel e driver nas preferências da fila antes do teste.

`IMPRESSO` no modo sistema significa envio concluído ao spooler. Confira a impressão física no papel. A falta da fila configurada gera `FALHA`. Um resultado incerto durante o envio ao spooler permanece em `INCERTO` para inspeção. Se o Terminal cair depois de assumir um trabalho, o operador pode marcar o estado incerto após o prazo, conferir spooler e papel e então autorizar uma cópia. Uma cópia tem identificador próprio e referência ao original. Retentativa do original é reservada a falhas confirmadas. Resultados já obtidos aguardam reconciliação em um diário local quando a API está indisponível.

Para concluir o teste físico, obtenha fabricante/modelo, nome da fila USB no PC da cozinha, versão do driver, largura e tipo de papel. Faça um pedido com acentos, observação longa e itens repetidos. Confira largura, quebra de linha, corte, status no terminal, desconexão e cópia identificada. O modo simulado foi testado na integração. A listagem Windows disponível mostrou uma Epson L3250 em rede e filas virtuais, sem identificação da fila USB da cozinha.
