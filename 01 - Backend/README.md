# Backend Java SE

Abra esta pasta no BlueJ, rode `Build.ps1` antes para obter os JARs de JDBC, Gson e Eclipse Paho em `+libs`, e abra o pacote `atlas`. Ponto de entrada `atlas.Backend.main`.

Na raiz, execute `& '.\07 - Infraestrutura\Scripts\Run.ps1' -Module Backend` com a infraestrutura iniciada. A API usa `/api/v1/`; a migration roda no primeiro start. Para iniciar pelo BlueJ com a configuração de rede e banco, veja o [guia](../00%20-%20Documentacao%20Geral/Manuais/Instalacao.md).

As classes de consolidação podem ser estudadas em `atlas.pedidos`; serviços, repositórios, controladores, autenticação e MQTT têm pacotes próprios. Consulte os [testes de integração](../08%20-%20Testes/README.md).
