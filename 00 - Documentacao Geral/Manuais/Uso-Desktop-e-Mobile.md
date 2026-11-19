# Guia de uso do ATLAS no computador e no Android

Este guia usa um único computador Windows para rodar o servidor, o Desktop, os terminais de produção e um Android emulado. Você vai cadastrar uma mesa, enviar um pedido pelo Mobile, confirmar os itens no Bar e na Cozinha e receber o pagamento no Desktop.

## O que roda em cada lugar

| Aplicação | Onde roda | Perfil | O que fazer |
| --- | --- | --- | --- |
| **ATLAS Desktop** | Janela JavaFX no Windows | `GERENTE` ou `CAIXA` | Cadastrar mesas, produtos e funcionários com a gerência. Acompanhar pedidos, consultar a fila de contas e registrar pagamentos. |
| **ATLAS Mobile** | Android Emulator no Windows ou aparelho Android | `GARCOM` | Selecionar mesa, montar o pedido, acompanhar produção, marcar entrega e solicitar a conta. |
| **Terminal Bar** | Janela JavaFX no Windows | `BAR` | Preparar os itens atribuídos ao Bar e registrar o resultado. |
| **Terminal Cozinha** | Janela JavaFX no Windows | `COZINHA` | Preparar os itens atribuídos à Cozinha e acompanhar a impressão. |
| **Backend, PostgreSQL e Mosquitto** | Computador Windows | Serviço | Compartilhar o estado entre todas as telas. O Backend aplica as regras, o banco persiste os dados e o broker MQTT transporta pedidos e atualizações. |

O Desktop e o Mobile têm funções complementares e usam o mesmo servidor. Para testar um pedido do início ao fim, mantenha também os terminais de produção abertos. O BlueJ é opcional para estudar os módulos Java SE. Para compilar, instalar e executar o Mobile, use o Android Studio ou as ferramentas do Android SDK.

## 1. Preparar o computador

O exemplo usa o modo portátil. Para ele, confirme que os binários do PostgreSQL e do Mosquitto estão em `.local/pgsql/bin` e `.local/mosquitto`. Também é preciso ter Java 21 e JavaFX. Os scripts reconhecem a instalação padrão do BlueJ em `C:\Program Files\BlueJ`. Em outra instalação, configure `JAVA_HOME` e `ATLAS_JAVAFX_LIB`. Para preparar os binários ou usar Docker Compose, consulte o [guia de instalação e rede](Instalacao.md).

No PowerShell, entre na raiz do projeto. Faça isso também nas outras janelas de PowerShell usadas pelo guia.

```powershell
Set-Location -LiteralPath 'C:\Users\pithe\Documents\Atlas\Atlas v.1.0'
& '.\07 - Infraestrutura\Scripts\Build.ps1'
```

Verifique a API antes de iniciar o servidor. Se o comando já retornar `database` e `mqtt` como `OK`, siga para o Desktop.

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/health' -TimeoutSec 3
```

Se a instância portátil estiver parada, inicie e repita a verificação.

```powershell
& '.\07 - Infraestrutura\Scripts\Start-Portable.ps1'
Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/health' -TimeoutSec 3
```

Na primeira inicialização portátil, o ATLAS prepara um banco vazio. O usuário inicial é `gerencia`. As senhas da **API** e do **MQTT** ficam no arquivo local `.local/portable/gerencia.txt`. Cada campo de login usa a senha correspondente.

## 2. Abrir e configurar o Desktop

Abra o Desktop em uma janela própria do PowerShell.

```powershell
& '.\07 - Infraestrutura\Scripts\Run.ps1' -Module Desktop
```

Na tela de login, informe o código `gerencia`, as duas senhas geradas e os endereços abaixo. A tabela inclui os valores do Mobile para a etapa seguinte.

| Campo | Desktop e terminais no Windows | Mobile no Android Emulator padrão |
| --- | --- | --- |
| Backend / API | `http://127.0.0.1:8080` | `http://10.0.2.2:8080` |
| Broker MQTT | `tcp://127.0.0.1:1883` | `tcp://10.0.2.2:1883` |

