# Clean Architecture Guide

**O que é e quando carregar este arquivo:**
Clean Architecture é uma estratégia de design de software que organiza o sistema em camadas concêntricas, cujo objetivo principal é a separação de preocupações através de uma regra de dependência estrita: o código das camadas internas nunca deve conhecer o código das camadas externas. **Carregue este contexto** sempre que o usuário mencionar organização de pacotes/diretórios, dissociação de regras de negócio de frameworks (Spring, Micronaut, etc.), independência de banco de dados ou UI, isolamento de domínios e criação de Casos de Uso (Use Cases).

---

## 1. Princípios Fundamentais
* **A Regra da Dependência:** As dependências de código-fonte devem apontar apenas para dentro, em direção ao núcleo do software (as camadas de negócio). Nada em uma camada interna pode mencionar algo de uma camada externa.
* **Independência de Frameworks:** A arquitetura não deve depender da existência de uma biblioteca ou framework robusto. Eles devem ser tratados como ferramentas (detalhes), e não como a base do sistema.
* **Testabilidade Absoluta:** As regras de negócio de aplicação e de domínio devem ser testáveis sem a necessidade de UI, banco de dados, servidores web ou qualquer outro elemento externo.
* **Independência de Detalhes:** O núcleo do negócio não deve saber (e não deve se importar) se a interface é web, console ou mobile, nem se o banco de dados é relacional (PostgreSQL, MySQL) ou NoSQL.

## 2. As Camadas do Sistema
* **Entidades (Entities):** O núcleo mais interno. Encapsulam as regras de negócio globais/corporativas. Podem ser objetos com métodos ou um conjunto de estruturas de dados e funções. São as menos propensas a mudar quando algo externo muda.
* **Casos de Uso (Use Cases):** Camada que orquestra o fluxo de dados para e das entidades, direcionando-as para atingir os objetivos do caso de uso da aplicação. Contêm regras de negócio específicas da aplicação.
* **Adaptadores de Interface (Interface Adapters):** Conjunto de adaptadores que convertem dados no formato mais conveniente para os Casos de Uso/Entidades para o formato mais conveniente para entidades externas (ex: Controllers MVC, Gateways de Banco de Dados, Presenters).
* **Frameworks e Drivers:** A camada mais externa. Onde residem os detalhes de infraestrutura: o banco de dados, o framework web (Spring/Micronaut), drivers, ferramentas de log e bibliotecas de terceiros.

## 3. Padrões Obrigatórios de Avaliação
Ao revisar ou sugerir designs baseados em Clean Architecture, você DEVE avaliar e garantir os seguintes pontos:
* **Inversão de Dependência (DIP):** Se um Caso de Uso precisa interagir com um repositório (banco de dados), ele deve definir uma interface (Porta de Saída) na sua própria camada. A implementação dessa interface deve residir na camada de infraestrutura.
* **Isolamento de Modelos (DTOs):** Dados cruzando as fronteiras das camadas não devem conter estruturas internas das Entidades. Use objetos de transferência de dados (DTOs) ou mapas simples para trafegar dados entre as bordas. Evite vazar entidades de negócio para Controllers ou Views.
* **Fronteiras Claras (Boundaries):** Certifique-se de que a comunicação com os Casos de Uso ocorra através de interfaces explícitas de entrada (Input Boundary) e saída (Output Boundary).

## 4. Instruções de Execução do Agente
* **Veto de Anotações Externas:** Rejeite ou questione fortemente o uso de anotações de infraestrutura/frameworks (ex: `@Entity` do JPA/Hibernate, anotações do Jackson como `@JsonProperty`, ou anotações de validação específicas do framework) dentro das classes de Entidades e Casos de Uso. Elas poluem o núcleo de negócio com detalhes externos.
* **Validação de Direção de Dependência:** Ao ler um arquivo ou classe, valide os `imports`. Se uma classe da camada de domínio ou aplicação importar algo de pacotes marcados como `infra`, `controller`, `repository` ou `configuration`, aponte a violação imediatamente.
* **Foco no Caso de Uso:** Incentive o usuário a criar Casos de Uso granulares (uma classe para cada ação do sistema, ex: `CreateOrderUseCase`, `CancelOrderUseCase`) em vez de serviços genéricos gigantescos.
