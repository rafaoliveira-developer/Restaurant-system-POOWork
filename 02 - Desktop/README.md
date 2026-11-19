# ATLAS Desktop

Na raiz execute `Build.ps1`, inicie a infraestrutura e rode `& '.\07 - Infraestrutura\Scripts\Run.ps1' -Module Desktop`. Use um funcionário com perfil GERENTE ou CAIXA e as credenciais correspondentes no broker.

No BlueJ, abra esta pasta e compile após gerar `+libs`. Ponto de entrada `atlas.desktop.Desktop.main`. Os FXML ficam em `resources/fxml`, agrupados por módulo, e podem ser abertos pelo Scene Builder. A folha `resources/css/central.css` contém os estilos específicos. Os recursos visuais vêm do [Figma Atlas Desktop](https://www.figma.com/proto/qn1c28a2EVnptWWzrP09xT/Atlas-Desktop?node-id=3-2).

Com a conta da gerência, cadastre mesas, produtos e funcionários. Acesse Pedidos para acompanhar preparo, detalhes e entrega. Em Salão selecione uma mesa pelo mapa ou lista. Contas solicitadas abre a revisão de consumos e confirma o recebimento no caixa.
