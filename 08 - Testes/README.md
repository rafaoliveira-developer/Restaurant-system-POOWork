# Validação do ATLAS

## Executar

Compile os módulos Java SE na raiz com `Build.ps1`. Inicie um PostgreSQL de teste na porta 55432 com o usuário `atlas` e forneça o diretório dos binários Windows do PostgreSQL e do Mosquitto. O runner cria um banco isolado, usuários MQTT efêmeros, um Backend separado e dados de teste próprios.

```powershell
& '.\08 - Testes\Integracao\Run-Integration.ps1' `
  -PgBin '.local/pgsql/bin' -MosquittoBin '.local/mosquitto' -JavaFX
```

Para preparar um cluster descartável em um PowerShell separado, use os binários portáteis. A inicialização é feita uma vez.

```powershell
& '.local/pgsql/bin/initdb.exe' -D '.local/pg-test' -U atlas -A trust -E UTF8 --locale=C
& '.local/pgsql/bin/postgres.exe' -D '.local/pg-test' -p 55432 -h 127.0.0.1
```

O cluster de teste sem senha escuta apenas em loopback. Para a instância de desenvolvimento, use o modo portátil autenticado descrito no README principal.

As portas padrão do teste são 58080 (API) e 51883 (MQTT). O runner registra os resultados em `.local/integration-<id>`. Com `-JavaFX`, também valida login, navegação, FXML, operação das duas estações e emissão simulada pelo terminal. O runner aceita `-AndroidSerial emulator-5556` para executar o teste instrumentado em um Android dedicado de QA.

## Cenários executados

| Cenário | Evidência |
|---|---|
| A. Bar | Somente bebidas chegaram ao Bar. Resultado CONFIRMADO, código 11 |
| B. Cozinha | Somente alimentos chegaram à Cozinha. Impressão simulada e resultado 11 |
| C. Misto | Itens separados e consolidados após as duas confirmações. Resultado 11 |
| D. Cozinha com erro | Bar confirmado, resultado PARCIAL com código 01 |
| E. Bar com erro | Cozinha confirmada, resultado PARCIAL com código 10 |
| F. Ambos com erro | Resultado ERRO com código 00 |
| G. Impressora indisponível | Falha persistida, retentativa, cópia identificada e deduplicada |
| H. Terminal desconectado | Fila disponível por REST e entrega após o retorno da sessão MQTT |

A automação também verifica login inválido, papel incorreto, cadastro de produto e mesa, atribuição de comanda, preços, rejeição persistida, duplicidade de pedido, evento atrasado, fechamento com consistência do total, relatório e histórico. PostgreSQL 17.9 e Eclipse Mosquitto 2.0.22 foram usados nos testes em Windows. A compilação do Backend foi conferida no BlueJ. As 18 interfaces FXML foram carregadas pelo JavaFX e o fluxo misto foi confirmado acionando os controles reais dos terminais.

O Gradle wrapper gerou o APK de depuração e o APK de release sem assinatura. Os testes JVM passaram e o lint debug foi executado. Em Android 35, o teste instrumentado fez login, selecionou mesa e produto, revisou o valor, enviou o pedido por MQTT, recebeu a confirmação do Bar e encontrou o pedido no histórico. O log registrou `OK (1 test)`.

As evidências locais da validação JavaFX e Android foram gravadas em `.local/integration-20cac6bf6fd1480ba9f0db8c44b351b9` e `.local/integration-eaf5562deece44d98d73f020fdbf5f42`. O teste físico com a impressora USB depende da fila e do equipamento da cozinha. Consulte o [manual de impressão](../00%20-%20Documentacao%20Geral/Manuais/Impressora.md).

A instância portátil passou por inicialização, verificação de PostgreSQL e MQTT, login e encerramento de sessão. O YAML do Compose foi validado por leitura estrutural. A execução do Compose e o uso do Scene Builder não foram verificados neste computador.
