# Infraestrutura

- `Docker/compose.yaml` inicia PostgreSQL e Eclipse Mosquitto.
- `Banco de Dados/` contém as migrations aplicadas pelo Backend.
- `MQTT/` contém o modelo de configuração e ACL.
- `Scripts/` compila, executa, abre no BlueJ, provisiona o Mosquitto e inicializa a instância portátil Windows.

Siga o [guia de instalação e rede](../00%20-%20Documentacao%20Geral/Manuais/Instalacao.md). O Backend acessa o banco local, e os demais componentes usam REST e MQTT.
