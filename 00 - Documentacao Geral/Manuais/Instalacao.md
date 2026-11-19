# Instalação, BlueJ e rede

## Ferramentas

- Windows PowerShell ou PowerShell 7, JDK 21, OpenJFX para Windows e Git.
- O BlueJ com o JDK e JavaFX incluídos atende a compilação Java SE. Ajuste `JAVA_HOME` e `ATLAS_JAVAFX_LIB` se os SDKs estiverem em outra pasta. Os scripts usam JavaFX 23.0.2 do BlueJ neste computador.
- Android Studio compatível com AGP 8.9.2 e Android SDK 35 para o Mobile. O projeto fixa Gradle 8.11.1, confira o [README do Android](../../03%20-%20Mobile/AndroidApp/README.md).
- Docker Compose ou os binários Windows de PostgreSQL 17 e Mosquitto 2 para execução local.

## Instância portátil

Na raiz, rode `Build.ps1` e `Start-Portable.ps1` conforme o README principal. Os binários portáteis residem em `.local/pgsql/bin` e `.local/mosquitto`. As portas locais são 55433 (banco), 1883 (broker) e 8080 (API). O setup portátil cria `.env`, contas iniciais e armazena dados em `.local/portable`. Use as credenciais geradas para cadastrar mesas, produtos e pessoas no Desktop.

Para cada conta operacional, execute um provisionamento e reinicie a infraestrutura portátil.

```powershell
& '.\07 - Infraestrutura\Scripts\Provision-Mqtt.ps1' -User garcom-01 -Role GARCOM
& '.\07 - Infraestrutura\Scripts\Provision-Mqtt.ps1' -User bar-01 -Role BAR
& '.\07 - Infraestrutura\Scripts\Provision-Mqtt.ps1' -User cozinha-01 -Role COZINHA
& '.\07 - Infraestrutura\Scripts\Start-Portable.ps1' -Action Stop
& '.\07 - Infraestrutura\Scripts\Start-Portable.ps1'
```

Crie as contas no Desktop com os mesmos códigos. No login de cada interface, forneça a senha da API e a senha MQTT desse usuário. Adicione o perfil CAIXA do mesmo modo. O cadastro de funcionários no PostgreSQL e o provisionamento do broker são operações distintas. Guarde `.env` e `.local/portable/gerencia.txt` em acesso restrito.

## Docker Compose

Copie `.env.example` para `.env`. Escolha os segredos de PostgreSQL, da conta administrativa da API e do Backend MQTT. `POSTGRES_PASSWORD` e `ATLAS_DB_PASSWORD` devem corresponder. Provisione uma credencial `atlas-backend` com perfil BACKEND usando a mesma senha de `ATLAS_MQTT_PASSWORD`. Provisione também o usuário de `ATLAS_ADMIN_USER` no perfil GERENTE.

```powershell
& '.\07 - Infraestrutura\Scripts\Provision-Mqtt.ps1' -User atlas-backend -Role BACKEND
& '.\07 - Infraestrutura\Scripts\Provision-Mqtt.ps1' -User gerencia -Role GERENTE
docker compose --env-file .env -f '07 - Infraestrutura/Docker/compose.yaml' up -d
& '.\07 - Infraestrutura\Scripts\Build.ps1'
& '.\07 - Infraestrutura\Scripts\Run.ps1' -Module Backend
```

Depois de provisionar outras contas, use `docker compose --env-file .env -f '07 - Infraestrutura/Docker/compose.yaml' restart mosquitto`. Os volumes do Compose guardam o PostgreSQL e a sessão do Mosquitto.

## Outros computadores e Android

Defina `ATLAS_BIND` com o endereço de escuta do servidor e configure `ATLAS_TLS_KEYSTORE` (PKCS12), `ATLAS_TLS_PASSWORD` e o certificado válido para o nome DNS da operação. Para Mosquitto, coloque os certificados em `07 - Infraestrutura/MQTT/tls`, copie `mosquitto.tls.example` para `mosquitto.tls.conf` e defina `ATLAS_MQTT_CONFIG=mosquitto.tls.conf` e mapeie a porta de escuta de rede com `ATLAS_MQTT_BIND` e `ATLAS_MQTT_PORT`. Ajuste `ATLAS_MQTT_URI` do Backend para `ssl://servidor:8883`. O dispositivo precisa confiar nos certificados do Backend e do broker. O APK de release exige HTTPS e MQTT SSL.

Em ambiente isolado de desenvolvimento, `ATLAS_ALLOW_LAN_HTTP=true` permite escutar HTTP fora do loopback. No emulador Android padrão use `http://10.0.2.2:8080` e `tcp://10.0.2.2:1883` para alcançar o host. Para aparelho físico em rede local, use o DNS/IP do PC central e libere no firewall apenas as portas da API e do broker da operação. Mantenha o PostgreSQL no loopback ou em rede privada de infraestrutura. Os clientes desktop têm campos próprios para os endereços. A aplicação da Cozinha roda no mesmo computador da impressora.

## BlueJ e Scene Builder

Rode `Build.ps1` antes para popular `+libs` de cada projeto. No BlueJ, abra a pasta do módulo que contém `package.bluej`. Use `Compile` e abra o pacote `atlas`. Para executar o Backend pelo BlueJ com os endereços locais, inicie o BlueJ pelo script `Open-BlueJ.ps1 -Module Backend` para herdar o ambiente e traga o PostgreSQL e o broker primeiro. Execute `atlas.Backend.main(String[])` com `{}`. Execute apenas uma instância do Backend por porta. Os pontos de entrada JavaFX constam nos READMEs locais. O BlueJ inclui JavaFX; se estiver usando um editor externo, o script `Run.ps1` configura os módulos JavaFX.

No Scene Builder, use **Arquivo → Abrir** e selecione um `.fxml` dentro de `resources/fxml` ou `resources/atlasfx` do módulo. Layouts usam controles JavaFX padrão e `fx:id` para ligação aos controladores. Após editar, rode `Build.ps1` para recarregar recursos também no BlueJ. [BlueJ FAQ](https://www.bluej.org/faq.html)
