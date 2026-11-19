# ATLAS Garçom

Abra esta pasta no Android Studio com Gradle JDK 17 ou 21 e Android SDK 35. O código das telas e do cliente é Java; layouts, tema e grafo de navegação são XML. O Gradle wrapper fixa a versão 8.11.1 e sua soma SHA-256.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

O Android Studio instala o APK pelo botão Run. O APK de depuração também fica em `app/build/outputs/apk/debug/app-debug.apk`. Faça login com perfil GARCOM, endereço da API, endereço MQTT e as duas credenciais individuais. Use `10.0.2.2` para o host do emulador Android padrão, conforme o [guia de rede](../../00%20-%20Documentacao%20Geral/Manuais/Instalacao.md).

Selecione a mesa, consulte a comanda, abra o cardápio, adicione itens e toque em **Revisar pedido**. Inclua observações e envie com **PEDIR**. Toque em **Acompanhar pedido** para ver o retorno da produção. Se a entrega estiver pendente, o reenvio reaproveita o mesmo identificador. A tela **Meus pedidos** permite consultar os próprios registros e marcar a entrega. **Solicitar conta** encaminha o fechamento ao caixa.
