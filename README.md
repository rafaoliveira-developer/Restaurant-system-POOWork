# Sistema de Gerenciamento de Restaurante

Sistema de gerenciamento de restaurante desenvolvido para a disciplina de **Programação Orientada a Objetos (POO)** da **Universidade Federal de Goiás (UFG)**.

O projeto tem como objetivo desenvolver uma aplicação para gerenciar operações básicas de um restaurante, aplicando conceitos fundamentais de orientação a objetos em Java.

## Tecnologias

- Java 15 ou superior
- Programação Orientada a Objetos
- Git
- GitHub

## Funcionalidades e componentes

O projeto está em desenvolvimento e contém classes que representam:

- Restaurante
- Mesas
- Garçons
- Produtos
- Alimentos
- Bebidas
- Cardápio
- Comandas
- Itens solicitados
- Cozinha
- Pedidos da cozinha
- Pagamentos

O sistema também possui enumerações para controlar as categorias dos produtos e os estados das mesas, comandas e pedidos da cozinha.

## Estrutura do projeto

```text
src/
├── Principal.java
└── com/
    └── poo/
        └── modelos/
            ├── Comanda.java
            ├── Bebida.java
            ├── CategoriaBebida.java
            ├── Alimento.java
            ├── CategoriaAlimento.java
            ├── Cozinha.java
            ├── PedidoCozinha.java
            ├── StatusPedidoCozinha.java
            ├── Cardapio.java
            ├── Pagamento.java
            ├── Produto.java
            ├── ItemSolicitado.java
            ├── Restaurante.java
            ├── StatusComanda.java
            ├── Mesa.java
            ├── StatusMesa.java
            └── Garcom.java
```

## Como executar

É necessário ter o JDK 15 ou superior instalado.

Na raiz do projeto, compile os arquivos:

```bash
javac -encoding UTF-8 -d out src/Principal.java src/com/poo/modelos/*.java
```

Em seguida, execute a aplicação:

```bash
java -cp out Principal
```

## Objetivo acadêmico

O principal objetivo do projeto é aplicar conceitos de Programação Orientada a Objetos em uma aplicação prática, incluindo:

- Classes e objetos
- Encapsulamento
- Herança
- Polimorfismo
- Enumerações
- Coleções
- Pacotes
- Relacionamentos entre classes

## Autoria

Desenvolvido por estudantes da **Universidade Federal de Goiás (UFG)** como parte da disciplina de Programação Orientada a Objetos.

## Situação do projeto

Em desenvolvimento.
