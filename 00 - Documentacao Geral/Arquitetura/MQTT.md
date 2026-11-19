# MQTT v1

Todo evento inclui `event` (texto) e `version` (inteiro, 1). As mensagens são JSON em UTF-8 com QoS 1, sessão persistente e sem retenção. O Mosquitto recusa publicações retidas. O `username` deve coincidir com o código do funcionário e com o usuário autenticado pelo token REST.

| Evento | Tópico | Conteúdo |
|---|---|---|
| NOVOPEDIDO | `atlas/v1/commands/{username}/NOVOPEDIDO` | `orderId`, `idempotencyKey` em UUID, `tableId` positivo, `items` de 1–100 entradas com `productId`, `quantity` de 1–99 e `notes` opcional, `token` |
| ENVIABAR | `atlas/v1/stations/BAR/ENVIABAR` | `orderId`, `sequence`, `table_label`, `tab_id`, `waiter`, `created_at`, `station`, `status`, `items` filtrados com `name`, `quantity`, `notes` |
| ENVIACOZINHA | `atlas/v1/stations/COZINHA/ENVIACOZINHA` | Campos de ENVIABAR e `printId` do trabalho original |
| CONFIRMABAR | `atlas/v1/commands/{username}/CONFIRMABAR` | `orderId`, `sequence`, `status` (PREPARANDO, OK ou ERRO), `error` obrigatório em ERRO, `token` |
| CONFIRMACOZINHA | `atlas/v1/commands/{username}/CONFIRMACOZINHA` | Campos de CONFIRMABAR para a outra estação |
| CONFIRMAREPEDIDO | `atlas/v1/users/{username}/CONFIRMAREPEDIDO` | `orderId`, `status`, `sequence` se registrado, `code` na consolidação, `stations` com resultados e `error` em rejeição |
| ATUALIZACAO | `atlas/v1/updates` | `resource` alterado. Os clientes autorizados reconciliam a API |

```json
{
  "event": "NOVOPEDIDO",
  "version": 1,
  "orderId": "7c2e5ca8-0af9-42ce-a280-a09aef45a9a8",
  "idempotencyKey": "c325d462-2197-4d08-a4e6-bcea613880b2",
  "tableId": 3,
  "items": [{"productId": 15, "quantity": 2, "notes": "Sem cebola"}],
  "token": "<sessão REST do garçom>"
}
```

O preço, o nome, a estação, o garçom, o horário e a sequência são definidos pelo Backend. A confirmação `PENDENTE` indica recepção e persistência. O resultado posterior vem como `CONFIRMADO`, `PARCIAL`, `ERRO` ou `REJEITADO`. O aplicativo consulta o resultado autorizado pelo UUID para reconciliar quedas e eventos perdidos. Uma confirmação tardia de preparo não reabre a estação finalizada.

| Código | Cozinha | Bar |
|---|---|---|
| 11 | OK / fora do pedido | OK / fora do pedido |
| 10 | OK / fora do pedido | ERRO |
| 01 | ERRO | OK / fora do pedido |
| 00 | ERRO | ERRO |

Um bit de estação ausente vale 1 e o vetor `stations` contém apenas as estações envolvidas. Em pedido de um setor, uma falha resulta em `ERRO`, mesmo quando o código tem um bit 1. Estado da produção e estado do spooler são registrados separadamente.

Use `Provision-Mqtt.ps1` para criar senha e ACL individual; aplique a mesma mudança quando um funcionário trocar de código ou perfil e revogue a credencial antiga. O Backend verifica novamente o papel e a identidade do token. Terminais recuperam a fila por REST após reconexão. Em produção, configure MQTT com TLS e limite as portas à rede de operação. A autenticação do broker se aplica também a clientes de monitoramento. [Eclipse Mosquitto](https://mosquitto.org/man/mosquitto-conf-5.html)
