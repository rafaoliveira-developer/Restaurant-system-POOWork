# Arquitetura

O Backend usa o servidor HTTP do JDK, JDBC para PostgreSQL, Gson para contratos JSON e Eclipse Paho para MQTT. A lógica de comanda, pedido, confirmação e permissão fica em serviços Java separados dos controles HTTP. JavaFX e Android usam a API para consultar e executar ações transacionais. Os dispositivos não conectam ao banco.

| Relação | Responsabilidade |
|---|---|
| employees, sessions | Perfis, expedientes, credencial derivada por PBKDF2-HMAC-SHA256 e sessões opacas com hash e validade de 12 horas |
| dining_tables, tabs | Identificação, área, disponibilidade e comanda ativa exclusiva por mesa |
| categories, stations, products | Relacionamentos independentes para categoria e estação |
| orders, order_items, dispatches, order_rejections | Pedido, preço registrado, confirmação por estação e rejeição persistida |
| payments, cash_closings | Pagamento e consolidação por fechamento de caixa |
| print_jobs, audit_events, outbox | Trabalho original, cópias, rastreabilidade e publicação assíncrona |

Os papéis são enumerações do contrato e as permissões são aplicadas pelos serviços Java, com restrição de papel no schema. O pedido trava a mesa e a comanda, valida a disponibilidade, registra os itens com os preços vigentes, cria os despachos e grava os eventos na outbox. A chave de idempotência é por garçom e o UUID identifica um único pedido. A rejeição correlacionada permanece durável para reentregas da mesma mensagem. O modelo da API cria a comanda no primeiro pedido ou por abertura explícita.

O garçom só opera comandas atribuídas a si. O caixa paga uma conta solicitada após a consolidação de todos os pedidos da comanda. Somente os itens aceitos por sua estação compõem o valor. A confirmação de pagamento, o registro de auditoria e a liberação da mesa são atômicos. A revisão do caixa envia o valor esperado para detectar mudanças antes da confirmação.

Os eventos MQTT são gravados na mesma transação do estado. O publicador usa QoS 1, sessão persistente e confirmação do broker antes de marcar a outbox. A janela entre PUBACK e commit permite reentrega. Pedidos e trabalhos de impressão são deduplicados no PostgreSQL. As interfaces reconsultam o estado oficial ao receber eventos e em intervalos de reconciliação. O APK guarda a mensagem pendente com sua chave em armazenamento privado e adiciona a sessão no envio.

As migrations `V001` e `V002` são aplicadas pela inicialização do Backend em ordem, com bloqueio transacional e a tabela `schema_versions`. Faça backup lógico com `pg_dump` no ambiente operacional. Proteja também a configuração TLS e o cadastro de identidades MQTT.
