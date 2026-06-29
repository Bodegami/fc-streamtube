# Code Review — SI-02.5: Serviço de e-mails transacionais

## Escopo

Arquivos não commitados em `feature/user-auth-01` referentes ao SI-02.5:

| Arquivo | Tipo |
|---|---|
| `backend/src/main/java/com/fcstreamtube/infrastructure/email/EmailService.java` | novo |
| `backend/src/test/java/com/fcstreamtube/infrastructure/email/EmailServiceTest.java` | novo |
| `backend/build.gradle.kts` | modificado |
| `backend/src/main/java/com/fcstreamtube/FcStreamtubeBackendApplication.java` | modificado |
| `backend/src/main/resources/application.yml` | modificado |

---

## Round 1

### CR-01 — `EmailService` sem interface de saída viola Clean Architecture ⚠️ CRÍTICO

**Arquivo:** `infrastructure/email/EmailService.java`

`EmailService` é uma classe concreta de infraestrutura sem interface correspondente. Quando os Use Cases de SI-02.6 (`RegisterUserUseCase`) e SI-02.9 (`ResetPasswordUseCase`) precisarem enviar e-mails, serão forçados a importar diretamente `com.fcstreamtube.infrastructure.email.EmailService`, violando a Regra da Dependência da Clean Architecture (camadas internas não devem conhecer camadas externas).

O guia `clean-architecture.md` é explícito: *"Se um Caso de Uso precisa interagir com [...] um serviço externo, ele deve definir uma interface (Porta de Saída) na sua própria camada."*

**Solução**: Criar a interface em `application/ports/out/` e fazer `EmailService` implementá-la:

```java
// application/ports/out/EmailGateway.java
public interface EmailGateway {
    void sendConfirmationEmail(String to, String confirmationUrl);
    void sendPasswordResetEmail(String to, String resetUrl);
}
```

```java
// infrastructure/email/EmailService.java
@Service
public class EmailService implements EmailGateway { ... }
```

Os Use Cases dependerão exclusivamente de `EmailGateway`. Após essa mudança, o `EmailServiceTest` continua testando a implementação concreta — correto.

---

### CR-02 — Falta de teste para o campo `from` ⚠️ MAIOR

**Arquivo:** `EmailServiceTest.java`

Nenhum teste verifica se o endereço de remetente (`from`) está corretamente propagado para a `SimpleMailMessage`. O valor é injetado via `@Value` no construtor: um e-mail vazio ou incorreto passaria em todos os testes existentes sem ser detectado.

**Adicionar** (um teste por método, para ambos os fluxos):

```java
@Test
void givenConfirmationEmail_whenSendConfirmationEmail_thenFromAddressIsSet() {
    emailService.sendConfirmationEmail("user@example.com",
        "http://localhost:8080/api/auth/confirm?token=abc123");

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());
    assertThat(captor.getValue().getFrom()).isEqualTo("noreply@fcstreamtube.com");
}
```

```java
@Test
void givenPasswordResetEmail_whenSendPasswordResetEmail_thenFromAddressIsSet() {
    emailService.sendPasswordResetEmail("user@example.com",
        "http://localhost:4200/reset-password?token=xyz789");

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());
    assertThat(captor.getValue().getFrom()).isEqualTo("noreply@fcstreamtube.com");
}
```

---

### CR-03 — Falta de teste para o `subject` ⚠️ MAIOR

**Arquivo:** `EmailServiceTest.java`

Nenhum teste verifica o assunto do e-mail. Um subject vazio ou trocado (ex: template de confirmação enviado com subject de reset) não seria detectado.

**Adicionar**:

```java
@Test
void givenConfirmationEmail_whenSendConfirmationEmail_thenSubjectIsSet() {
    emailService.sendConfirmationEmail("user@example.com",
        "http://localhost:8080/api/auth/confirm?token=abc123");

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());
    assertThat(captor.getValue().getSubject()).isEqualTo("Confirm your StreamTube account");
}

@Test
void givenPasswordResetEmail_whenSendPasswordResetEmail_thenSubjectIsSet() {
    emailService.sendPasswordResetEmail("user@example.com",
        "http://localhost:4200/reset-password?token=xyz789");

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());
    assertThat(captor.getValue().getSubject()).isEqualTo("Reset your StreamTube password");
}
```

---

### CR-04 — Assimetria de cobertura entre os dois métodos de envio ⚠️ MAIOR

**Arquivo:** `EmailServiceTest.java`

`sendConfirmationEmail` tem 2 testes de caminho feliz (recipient + body). `sendPasswordResetEmail` tem apenas 1 (body). Falta o teste de recipient para o fluxo de reset:

