# Auditoria de origem

O diretório oficial existia e estava vazio no início. O clone local trouxe o histórico e as referências remotas sem checkout na raiz. A árvore de `Front` continha `AndroidApp` e um README de contexto acadêmico. A implementação Android foi extraída da revisão `1c2b8f314d42479b8c2ac0f477ac538943abab48` para `03 - Mobile/AndroidApp`. O repositório remoto fica como referência de origem.

| Origem | Achado | Integração |
|---|---|---|
| `MainActivity`, `activity_main.xml`, `nav_graph.xml` | NavHost, Fragments, splash e tratamento de insets | Host e nomes de telas preservados. Ajustes no grafo para o atendimento centralizado |
| `LoginFragment`, `SessaoUsuario` | Código e senha fixos, sessão estática | Login REST, papel de garçom e credencial MQTT individual. Token em memória |
| `MesaFragment`, `EstadoMesas` | Quatro mesas e ocupação local | Mesas dinâmicas agrupadas por área, estados derivados da comanda persistida |
| `Cardapio`, `ProdutoMenu`, `CardapioFragment` | Produtos estáticos, setor usado como filtro comercial e valores em double | Catálogo REST, categorias e estações independentes, BigDecimal, busca, imagens e quantidade |
| `ComandaTemporaria`, `ComandaFragment` | Itens locais por mesa e comanda em memória | Rascunho local por tela, consumo e total oficial no servidor, revisão em painel inferior, envio idempotente |
| `PagamentoFragment` | Pagamento simulado no telefone | Solicitação de conta no Android, conferência e pagamento registrado na interface Caixa |
| `AdministracaoFragment` | Inclusão e remoção de produtos em listas locais | Gestão de produtos e funcionários no Desktop, com autorização no Backend |
| Manifesto e recursos | Layouts XML, ícones, tema e recursos Android | Mantidos quando reaproveitáveis. Tema e splash associados à marca ATLAS. Acesso de rede e backup de dados operacionais ajustados |
| Gradle e testes | Wrapper 9.6, AGP 9.4.1/SDK 37 na revisão e testes de exemplo | Gradle 8.11.1, AGP 8.9.2 e SDK 35, verificados com Java 21. Teste unitário do rascunho |
| `master`, `exp`, `traducao-pt-br` | Domínio OO acadêmico, comanda, estados de mesa, divisão cozinha/salão e classe de pagamento incompleta | Reutilização dos conceitos operacionais. Adaptados para transações, histórico de preços e múltiplos terminais |
| `main`, `dev`, `beta` | Árvores sem serviços Java rastreados nas referências auditadas | Backend, API, ACL, esquema e impressão foram implementados no ATLAS |

## Referências visuais

Foram inspecionadas as sete páginas do Figma, os inventários de estados e as telas representativas de acesso, início, pedidos, salão, menu, funcionários, caixa, relatórios, histórico e configurações. O Desktop usa os recursos de marca e ícones exportados do Figma, a navegação lateral recolhível e a paleta Atlas. As telas usam controles JavaFX e dados reais. A v1 cobre Bar e Cozinha. O salão usa estados por mesa e a gerência acompanha falhas de impressão no panorama. O mapa de assentos, Higienização, Padaria e os estados ilustrativos sem regra correspondente ficaram fora desta implementação. [Figma Atlas Desktop](https://www.figma.com/proto/qn1c28a2EVnptWWzrP09xT/Atlas-Desktop?node-id=3-2)

A imagem das três telas Mobile foi citada no texto de requisitos e o conjunto de anexos recebido continha dois arquivos de texto. A adaptação usa a estrutura descrita para seleção de mesas, catálogo horizontal por categoria e revisão no painel inferior. A comparação visual com a imagem ainda depende do arquivo.

Os SVG e versões rasterizadas dos ícones ficam em `02 - Desktop/resources/icons`. Os ícones Phosphor seguem a licença do projeto original. [Phosphor Icons](https://github.com/phosphor-icons/core)
