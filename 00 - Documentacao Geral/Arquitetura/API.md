# API REST v1

Base `/api/v1/`. Use JSON em UTF-8. Informe `Authorization: Bearer <token>` nas operações protegidas. Erros retornam `{"error":"mensagem"}` com o código HTTP correspondente. A criação de pedido e a produção usam MQTT.

| Método e recurso | Acesso e uso |
|---|---|
| GET `health` | Disponibilidade de PostgreSQL e MQTT |
| POST `login` | `username` e `password` retornam `token` e `employee` |
| GET `me`, POST `logout` | Conta atual e revogação de sessão |
| GET `restaurant` | Nome de exibição, todas as contas |
| GET `tables`, `products` | Garçom, gerente e caixa. Gerência também recebe produtos desativados |
| POST `tables`, PUT `tables/{id}` | Gerência. `label`, `area`, `available` |
| POST `products`, PUT `products/{id}` | Gerência. `name`, `category`, `station`, `price`, `description`, `available`, `image` HTTPS |
| GET/POST `employees`, PUT `employees/{id}` | Gerência. `username`, `name`, `role`, `schedule`, `active`, `password` obrigatória no cadastro |
| GET `orders`, `orders/{uuid}` | Gerência, caixa e garçom para os próprios pedidos |
| POST `orders/{uuid}/deliver` | Gerência e garçom responsável, quando houver produção confirmada |
| GET `queue/{station}` | Setor próprio ou gerência, com itens exclusivos da estação |
| GET `tabs/{tableId}` | Gerência, caixa e garçom responsável pela comanda |
| POST `tabs/{tableId}/open` | Gerência ou garçom, abertura idempotente. A gerência informa `waiterId` |
| POST `tabs/{tableId}/request` | Gerência, caixa ou garçom responsável |
| POST `tabs/{tableId}/pay` | Gerência ou caixa. `method` = PIX, DINHEIRO ou CARTAO, `expectedTotal` opcional |
| POST `tabs/{tableId}/cancel` | Gerência, comanda sem pedidos |
| GET `closing-queue`, `payments`, `dashboard` | Gerência e caixa |
| GET/POST `cash-closings` | Gerência e caixa. Consolida pagamentos ainda sem caixa |
| GET `reports?from=YYYY-MM-DD&to=YYYY-MM-DD` | Gerência e caixa, período inclusivo em São Paulo |
| GET `history`, GET/PUT `settings` | Gerência. Atualização: `restaurant` |
| GET `prints` | Cozinha, trabalhos pendentes |
| POST `prints/{uuid}/claim`, `result` | Cozinha. O resultado usa `claimId`, `status` e `error` quando aplicável |
| POST `prints/{uuid}/retry`, `cancel`, `uncertain`, `reprint` | Cozinha. A cópia exige um UUID `copyId` |

Listagens operacionais usam limites de proteção e ordem determinística. Consulte o [contrato OpenAPI](openapi.yaml) para importar a API em uma ferramenta de testes.