```java
@Test
void givenPasswordResetEmail_whenSendPasswordResetEmail_thenMessageSentToCorrectRecipient() {
    String to = "user@example.com";
    String resetUrl = "http://localhost:4200/reset-password?token=xyz789";

    emailService.sendPasswordResetEmail(to, resetUrl);

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());
    assertThat(captor.getValue().getTo()).containsExactly(to);
}
```

---

### CR-05 — Falha SMTP não verifica comportamento de log ⚠️ MAIOR

**Arquivo:** `EmailServiceTest.java`

Os testes `givenSmtpFailure_when*_thenExceptionNotPropagated` verificam corretamente que a exceção não é propagada. Porém não verificam que o `log.error` foi emitido. Em produção, uma falha SMTP silenciosa (ex.: sem log) seria invisível no sistema de monitoramento.

O padrão já estabelecido neste projeto no SI-02.4 (`GlobalExceptionHandlerIT`) usa `ListAppender` para verificar log behavior. Aplicar a mesma abordagem aqui garante que um refactor futuro que acidentalmente remova o `log.error` quebre o teste, não o monitoramento.

**Adicionar** (exemplo para o método de confirmação; replicar para o de reset):

```java
@Test
void givenSmtpFailure_whenSendConfirmationEmail_thenErrorIsLogged() {
    doThrow(new MailSendException("SMTP connection refused"))
            .when(mailSender).send(any(SimpleMailMessage.class));

    Logger logger = (Logger) LoggerFactory.getLogger(EmailService.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);

    emailService.sendConfirmationEmail("user@example.com",
            "http://localhost:8080/api/auth/confirm?token=abc123");

    logger.detachAppender(listAppender);

    assertThat(listAppender.list)
            .anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage()).contains("user@example.com");
            });
}
```

---

### CR-06 — `smtp.auth` e `starttls.enable` hardcoded bloqueiam configuração de produção ⚠️ MENOR

**Arquivo:** `application.yml`

```yaml
properties:
  mail:
    smtp:
      auth: false
      starttls:
        enable: false
```

Os valores `false` são corretos para Mailhog em dev. Em produção com provedores SMTP reais (SendGrid, Amazon SES), essas propriedades precisam ser `true`. Embora o Spring Boot suporte override via `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`, a convenção do projeto é parametrizar explicitamente via `${ENV_VAR:default}` — conforme todos os outros blocos de configuração no mesmo arquivo.

**Corrigir**:

```yaml
properties:
  mail:
    smtp:
      auth: ${SMTP_AUTH:false}
      starttls:
        enable: ${SMTP_STARTTLS:false}
```

---

### CR-07 — Ausência de log no caminho de sucesso dificulta diagnóstico em produção 💡 SUGESTÃO

**Arquivo:** `infrastructure/email/EmailService.java`

Quando o envio é bem-sucedido, nenhum log é emitido. Em produção, é impossível confirmar via logs se um e-mail foi enviado sem consultar o provedor SMTP diretamente.

**Adicionar** após `mailSender.send(message)` em ambos os métodos:

```java
log.info("Email sent to {}", to);
```

> **Nota**: O endereço de e-mail (`to`) já é logado no `log.error` de falha. Logar no sucesso é consistente com o comportamento atual de log do serviço.

---

### Achados por arquivo — resumo

| # | Severidade | Arquivo | Descrição |
|---|---|---|---|
| CR-01 | CRÍTICO | `EmailService.java` | Sem interface `EmailGateway` — violação de Clean Architecture |
| CR-02 | MAIOR | `EmailServiceTest.java` | Falta teste para campo `from` (ambos os métodos) |
| CR-03 | MAIOR | `EmailServiceTest.java` | Falta teste para `subject` (ambos os métodos) |
| CR-04 | MAIOR | `EmailServiceTest.java` | `sendPasswordResetEmail` sem teste de recipient |
| CR-05 | MAIOR | `EmailServiceTest.java` | Falha SMTP não verifica log behavior |
| CR-06 | MENOR | `application.yml` | `smtp.auth` e `starttls.enable` não parametrizados |
| CR-07 | SUGESTÃO | `EmailService.java` | Adicionar `log.info` no caminho de sucesso |

### O que está correto

