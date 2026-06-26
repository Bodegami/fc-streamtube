# Software Architecture Patterns Guide

## Padrões Arquiteturais: Hexagonal, Onion e BFF

**O que é e quando carregar este arquivo:**
Este contexto define padrões estruturais para isolamento de domínio e otimização de entrega de dados para clientes. **Carregue este arquivo** sempre que o usuário mencionar *Ports and Adapters*, Arquitetura Hexagonal, Onion Architecture, Backend For Frontend (BFF), API Gateways específicos por cliente, interfaces de entrada/saída, ou como estruturar a comunicação de microsserviços com diferentes interfaces de usuário (ex: App Mobile vs. Painel Web).

---

## 1. Arquitetura Hexagonal (Ports and Adapters)
Foca em criar simetria entre o que "aciona" a aplicação e o que a aplicação "precisa acionar", mantendo o núcleo agnóstico.
* **Core (Núcleo):** Onde reside a lógica de negócio. Não conhece frameworks de web ou banco de dados.
* **Ports (Portas):** Interfaces que definem os contratos de comunicação.
    * **Driving Ports (Portas de Entrada/Primárias):** Como o mundo externo interage com a aplicação (ex: interface `UseCases`).
    * **Driven Ports (Portas de Saída/Secundárias):** Como a aplicação interage com o mundo externo (ex: interface `Repository` ou `ExternalClient`).
* **Adapters (Adaptadores):** Implementações concretas das portas.
    * **Driving Adapters:** Ex: Controllers REST em Spring/Micronaut, Listeners de Kafka, ou CLI. Eles invocam as Portas de Entrada.
    * **Driven Adapters:** Ex: Implementações de repositórios JPA, clientes HTTP para APIs externas. Eles implementam as Portas de Saída.

## 2. Onion Architecture
Estritamente focada em Inversão de Dependência (DIP) aliada ao Domain-Driven Design (DDD). Visualizada como camadas de uma cebola onde tudo aponta para o centro.
* **Camadas Internas (Domínio):** Modelos de Domínio e Serviços de Domínio. Representam o estado e as regras fundamentais.
* **Camadas Intermediárias (Aplicação):** Serviços de Aplicação (orquestram as transações de domínio e casos de uso).
* **Camada Externa (Infraestrutura/UI):** Bancos de dados, testes, UI, frameworks. 
* **Regra de Ouro:** O código compilado das camadas internas não pode possuir dependências (imports) das camadas externas. A infraestrutura é "injetada" em tempo de execução.

## 3. Backend For Frontend (BFF)
Padrão de borda (Edge) que dita que cada experiência de usuário distinta deve ter seu próprio backend customizado, em vez de uma única API "tamanho único" para todos.
* **Foco na UI:** Um `MobileCartBFF` entregará um payload enxuto, otimizado para redes móveis, enquanto um `WebAdminBFF` entregará dados agregados ricos para dashboards.
* **Orquestração e Agregação:** O BFF chama diversos microsserviços internos, agrega os dados e os formata no formato exato que a tela precisa.
* **Sem Regras de Negócio:** O BFF **nunca** deve conter regras de negócio de domínio. Ele lida apenas com lógica de apresentação, roteamento e formatação.

## 4. Padrões Obrigatórios de Avaliação
Ao revisar ou sugerir arquiteturas com esses padrões, você DEVE avaliar:
* **Vazamento de Domínio no BFF:** Inspecione ativamente se o BFF está calculando preços, validando regras de negócio ou alterando estados diretamente. Se estiver, mova essa lógica para o microsserviço de domínio correspondente.
* **Isolamento de Adaptadores:** Um Adaptador nunca deve chamar outro Adaptador diretamente. A comunicação deve sempre passar através do Core via Portas.
* **Dumping de Dados Rest:** Rejeite implementações onde a API (ou BFF) simplesmente cospe a Entidade do banco de dados para a interface. Exija o mapeamento para Responses/DTOs específicos para a tela.

## 5. Instruções de Execução do Agente
* Ao estruturar diretórios para projetos em Hexagonal, force a separação em pacotes de domínio (`domain`), portas (`application/ports`) e adaptadores (`infrastructure/adapters`).
* Quando o usuário desenhar uma funcionalidade que será consumida tanto por um App quanto por um E-commerce Web, recomende imediatamente o padrão BFF para evitar que o cliente mobile faça múltiplos requests ou baixe payloads pesados.
* Valide os *imports*: Classes que implementam as regras de negócio em Java/Python não devem conter anotações de infraestrutura de entrada (ex: `@RestController`) ou de persistência.