# Testes Android

O Gradle do AndroidApp contém testes do rascunho com precisão em centavos, quantidade e reconciliação de catálogo. O teste instrumentado `FluxoAtlasTest` usa Espresso, PostgreSQL, Mosquitto e o Backend de integração.

Use exclusivamente um Android dedicado de teste com ADB, informe seu serial ao `Run-Integration.ps1 -AndroidSerial <serial>` e aguarde a instalação dos APKs de depuração e teste. O runner encaminha temporariamente as portas da API e MQTT por ADB. Consulte o [guia de validação](../README.md).

O fluxo instrumentado passou em um emulador Android 35 com API, PostgreSQL e Mosquitto reais. A comparação com a imagem visual de referência continua dependente do anexo.