- `build.gradle.kts`: dependência `spring-boot-starter-mail` adicionada corretamente.
- `FcStreamtubeBackendApplication`: `@EnableAsync` em posição idiomática; aceitável dado que a alternativa (`AsyncConfig`) seria indistinguível em termos de SRP para este nível de complexidade.
- `application.yml`: parametrização via `${ENV_VAR:default}` para host, port, credentials e `app.mail.from` — consistente com o padrão do projeto.
- `EmailService`: constructor injection, `@Value` no parâmetro `from`, `@Async` em ambos os métodos, captura e log de exceções.
- `EmailServiceTest`: naming `givenX_whenY_thenZ`, separação de comportamentos (um teste = um comportamento), uso de `ArgumentCaptor` corretamente, `@BeforeEach` para setup do serviço.

---

## Round 2

### Status do Round 1

| # | Status | Observação |
|---|---|---|
| CR-01 | ✅ RESOLVIDO | `EmailGateway` criada em `application/ports/out/`; `EmailService implements EmailGateway`; `backend/CLAUDE.md` atualizado |
| CR-02 | ✅ RESOLVIDO | `thenFromAddressIsSet` adicionado para ambos os métodos |
| CR-03 | ✅ RESOLVIDO | `thenSubjectIsSet` adicionado para ambos os métodos |
| CR-04 | ✅ RESOLVIDO | `givenPasswordResetEmail_..._thenMessageSentToCorrectRecipient` adicionado |
| CR-05 | ✅ RESOLVIDO | `thenErrorIsLogged` com `ListAppender` em `@BeforeEach`/`@AfterEach` adicionado para ambos os métodos |
| CR-06 | ✅ RESOLVIDO | `smtp.auth: ${SMTP_AUTH:false}` e `starttls.enable: ${SMTP_STARTTLS:false}` parametrizados |
| CR-07 | ✅ RESOLVIDO | `log.info("Email sent to {}", to)` adicionado no caminho de sucesso de ambos os métodos |

Suite completa: **BUILD SUCCESSFUL** — todos os testes do projeto permanecem verdes após as correções.

---

### CR-08 — Assertions de log não distinguem o fluxo correto 💡 SUGESTÃO

**Arquivo:** `EmailServiceTest.java` — linhas 111–115 e 180–184

Os dois testes `thenErrorIsLogged` assertam apenas:

```java
assertThat(event.getLevel()).isEqualTo(Level.ERROR);
assertThat(event.getFormattedMessage()).contains("user@example.com");
```

Se os templates de mensagem fossem acidentalmente trocados entre os dois métodos (ex.: `sendPasswordResetEmail` emitindo `"Failed to send confirmation email to..."`), ambos os testes ainda passariam — o endereço de e-mail estaria presente em qualquer um dos dois logs.

**Sugestão** — adicionar asserção sobre o contexto do fluxo em cada teste:

```java
// em givenSmtpFailure_whenSendConfirmationEmail_thenErrorIsLogged
assertThat(event.getFormattedMessage()).contains("confirmation email");

// em givenSmtpFailure_whenSendPasswordResetEmail_thenErrorIsLogged
assertThat(event.getFormattedMessage()).contains("password reset email");
```

> Severidade baixa: o risco é de mensagem de log errada em produção, não de comportamento incorreto. Fica a critério do time se o custo de manutenção da string literal no teste vale a cobertura adicional.

---

### Achados Round 2 — resumo

| # | Severidade | Arquivo | Descrição |
|---|---|---|---|
| CR-08 | SUGESTÃO | `EmailServiceTest.java` | Assertions de log não verificam o tipo de e-mail no texto da mensagem |

**Conclusão do Round 2**: nenhum achado crítico, maior ou menor. O SI-02.5 está pronto para commit.

---

## Round 3

### Status do Round 2

| # | Status | Observação |
|---|---|---|
| CR-08 | ✅ RESOLVIDO | `contains("confirmation email")` adicionado em `thenErrorIsLogged` do fluxo de confirmação; `contains("password reset email")` adicionado no fluxo de reset |

Suite completa: **BUILD SUCCESSFUL** — 12 testes no `EmailServiceTest`, todos passando sem regressões.

### Conclusão

Nenhum achado novo. Todos os critérios avaliados estão satisfeitos:

- **Clean Architecture**: `EmailGateway` em `application/ports/out/`, `EmailService` em `infrastructure/email/`, zero dependências de infra no domínio.
- **SOLID**: SRP respeitado (`EmailService` faz apenas envio de e-mail), DIP aplicado via `EmailGateway`.
- **TDD / FIRST**: 12 testes unitários, sem Spring context, rápidos, independentes, auto-validantes, com setup/teardown limpo via `@BeforeEach`/`@AfterEach`.
- **Guias do projeto**: naming `givenX_whenY_thenZ`, `ListAppender` para log behavior (padrão já estabelecido no SI-02.4), env vars para toda configuração sensível de SMTP.

**O SI-02.5 está aprovado para commit.**
