# Domain-Driven Design Guide

**O que é e quando carregar este arquivo:**
DDD é uma abordagem de modelagem de software que foca em resolver a complexidade no coração do negócio, alinhando o design do código diretamente com o modelo mental dos especialistas de negócio. **Carregue este contexto** sempre que o usuário mencionar modelagem de negócios complexos, Linguagem Ubíqua, delimitação de fronteiras (Bounded Contexts), mapeamento de subdomínios (Core, Supporting, Generic), ou elementos táticos como Agregados, Entidades, Value Objects e Serviços de Domínio.

---

## 1. Design Estratégico (Fronteiras e Alinhamento)
* **Linguagem Ubíqua:** Uma linguagem única, rigorosa e compartilhada entre desenvolvedores e especialistas de negócio (Domain Experts). Termos técnicos ou traduções artificiais não devem existir; o código deve ler-se exatamente como o negócio opera.
* **Contextos Delimitados (Bounded Contexts):** Fronteiras conceituais explícitas dentro das quais um modelo de domínio específico se aplica. Um mesmo termo (ex: `Produto`) pode ter significados, atributos e regras completamente diferentes em contextos distintos (ex: Contexto de *Catálogo* vs. Contexto de *Logística*).
* **Destilação de Subdomínios:** Classifique os problemas para priorizar o esforço de engenharia:
    * **Core Domain:** O coração do sistema, o diferencial competitivo que deve ser desenvolvido sob medida com máxima excelência.
    * **Supporting Domain:** Funcionalidades auxiliares personalizadas, necessárias para o negócio, mas que não geram diferencial competitivo direto.
    * **Generic Domain:** Problemas padronizados que não possuem especificidades do negócio (ex: autenticação, envio de e-mails) e podem usar soluções prontas do mercado.

## 2. Design Tático (Elementos de Modelagem do Código)
* **Entidades (Entities):** Objetos que possuem uma identidade única e contínua que persiste ao longo do tempo. Seus atributos podem mudar, mas sua identidade permanece imutável (ex: `Cliente`, `Pedido`).
* **Objetos de Valor (Value Objects - VO):** Objetos sem identidade conceitual própria, definidos estritamente pela combinação de seus atributos. São imutáveis por definição. Se alterados, são substituídos por completo (ex: `Endereço`, `Monetário`, `CPF`).
* **Agregados e Raízes de Agregados (Aggregates & Aggregate Roots):** Um cluster de Entidades e Value Objects associados que são tratados como uma única unidade de transação e consistência de dados. O acesso ao agregados deve ocorrer estritamente através de uma única entidade marcada como a Raiz do Agregado (Aggregate Root).
* **Serviços de Domínio (Domain Services):** Operações ou comportamentos puramente de negócio que não pertencem naturalmente a uma única Entidade ou Value Object. Não possuem estado.
* **Repositórios (Repositories):** Interfaces que abstraem o acesso à persistência, operando exclusivamente sobre Raízes de Agregados.
* **Eventos de Domínio (Domain Events):** Fatos explícitos que aconteceram no domínio e que possuem relevância direta para o negócio (ex: `PedidoFaturado`).

## 3. Padrões Obrigatórios de Avaliação
Ao analisar código ou arquitetura baseada em DDD, você DEVE avaliar e garantir:
* **Veto a Modelos Anêmicos:** Rejeite classes de domínio que sirvam apenas como sacos de dados (apenas propriedades com getters e setters públicos). Entidades e Agregados devem conter os métodos de comportamento e defender suas próprias invariantes (regras de validação de estado).
* **Referências entre Agregados por ID:** Agregados nunca devem manter referências diretas de objetos gráficos na memória para outros agregados. A comunicação ou associação entre eles deve ocorrer exclusivamente através do ID da Raiz do Agregado alvo.
* **Consistência Imediata vs. Eventual:** Garanta consistência imediata (transacional) estritamente dentro das fronteiras de um único Agregado. Mudanças colaterais que afetam outros Agregados ou Contextos devem ser tratadas via consistência eventual (usando Eventos de Domínio).

## 4. Instruções de Execução do Agente
* **Defesa de Invariantes:** Ao avaliar a criação de um objeto ou a execução de um método, certifique-se de que o estado do Agregado nunca fique inválido. Os construtores e métodos devem lançar exceções de domínio imediatamente caso as regras de negócio sejam violadas.
* **Isolamento de Tipos Primitivos (Primitive Obsession):** Questione proativamente o uso excessivo de tipos primitivos (`String`, `BigDecimal`, `UUID`) para representar conceitos de negócio complexos. Incentive a extração desses tipos para Value Objects auto-validados (ex: criar um VO `Email` em vez de usar uma `String` pura).
* **Nomenclatura do Domínio:** Substitua termos puramente orientados a CRUD (ex: `updateStatus()`, `saveData()`) por termos ricos e expressivos que representem a ação real do negócio na Linguagem Ubíqua (ex: `aprovarPagamento()`, `alterarEnderecoDeEntrega()`).