Dentro do emulador, `127.0.0.1` aponta para o próprio Android. O endereço `10.0.2.2` alcança o loopback do Windows e permite que as telas usem o mesmo Backend e broker. [Rede do Android Emulator](https://developer.android.com/studio/run/emulator-networking-address)

### Criar os registros do teste

1. Em **Salão → Nova mesa**, crie **Mesa QA** na área **Salão**. Deixe **Mesa disponível** marcado.
2. Em **Menu → Novo produto**, crie **Suco QA**, categoria **Bebidas**, estação `BAR`, preço `8,00`. Crie **Sanduíche QA**, categoria **Lanches**, estação `COZINHA`, preço `20,00`. Deixe **Disponível para pedidos** marcado nos dois.
3. Em **Funcionários → Novo funcionário**, cadastre `garcom-qa` com cargo `GARCOM`, `bar-qa` com `BAR` e `cozinha-qa` com `COZINHA`. Deixe o acesso ativo e escolha uma senha de API de pelo menos 12 caracteres para cada conta. Se quiser testar o perfil de caixa em separado, cadastre também `caixa-qa` com cargo `CAIXA`.

### Criar os acessos MQTT

O cadastro no Desktop cria a conta da API. Em outra janela do PowerShell, crie também a credencial MQTT de cada funcionário. Use o mesmo código informado no cadastro. O script pede a senha do broker.

```powershell
& '.\07 - Infraestrutura\Scripts\Provision-Mqtt.ps1' -User garcom-qa -Role GARCOM
& '.\07 - Infraestrutura\Scripts\Provision-Mqtt.ps1' -User bar-qa -Role BAR
& '.\07 - Infraestrutura\Scripts\Provision-Mqtt.ps1' -User cozinha-qa -Role COZINHA
```

Se cadastrou um caixa, provisione o mesmo código para ele.

```powershell
& '.\07 - Infraestrutura\Scripts\Provision-Mqtt.ps1' -User caixa-qa -Role CAIXA
```

Depois de cadastrar as credenciais, reinicie a instância portátil para o broker carregar as permissões. Faça essa etapa antes de enviar os pedidos de teste.

```powershell
& '.\07 - Infraestrutura\Scripts\Start-Portable.ps1' -Action Stop
& '.\07 - Infraestrutura\Scripts\Start-Portable.ps1'
Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/health' -TimeoutSec 3
```

Abra o Bar e a Cozinha em duas janelas diferentes. Entre com as contas correspondentes, informando as senhas da API e do MQTT e usando os endereços da coluna Windows.

```powershell
& '.\07 - Infraestrutura\Scripts\Run.ps1' -Module Bar
```

```powershell
& '.\07 - Infraestrutura\Scripts\Run.ps1' -Module Cozinha
```

Se o Desktop perder a conexão durante a reinicialização e não retomar a sessão, abra-o novamente e faça login.

## 3. Rodar o Mobile no próprio computador

### Pelo Android Studio

1. Abra a pasta **03 - Mobile/AndroidApp** como projeto. O projeto usa **Gradle 8.11.1**, **Android Gradle Plugin 8.9.2** e **Android SDK 35**. Use JDK 17 ou 21 para o Gradle.
2. No SDK Manager, instale a plataforma Android 35, o **Android Emulator** e o **Android SDK Platform-Tools**, se estiverem faltando.
3. Em **View → Tool Windows → Device Manager**, use **+ → Create Virtual Device**. Selecione um modelo de telefone, escolha uma imagem de sistema Android 35 e conclua a criação. Inicie o dispositivo virtual. [Criar um dispositivo virtual Android](https://developer.android.com/studio/run/managing-avds)
4. Selecione o emulador como destino e clique em **Run** para compilar e instalar a variante de depuração. Abra o app, informe os endereços da coluna do emulador e entre como `garcom-qa`, usando as duas senhas individuais.

Na variante de depuração, estes endereços locais funcionam mesmo quando os campos da tela exibem os rótulos **Endereço HTTPS do Backend** e **Endereço SSL do broker**.

### Pelo APK de depuração

Com um emulador já iniciado, você também pode instalar o APK sem compilar pela interface do Android Studio. Se faltar o arquivo, gere-o a partir da raiz. O comando abaixo reaproveita o SDK e os caches locais quando estão disponíveis. Em outro ambiente, configure o SDK instalado em `ANDROID_HOME` ou pelo Android Studio.

```powershell
$sdkLocal = Join-Path (Get-Location).Path '.local\android-sdk'
if (!$env:ANDROID_HOME -and (Test-Path -LiteralPath $sdkLocal)) {
    $env:ANDROID_HOME = $sdkLocal
}
if (!$env:JAVA_HOME -and (Test-Path -LiteralPath 'C:\Program Files\BlueJ\jdk\bin\java.exe')) {
    $env:JAVA_HOME = 'C:\Program Files\BlueJ\jdk'
}
if (!$env:GRADLE_USER_HOME -and (Test-Path -LiteralPath '.local/gradle-home')) {
    $env:GRADLE_USER_HOME = (Resolve-Path -LiteralPath '.local/gradle-home').Path
}
if (!$env:ANDROID_USER_HOME -and (Test-Path -LiteralPath '.local/android-user')) {
    $env:ANDROID_USER_HOME = (Resolve-Path -LiteralPath '.local/android-user').Path
}

Push-Location -LiteralPath '.\03 - Mobile\AndroidApp'
try {
    & '.\gradlew.bat' :app:assembleDebug
} finally {
    Pop-Location
}
```

Em um PowerShell na raiz, localize o `adb.exe`. O exemplo usa o SDK preparado em `.local/android-sdk` quando disponível e aceita `ANDROID_HOME` ou um `adb` no PATH.

```powershell
$adb = '.\.local\android-sdk\platform-tools\adb.exe'
if (!(Test-Path -LiteralPath $adb) -and $env:ANDROID_HOME) {
    $adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
}
if (!(Test-Path -LiteralPath $adb)) {
    $adb = (Get-Command adb.exe -ErrorAction Stop).Source
}
& $adb devices
```

Use o serial que aparece com estado `device`. Troque `emulator-5556` no exemplo se o seu serial for outro.

```powershell
$serial = 'emulator-5556'
& $adb -s $serial install -r '.\03 - Mobile\AndroidApp\app\build\outputs\apk\debug\app-debug.apk'
```

Abra o ATLAS no emulador. [Listar dispositivos e instalar APKs pelo ADB](https://developer.android.com/tools/adb)

### Se o SDK e o emulador de QA já existem em `.local`

Neste computador, o dispositivo virtual `AtlasQA` foi criado durante a validação anterior e está cadastrado em `.local/avd`. Execute em uma janela própria do PowerShell, a partir da raiz. Use o comando de abertura somente se `AtlasQA` aparecer na lista.

```powershell
$env:ANDROID_HOME = (Resolve-Path -LiteralPath '.local/android-sdk').Path
$env:ANDROID_USER_HOME = (Resolve-Path -LiteralPath '.local/android-user').Path
$env:ANDROID_EMULATOR_HOME = $env:ANDROID_USER_HOME
$env:ANDROID_AVD_HOME = (Resolve-Path -LiteralPath '.local/avd').Path
$emulator = Join-Path $env:ANDROID_HOME 'emulator\emulator.exe'
& $emulator -list-avds
& $emulator -avd AtlasQA
```

### Em um celular físico

O celular precisa alcançar a API e o broker por um endereço de rede do computador. Para esse cenário, configure a escuta e os endereços de rede conforme o [guia de instalação, rede e TLS](Instalacao.md#outros-computadores-e-android). O modo portátil deste guia escuta em loopback. O endereço `10.0.2.2` se aplica ao Android Emulator e a versão release exige HTTPS e MQTT sobre TLS. [Rede do Android Emulator](https://developer.android.com/studio/run/emulator-networking-address)

## 4. Testar Desktop e Mobile juntos, manualmente

Use uma comanda de teste e execute o fluxo nesta ordem.

| Etapa | O que fazer | O que você deve ver |
| --- | --- | --- |
| **1. Enviar pelo Mobile** | Entre como `garcom-qa`, selecione **Mesa QA**, abra o cardápio, adicione 1 **Suco QA** e 1 **Sanduíche QA**. Toque em **Revisar pedido** e depois em **PEDIR**. | Total de **R$ 28,00** na revisão. Depois do envio, **Acompanhar pedido** mostra o recebimento ou a produção em andamento. |
| **2. Conferir no Desktop** | Como gerência, abra **Pedidos** e procure a mesa ou o número do pedido. | O mesmo pedido aparece vinculado à mesa e ao garçom. |
| **3. Produzir no Bar e na Cozinha** | Em cada terminal, selecione o pedido. Use **Iniciar preparo** e, ao concluir, **Marcar pronto**. | O Bar recebe o suco e a Cozinha recebe o sanduíche. Se a impressão estiver em modo simulado, o comprovante é gravado em `.local/comprovantes` por padrão. |
| **4. Confirmar no Mobile** | Abra **Acompanhar pedido** ou consulte **Meus pedidos**. Se quiser testar a entrega, abra os detalhes e escolha **Marcar entregue**. | O pedido fica **Confirmado** quando as duas estações concluem. O histórico mantém o mesmo número do pedido. |
| **5. Fechar pelo Desktop** | No Mobile, volte à comanda e toque em **Solicitar conta**. No Desktop, com Gerência ou Caixa, abra **Contas solicitadas**, selecione a comanda e escolha **Revisar fechamento**. Selecione a forma de pagamento e confirme. | O Desktop calcula **R$ 28,00**, registra o pagamento, fecha a comanda e libera a mesa. O lançamento registra a forma de pagamento informada no ATLAS. |

Para testar falha parcial, abra outra comanda ou use outra mesa. Envie um pedido misto, conclua o Bar, use **Informar falha** na Cozinha e informe um motivo. O Mobile deve receber um resultado parcial, e somente o item concluído pelo Bar deve entrar na conta. [Estados de pedido e consolidação MQTT](../Arquitetura/MQTT.md)

**Fechar caixa** é uma ação posterior no Desktop. Ela consolida vários pagamentos já registrados. Para testar apenas a saída da mesa e o fechamento da comanda, basta registrar o pagamento na etapa 5.

## 5. Rodar os testes automatizados no mesmo computador

O teste manual usa os cadastros e a instância portátil. O runner automatizado cria contas e dados próprios, um banco de teste e portas separadas. Com `-JavaFX`, ele testa o Desktop e os terminais. Com `-AndroidSerial`, também instala o APK de debug e o APK de teste no dispositivo indicado e executa o fluxo instrumentado. Use um emulador dedicado, pois a instalação de debug pode atualizar o app que já está nele.

1. Na raiz, compile o projeto com `Build.ps1`. Inicie o PostgreSQL de teste em `55432`, conforme o [procedimento para criar o cluster de teste](../../08%20-%20Testes/README.md#executar), e deixe a janela do PostgreSQL aberta.
2. Inicie o emulador e confirme que aparece como `device` em `adb devices`. Se usar outro SDK, ajuste `ANDROID_HOME` no mesmo PowerShell que chamará o runner. Com o SDK local, o runner encontra `.local/android-sdk`.
3. Em outro PowerShell, execute da raiz. Substitua o serial de exemplo pelo exibido pelo ADB.

```powershell
$serial = 'emulator-5556'
& '.\08 - Testes\Integracao\Run-Integration.ps1' `
  -PgBin '.local/pgsql/bin' `
  -MosquittoBin '.local/mosquitto' `
  -JavaFX `
  -AndroidSerial $serial
```

O runner usa por padrão a API em `58080` e o MQTT em `51883`. Ele encaminha as portas para o Android automaticamente, por isso o teste instrumentado tem configuração própria. Procure pelos resultados de integração e por `OK (1 test)` no Android. As evidências e comprovantes ficam em `.local/integration-<id>`. Para executar apenas Backend e JavaFX, use o mesmo comando sem a opção `-AndroidSerial`.

## Problemas comuns

| Sintoma | O que verificar |
| --- | --- |
| O Desktop abre, mas a API dá erro de conexão | Rode `Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/health'` no Windows. Se falhar, verifique a inicialização e os logs em `.local/portable`. |
| Funciona no Windows, mas o Mobile não conecta | No Android Emulator padrão, confira `10.0.2.2` nos dois endereços e use o APK de depuração para o roteiro em HTTP e TCP locais. |
| Login da API funciona, mas o MQTT fica desconectado | Confirme se o mesmo código foi provisionado no broker, se a senha informada é a do MQTT e se o broker foi reiniciado após a alteração. |
| Terminal vazio ou item na estação errada | Confira o perfil do terminal, o campo **Estação de produção** no produto e se o pedido foi aceito pelo Backend. Use **Atualizar** no terminal para recarregar a fila. |
| O caixa não consegue fechar a comanda | Confirme que a conta foi solicitada e que todas as estações envolvidas finalizaram. Se o total mudou, reabra a comanda e revise o valor. |
| O ADB mostra `offline` | Aguarde o emulador terminar de iniciar e repita `adb devices`. Use o serial do dispositivo listado como `device`. |

## Encerrar

Feche as janelas Desktop, Bar e Cozinha e desligue o emulador pelo Device Manager ou pela janela do Android Emulator. Se você iniciou a instância portátil para o teste, encerre-a na raiz do projeto. O encerramento preserva o banco local.

```powershell
& '.\07 - Infraestrutura\Scripts\Start-Portable.ps1' -Action Stop
```

Para aprofundar a configuração, veja o [README principal](../../README.md), o [guia de instalação e rede](Instalacao.md), o [README do Android](../../03%20-%20Mobile/AndroidApp/README.md) e o [relatório de testes](../../08%20-%20Testes/README.md).
