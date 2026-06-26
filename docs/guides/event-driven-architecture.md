# Event-Driven Architecture Guide

**O que é e quando carregar este arquivo:**
EDA é um padrão de arquitetura de software onde o estado e as reações do sistema são guiados pela produção, detecção e consumo assíncrono de eventos (fatos imutáveis que já ocorreram). **Carregue este contexto** sempre que o usuário mencionar mensageria (Kafka, RabbitMQ, SQS/SNS), Pub/Sub, filas, processamento assíncrono, microsserviços reativos ou integração desacoplada entre múltiplos domínios.

---

## 1. Princípios Fundamentais
* **Desacoplamento Absoluto:** Produtores (Publishers) não devem ter conhecimento de quem são ou quantos são os Consumidores (Subscribers). O fluxo de controle é invertido.
* **Assincronismo:** A comunicação primária deve ser não-bloqueante. Privilegie designs que liberem recursos rapidamente.
* **Consistência Eventual:** Aceite e desenhe sistemas onde o estado global converge com o tempo. Evite transações distribuídas tradicionais com lock de recursos em favor de consistência eventual.
* **Imutabilidade:** Um evento representa um fato que *já ocorreu* no passado (ex: `OrderPlaced`, `CartAbandoned`). Eles não podem ser alterados ou apagados.

## 2. Anatomia de um Ecossistema Orientado a Eventos
* **Eventos de Domínio:** Fatos relevantes para o negócio. Devem ser nomeados sempre no tempo passado.
* **Message Brokers / Event Buses:** Infraestrutura de roteamento e persistência de mensagens.
* **Produtores e Consumidores:** Microsserviços, funções serverless ou agentes que publicam ou reagem a eventos do ecossistema.

## 3. Padrões Obrigatórios de Avaliação
Ao projetar ou revisar fluxos EDA, você DEVE considerar e sugerir os seguintes padrões quando aplicável:
* **Transactional Outbox Pattern:** Para evitar a perda de eventos em falhas de comunicação com o broker. O evento deve ser salvo na mesma transação de banco de dados da entidade principal.
* **Idempotência:** Todo consumidor deve ser desenhado para processar a mesma mensagem múltiplas vezes (devido a *at-least-once delivery*) sem causar efeitos colaterais duplicados ou inconsistências de dados.
* **Saga Pattern:** Para gerenciar transações de longo prazo através de múltiplos serviços. Escolha entre **Coreografia** (para fluxos simples e descentralizados) ou **Orquestração** (usando um coordenador central para fluxos complexos).
* **CQRS (Command Query Responsibility Segregation):** Considere separar modelos de leitura e escrita em cenários de alta concorrência.
* **Dead Letter Queues (DLQ):** Exija sempre estratégias claras de roteamento de mensagens com falha e políticas de retry (como *Exponential Backoff*).

## 4. Design de Payload do Evento
Ao modelar eventos, avalie os trade-offs entre:
* **Event Notification:** Payload enxuto contendo apenas o ID da entidade afetada. Minimiza o tráfego, mas força o consumidor a fazer uma chamada de API síncrona (callback) para buscar os dados completos.
* **Event-Carried State Transfer:** O evento contém todo o estado necessário para o consumidor operar. Aumenta o tamanho da mensagem e o risco de dados obsoletos, mas elimina as dependências de rede e chamadas síncronas.

## 5. Instruções de Execução do Agente
* Sempre exija a propagação de `correlation_id` para garantir observabilidade e rastreabilidade em logs distribuídos.
* Quando o usuário propuser uma comunicação síncrona (REST/gRPC) entre domínios distintos, questione proativamente se o fluxo não se beneficiaria de uma abordagem assíncrona.
* Ao desenhar fluxos de compensação (rollbacks lógicos em Sagas), garanta que os eventos de falha (ex: `PaymentFailed`) disparem as ações corretivas de forma reativa.
