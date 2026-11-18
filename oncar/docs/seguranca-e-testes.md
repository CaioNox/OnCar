# OnCar — Segurança e testes unitários

Guia das mudanças de segurança pendentes no repositório e da suíte de testes unitários
criada para o backend (Spring Boot) e o frontend (Next.js). O objetivo é que você consiga
abrir cada arquivo citado e entender **o que mudou, por que mudou e como está protegido
por teste**.

> Situação em 18/11/2026. Nada disto foi commitado. Todos os números de testes abaixo
> são de execuções reais (`./mvnw test` e `npm test`), não estimativas.
>
> Os trechos de código são do repositório. Em alguns, foram acrescentados comentários
> curtos só para explicar a linha neste guia; no arquivo original eles podem não existir.
> As mensagens do sistema aparecem sem acento porque é assim que estão no código.

## Sumário

- [Visão geral](#visão-geral)
- [Parte A — Mudanças de segurança](#parte-a--mudanças-de-segurança)
  - [A.1 Produção é o padrão; demonstração só na sua máquina](#a1-produção-é-o-padrão-demonstração-só-na-sua-máquina)
  - [A.2 Senha do banco obrigatória em produção](#a2-senha-do-banco-obrigatória-em-produção)
  - [A.3 Console do H2 desligado](#a3-console-do-h2-desligado)
  - [A.4 Revogação de sessões quando o acesso muda](#a4-revogação-de-sessões-quando-o-acesso-muda)
  - [A.5 Fixação de sessão no login pela API](#a5-fixação-de-sessão-no-login-pela-api)
  - [A.6 API recebe 401 em JSON; telas vão ao login com o motivo](#a6-api-recebe-401-em-json-telas-vão-ao-login-com-o-motivo)
  - [A.7 Spring Boot 3.3 → 4.1](#a7-spring-boot-33--41)
  - [A.8 Outras mudanças no mesmo pacote](#a8-outras-mudanças-no-mesmo-pacote)
  - [A.9 Riscos que continuam abertos](#a9-riscos-que-continuam-abertos)
- [Parte B — Testes unitários do backend](#parte-b--testes-unitários-do-backend)
- [Parte C — Testes do frontend (Vitest + Testing Library)](#parte-c--testes-do-frontend-vitest--testing-library)
- [Parte D — Como rodar](#parte-d--como-rodar)
- [Parte E — Validação: o que foi executado de verdade](#parte-e--validação-o-que-foi-executado-de-verdade)
- [Parte F — Pendências e recomendações](#parte-f--pendências-e-recomendações)
- [Apêndice — Arquivos criados e modificados](#apêndice--arquivos-criados-e-modificados)

---

## Visão geral

| | Antes | Depois |
|---|---|---|
| Testes do backend | 110 (todos de integração, `@SpringBootTest` + H2) | **386** (+276 unitários com JUnit 5 + Mockito) |
| Testes do frontend | nenhum framework de teste | **188** em Vitest + Testing Library (componentes, hooks e `src/lib`) |
| Falhas | 0 | **0** |

Três ideias guiam tudo o que vem a seguir:

1. **Teste unitário isola a unidade.** No backend, repositórios e serviços vizinhos são
   simulados com Mockito: nenhum teste unitário sobe o Spring nem toca no banco. No
   frontend, o `fetch` (a API) e o roteador do Next são simulados.
2. **Estrutura em memória não é dependência externa.** `IndiceProdutos`,
   `SessionRegistryImpl` e `TabelaHash` são usados de verdade. Simulá-los esconderia
   justamente o comportamento que se quer verificar.
3. **Nenhuma expectativa foi inventada.** Cada valor esperado vem do código ou de uma
   regra documentada nele. Quando o comportamento é discutível, o teste registra o que o
   código faz hoje e o ponto vai para a [Parte F](#parte-f--pendências-e-recomendações).

---

## Parte A — Mudanças de segurança

> **Procedência.** Estas mudanças já estavam na árvore de trabalho, sem commit, quando
> a sessão de testes começou. Não foram escritas nela. A descrição abaixo foi feita a
> partir do `git diff` e do código atual, e cada item aponta o teste que o protege. Os
> testes unitários desta sessão cobrem várias dessas peças (marcadas com 🧪).

### A.1 Produção é o padrão; demonstração só na sua máquina

**Problema.** Antes, `application.properties` tinha `spring.profiles.active=dev`. Quem
subisse o jar no servidor com `java -jar`, esquecendo o perfil, abria a **base de
demonstração**: H2 em memória e usuários com a senha pública `oncar2026`.

**Mudança.**

`oncar/backend/src/main/resources/application.properties`:

```properties
# Sem perfil informado, a aplicacao sobe como PRODUCAO: sem usuarios de demonstracao e
# exigindo as variaveis do banco (ONCAR_DB_*).
spring.profiles.default=prod
```

- `spring.profiles.default` (e não `active`) só vale quando **ninguém** informou perfil.
  Quem passa `--spring.profiles.active=dev` continua no dev.
- O desenvolvimento não perde nada: o `pom.xml` define
  `<spring-boot.run.profiles>dev</spring-boot.run.profiles>`, então `./mvnw spring-boot:run`
  sobe em dev. Por ser propriedade Maven, isso não vai para dentro do jar.
- Tudo o que era de demonstração saiu para `application-dev.properties` (arquivo novo):
  H2, `ddl-auto=update`, cache do Thymeleaf desligado e:

```properties
# A carga de demonstracao (CargaInicial) cria usuarios com a senha publica "oncar2026".
# Por isso o perfil dev so atende na propria maquina
server.address=127.0.0.1
```

Com isso, ninguém na mesma rede Wi-Fi consegue entrar com as contas de demonstração.

**Teste que protege:** `config/ConfiguracaoDePerfisTest` (já existia), que lê os
`.properties` e falha se `spring.profiles.active` voltar ou se `server.address` do dev
mudar.

### A.2 Senha do banco obrigatória em produção

**Problema.** `application-prod.properties` tinha `${ONCAR_DB_PASSWORD:oncar}`: sem a
variável, a aplicação tentava a senha **fraca e conhecida** `oncar`.

**Mudança.** O valor padrão saiu:

```properties
spring.datasource.password=${ONCAR_DB_PASSWORD}
```

Mas isso sozinho cria outro problema. Sem a variável, o Spring Boot aceitaria o **texto
literal** `${ONCAR_DB_PASSWORD}` como senha e só falharia lá na frente, com um erro de
conexão que não aponta a causa. Por isso entrou `config/VerificacaoDeProducao.java`:

```java
@Configuration
@Profile("prod")                      // so existe em producao
public class VerificacaoDeProducao {

    @Bean
    static BeanFactoryPostProcessor exigirSenhaDoBanco(Environment ambiente) {
        // BeanFactoryPostProcessor roda ANTES de qualquer bean (inclusive o DataSource):
        // a mensagem e a primeira coisa no log, e a aplicacao nem tenta conectar.
        return fabrica -> verificarSenhaDoBanco(ambiente);
    }

    static void verificarSenhaDoBanco(Environment ambiente) {
        String senha;
        try {
            senha = ambiente.getProperty("spring.datasource.password");
        } catch (IllegalArgumentException placeholderSemValor) {
            senha = null;               // placeholder sem valor: tratado como ausente
        }
        // ausente, em branco ou placeholder nao resolvido -> recusa subir
        if (senha == null || senha.isBlank() || senha.contains("${")) {
            throw new IllegalStateException(MENSAGEM);
        }
    }
}
```

**Testes:** `ConfiguracaoDePerfisTest` cobre a variável ausente, em branco e informada.

**Primeiro acesso.** Como produção começa sem usuários, `config/PrimeiroAcesso.java` cria
o primeiro proprietário a partir de `ONCAR_ADMIN_EMAIL`/`ONCAR_ADMIN_SENHA`, **só
enquanto a base estiver vazia**. 🧪 `config/PrimeiroAcessoTest`:

- base já povoada → as variáveis são ignoradas, ninguém é criado;
- e-mail ou senha ausentes → só avisa no log;
- senha fora da política → a aplicação **não sobe**, com mensagem que aponta as variáveis.
  É melhor do que ficar no ar sem nenhum acesso possível.

### A.3 Console do H2 desligado

O console web do H2 executa **SQL arbitrário** no banco. Antes ele estava ligado e
liberado no `SecurityConfig` (`"/h2-console/**"` no `permitAll`). Agora:

- `application.properties`: `spring.h2.console.enabled=false`, valendo em todos os perfis;
- `SecurityConfig`: `/h2-console/**` saiu da lista de rotas públicas.

**Teste:** `ConfiguracaoDePerfisTest.consoleDoH2Desligado`.

### A.4 Revogação de sessões quando o acesso muda

Esta é a mudança mais importante.

**Problema.** O Spring Security lê o perfil e a situação do usuário **só no login** e
guarda isso na sessão. Sem revogação:

- um funcionário **inativado** continuava usando o sistema enquanto a aba ficasse aberta;
- um gerente **rebaixado** a atendente continuava vendo custos;
- depois de **redefinir uma senha vazada**, quem já tinha entrado com ela continuava dentro.

**Como funciona agora** (fluxo):

```
UsuarioService.inativar / atualizar / excluir / redefinirSenha
        │  (dentro da transação)
        ▼
SessoesAtivasService.encerrarSessoesDe(email)
        │  registra um "afterCommit": só age se a transação gravar
        ▼  (commit)
SessionRegistry → marca todas as sessões do usuário como expiradas
        │
        ▼  (próxima requisição daquele navegador)
ConcurrentSessionFilter → derruba a sessão
        ├─ API   → 401 + JSON "Sua sessao foi encerrada porque o seu acesso foi alterado..."
        └─ telas → redirect para /login?encerrada (com aviso no login.html)
```

**1) O registro de sessões** — `config/SessoesConfig.java` (novo):

```java
@Bean
public SessionRegistry sessionRegistry() {
    return new SessionRegistryImpl();      // em memoria: basta para UMA instancia
}

@Bean
public HttpSessionEventPublisher httpSessionEventPublisher() {
    // avisa o registro quando o Tomcat destroi uma sessao (logout, timeout),
    // senao o registro acumularia sessoes mortas
    return new HttpSessionEventPublisher();
}
```

**2) Toda sessão entra no registro** — `SecurityConfig`:

```java
.sessionManagement(session -> session
        .sessionConcurrency(concorrencia -> concorrencia
                .maximumSessions(-1)                  // sem limite de sessoes por usuario...
                .sessionRegistry(sessionRegistry)     // ...mas todas registradas
                .expiredSessionStrategy(this::sessaoEncerrada)))
```

`maximumSessions(-1)` parece estranho, mas é proposital. O recurso de "concorrência" é
usado só para ganhar o registro e o `ConcurrentSessionFilter`; ninguém fica limitado a
um dispositivo.

**3) Login pela API também entra no registro.** O `formLogin` registra sozinho, mas o
`POST /api/sessao` autentica manualmente. Por isso, em `SessaoApiController`:

```java
sessoesAtivas.registrar(sessao.getId(), autenticacao.getPrincipal());
```

**4) Quem encerra** — `service/SessoesAtivasService.java` (novo), com o trecho central
comentado:

```java
private void encerrar(String email, String idDaSessaoMantida) {
    if (email == null || email.isBlank()) {
        return;                                    // nada a fazer, e nao derruba ninguem por engano
    }
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
        // Dentro de uma transacao: espera o COMMIT. Se a alteracao for desfeita
        // (rollback), ninguem e desconectado a toa; e quem entrar de novo ja
        // encontra os dados gravados.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                expirar(email, idDaSessaoMantida);
            }
        });
    } else {
        expirar(email, idDaSessaoMantida);
    }
}

private void expirar(String email, String idDaSessaoMantida) {
    sessionRegistry.getAllPrincipals().stream()
            .filter(principal -> pertenceAo(principal, email))         // por e-mail, sem caixa
            .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
            .filter(sessao -> !Objects.equals(sessao.getSessionId(), idDaSessaoMantida))
            .forEach(SessionInformation::expireNow);                    // marca; o filtro derruba
}
```

`pertenceAo` aceita os dois formatos de *principal*: `UserDetails`, vindo do formulário,
e `String`, vindo da API.

**5) Onde é chamado** — `UsuarioService`:

| Operação | Encerra | Por quê |
|---|---|---|
| `inativar` | todas as sessões | o usuário não deve mais entrar |
| `excluir` | todas | idem |
| `redefinirSenha` (pelo proprietário) | todas | "quem descobriu a senha antiga perde o acesso já" |
| `atualizar` | todas, **só se e-mail ou perfil mudou** | a sessão guarda e-mail e perfil do momento do login |
| `alterarSenha` (pelo próprio usuário) | **as outras**, mantém a atual | feito no `ContaController`: quem trocou a senha continua conectado |

O trecho de `atualizar` compara antes de gravar:

```java
String emailAnterior = usuario.getEmail();
boolean acessoMudou = !emailAnterior.equalsIgnoreCase(dados.getEmail())
        || usuario.getPerfil() != dados.getPerfil();
// ... grava ...
if (acessoMudou) {
    sessoesAtivas.encerrarSessoesDe(emailAnterior);   // o e-mail ANTIGO e o da sessao aberta
}
```

**Testes:**

- 🧪 `service/SessoesAtivasServiceTest` (9 testes, unitários, com o `SessionRegistryImpl` real):
  - encerra os dois formatos de principal;
  - compara e-mail sem diferenciar caixa e sem espaços;
  - não afeta outros usuários;
  - `encerrarOutrasSessoesDe` preserva a sessão atual;
  - e-mail nulo, vazio ou em branco não derruba ninguém;
  - **só age depois do commit e não age no rollback.**
- 🧪 `service/UsuarioServiceUnitarioTest`, aninhado *"alteração de cadastro e sessões
  abertas"*: mudar só o nome **não** derruba; mudar perfil ou e-mail derruba com o
  e-mail **antigo**; id inexistente não encerra nada.
- `web/RevogacaoDeSessoesTest` (integração, já existia, 14 testes): o fluxo inteiro via
  HTTP, para telas e API.

Trecho do teste do commit, comentado:

```java
@Test
@DisplayName("dentro de uma transacao so encerra depois do commit")
void esperaOCommit() {
    sessoes.registrar("tela", usuario(EMAIL));
    TransactionSynchronizationManager.initSynchronization();   // simula "estou numa transacao"

    sessoes.encerrarSessoesDe(EMAIL);
    assertThat(expirada("tela")).as("antes do commit").isFalse();   // ainda nao!

    // simula o commit: dispara os afterCommit registrados
    TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    assertThat(expirada("tela")).as("depois do commit").isTrue();
}
```

> **Limitação documentada no código.** O registro fica em memória. Com **mais de uma
> instância** atrás de um balanceador, uma instância não vê as sessões da outra. Nesse
> cenário é preciso Spring Session (JDBC ou Redis).

### A.5 Fixação de sessão no login pela API

**Ataque.** O atacante planta um `JSESSIONID` conhecido no navegador da vítima antes do
login. A vítima entra, e o atacante, que conhece o ID, está autenticado como ela.

O `formLogin` troca o ID sozinho, mas o login manual da API (`SessaoApiController`) não
trocava. A correção:

```java
// Descartar a sessao anterior (em vez de so trocar o ID) tambem evita herdar
// atributos de outro usuario que tenha entrado no mesmo navegador sem sair.
HttpSession anterior = request.getSession(false);   // false: nao cria se nao existir
if (anterior != null) {
    anterior.invalidate();                          // o ID plantado morre aqui
}
HttpSession sessao = request.getSession(true);      // sessao nova, ID novo
contextRepository.saveContext(contexto, request, response);
```

Isso só acontece **depois** de `authenticationManager.authenticate(...)`. Um login que
falha não derruba a sessão que o navegador já tinha.

**Testes** (`web/ApiRestTest`, integração):
- *"o login pela API descarta a sessão anterior"*: o ID antigo fica inválido e anônimo
  (401), e o novo está autenticado;
- *"login malsucedido não derruba a sessão que o navegador já tinha"*.

### A.6 API recebe 401 em JSON; telas vão ao login com o motivo

**Problema.** Havia `invalidSessionUrl("/login?expirada")`, que redirecionava **qualquer**
requisição com cookie vencido. Isso incluía:

- chamadas da API, que devem receber **401 em JSON**, não um redirect para HTML;
- o próprio `POST /api/sessao`, o que **impedia o novo login** pelo frontend.

**Mudança** em `SecurityConfig`:

```java
.exceptionHandling(handling -> handling
        // /api/** -> 401 puro
        .defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), REQUISICOES_DA_API)
        // telas -> login, com o motivo quando for o caso
        .defaultAuthenticationEntryPointFor(
                new LoginComMotivoEntryPoint(), AnyRequestMatcher.INSTANCE)
```

```java
private static final class LoginComMotivoEntryPoint extends LoginUrlAuthenticationEntryPoint {
    @Override
    protected String determineUrlToUseForThisRequest(HttpServletRequest request, ...) {
        // o navegador ainda manda o cookie de uma sessao que ja venceu?
        boolean sessaoVencida = request.getRequestedSessionId() != null
                && !request.isRequestedSessionIdValid();
        return sessaoVencida ? "/login?expirada" : "/login";
    }
}
```

O `login.html` ganhou o aviso de `?encerrada` (sessão revogada), separado do de
`?expirada` (inatividade).

🧪 No frontend, `src/lib/api.test.ts` verifica o outro lado do contrato: um 401 vira
`ErroDaApi` com `naoAutenticado === true` e a mensagem *"Sua sessao expirou..."*.

### A.7 Spring Boot 3.3 → 4.1

`pom.xml`: `3.3.5` → **`4.1.2`**. O comentário no pom explica que as linhas 3.3 e 3.5 já
não recebem correções de segurança gratuitas. Consequências no código:

- starters renomeados: `spring-boot-starter-web` → `spring-boot-starter-webmvc`;
  `spring-security-test` → `spring-boot-starter-security-test`; MockMvc foi para
  `spring-boot-starter-webmvc-test`;
- `AntPathRequestMatcher` (removido) → `PathPatternRequestMatcher.withDefaults().matcher("/api/**")`;
- imports dos testes de integração atualizados (`org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`).

### A.8 Outras mudanças no mesmo pacote

- **Frontend → backend em `127.0.0.1`** (`next.config.ts`, `.env.example`): o padrão de
  `ONCAR_BACKEND_URL` passou de `localhost` para `127.0.0.1`. Isso é coerente com o
  `server.address=127.0.0.1` do perfil dev (A.1): o Node pode resolver `localhost` para o
  IPv6 `::1`, onde o backend não escuta.
- **Templates** (`home/inicio.html`, `relatorio/fragmentos.html`): `th:attr="a=...,b=..."`
  virou atributos próprios (`th:data-dica`, `th:aria-label`, `th:aria-current`). É uma
  mudança de escrita, não de segurança: cada atributo passa a ter a sua expressão.

### A.9 Riscos que continuam abertos

| Risco | Gravidade | Onde | Ação sugerida |
|---|---|---|---|
| **`next` 16.3.4 com alerta crítico** do `npm audit` (RCE em `next/og` `ImageResponse`, mais cache poisoning e vazamento de Draft Mode; faixa afetada 16.0.0–16.4.4) | **Crítica** | `frontend/package.json` | Atualizar o Next para uma versão corrigida e rodar `npm test` + `npm run build` |
| Outras 20 vulnerabilidades do `npm audit` (6 altas, 14 moderadas: `shadcn`/`zod`, `undici`, `typescript-eslint`...) | Alta/moderada | dependências do frontend | `npm audit fix` e revisar. Conferido: **nenhuma vem das bibliotecas de teste** instaladas nesta sessão |
| Registro de sessões em memória | Média, só em cluster | `SessoesConfig` | Spring Session se houver mais de uma instância |
| Permissões no frontend são só visuais | Informativo | `src/lib/tipos.ts` | Correto por desenho: a autorização real é do backend. Os testes garantem que a tela não oferece o que a API recusa |
| Mockito se anexa sozinho no JDK 26 (aviso) | Baixa | `pom.xml` (surefire) | Configurar o Mockito como `-javaagent` antes que um JDK futuro bloqueie |

---

## Parte B — Testes unitários do backend

### B.1 Ferramentas e convenções

Nenhuma dependência nova: JUnit 5, Mockito, AssertJ e spring-security-test já vinham do
`spring-boot-starter-test` e do `spring-boot-starter-security-test`.

Padrão de uma classe de teste unitária:

```java
@ExtendWith(MockitoExtension.class)   // Mockito sem Spring: rapido e isolado
class EstoqueServiceUnitarioTest {

    @Mock private ProdutoRepository produtoRepository;          // simulados
    @Mock private MovimentacaoRepository movimentacaoRepository;
    @Mock private UsuarioService usuarioService;
    @Mock private AuditoriaService auditoriaService;

    @InjectMocks private EstoqueService estoqueService;           // o real, recebendo os mocks
```

Convenções adotadas:

- **Nome dos arquivos.** As classes que já tinham teste de integração ganharam o sufixo
  `UnitarioTest` (`EstoqueServiceTest` é integração, `EstoqueServiceUnitarioTest` é
  unitário). As que não tinham teste algum usam só `Test`.
- **`@DisplayName` em português**, descrevendo a regra (*"saldo apurado igual ao atual
  não gera ajuste"*), no mesmo estilo dos testes existentes.
- **`@Nested`** agrupa por funcionalidade (entrada, saída, ajuste, estorno...).
- **`@ParameterizedTest`** para limites: `@NullSource`, `@ValueSource`, `@CsvSource`,
  `@EnumSource`.
- **Strict stubs** (padrão do `MockitoExtension`): se um teste prepara um retorno que
  nunca é usado, o teste falha. Isso impede *setup* morto e mentiroso.
- `SecurityContextHolder.clearContext()` no `@AfterEach` de toda classe que autentica,
  para um teste não herdar o usuário do anterior.

### B.2 Mapa dos testes

| Arquivo (`backend/src/test/java/br/com/oncar/...`) | Unidade | Testes |
|---|---|---:|
| `service/EstoqueServiceUnitarioTest` | custo médio, entrada, saída, ajuste, estorno | 47 |
| `service/UsuarioServiceUnitarioTest` | política e reuso de senha, bloqueio, sessões | 33 |
| `service/DespesaServiceUnitarioTest` | limites de descrição, valor, competência, fornecedor | 28 |
| `service/ProdutoServiceUnitarioTest` | índice de SKU, unicidade, margem negativa | 22 |
| `service/RelatorioServiceUnitarioTest` | receita, apuração mensal, posição, maior giro | 12 |
| `service/SessoesAtivasServiceTest` | revogação de sessões (A.4) | 9 |
| `service/ClienteServiceTest` | documento único, veículos, exclusão | 7 |
| `service/FornecedorServiceTest` | CNPJ único, exclusão bloqueada | 7 |
| `service/DetalhesUsuarioServiceTest` | credencial, conta inativa/travada | 5 |
| `service/AuditoriaServiceUnitarioTest` | quem é o autor do log | 5 |
| `service/relatorio/PeriodoTest` | limite de 24 meses, bissexto, padrões | 9 |
| `web/controller/GraficoMensalTest` | escala do eixo, rótulos, barras | 19 |
| `web/controller/FormatosTest` | moeda, quantidade, variação (pt-BR) | 11 |
| `web/api/PermissoesApiTest` | matriz de perfis da API | 6 |
| `web/api/TratadorDeErrosApiTest` | exceção → 400/403/404 | 5 |
| `config/PrimeiroAcessoTest` | primeiro proprietário (A.2) | 6 |
| `config/ConversoresConfigTest` | campo fornecedor dos formulários | 3 |
| `domain/model/ProdutoTest`, `UsuarioTest`, `MovimentacaoTest` | regras das entidades | 18 |
| `domain/enums/MotivoMovimentacaoTest`, `PerfilTest` | grupos de motivo, `ROLE_` | 15 |
| `estrutura/IndiceProdutosTest` | índice + acesso concorrente | 3 |
| `estrutura/TabelaHashTest` (**modificado**) | +6 casos (colisão, cadeia, `clear`) | +6 |
| **Total novo** | | **276** |

### B.3 Passeio guiado por cinco testes

#### 1. Custo médio ponderado (cálculo puro) — `EstoqueServiceUnitarioTest`

O método é *package-private* (`BigDecimal calcularCustoMedioPonderado(...)`). Como o
teste está no mesmo pacote, chama-o direto, sem passar por repositório nenhum:

```java
@Test
@DisplayName("pondera o custo atual pelo saldo e o da compra pela quantidade")
void calculaMediaPonderada() {
    // (10 x 20 + 30 x 40) / 40 = 35      <- a conta fica escrita no teste
    BigDecimal custo = estoqueService.calcularCustoMedioPonderado(new BigDecimal("10"),
            new BigDecimal("20"), new BigDecimal("30"), new BigDecimal("40"));

    assertThat(custo).isEqualByComparingTo("35").hasScaleOf(4);   // valor E escala de 4 casas
}
```

Os outros casos do grupo: arredondamento HALF_UP (`0,66666…` → `0,6667`), saldo negativo
tratado como zero, custos nulos, **divisão por zero** (saldo 0 e quantidade 0) e a
reversão que desfaz exatamente uma entrada.

#### 2. Recusa sem efeito colateral — `EstoqueServiceUnitarioTest`

Não basta a exceção sair: **nada** pode ter sido gravado antes dela.

```java
@Test
@DisplayName("quantidade acima do saldo e recusada sem baixar nada")
void saldoInsuficiente() {
    comSaldo("3", "5");
    produtoExiste();

    assertThatThrownBy(() -> estoqueService.registrarSaida(PRODUTO_ID, new BigDecimal("3.001"),
            MotivoMovimentacao.VENDA_BALCAO, null, null, null, EMAIL))
            .isInstanceOf(RegraNegocioException.class)
            .hasMessageContaining("Saldo insuficiente para o produto FL-001")
            .hasMessageContaining("Nenhum item foi baixado");
    assertThat(produto.getSaldo()).isEqualByComparingTo("3");     // saldo intacto
    verify(produtoRepository, never()).save(any());               // nada gravado
    verify(movimentacaoRepository, never()).save(any());
}
```

O par desse teste é o limite: `saidaDoSaldoInteiro` baixa **exatamente** o saldo (3 de 3)
e deve aceitar, zerando o estoque.

#### 3. Codificador de senha previsível — `UsuarioServiceUnitarioTest`

O BCrypt real é lento e usa sal aleatório, então o mesmo texto gera hashes diferentes.
Para testar a **regra** (e não o BCrypt), o `PasswordEncoder` é simulado com uma
fórmula determinística:

```java
private void codificadorPrevisivel() {
    // "oncar2026" vira "hash:oncar2026"
    when(passwordEncoder.encode(anyString())).thenAnswer(chamada -> "hash:" + chamada.getArgument(0));
}

private void comparacaoPrevisivel() {
    when(passwordEncoder.matches(anyString(), anyString()))
            .thenAnswer(chamada -> ("hash:" + chamada.getArgument(0)).equals(chamada.getArgument(1)));
}
```

Com isso dá para testar um limite que o teste de integração não pega: **a 4ª senha mais
antiga já pode voltar** (o bloqueio vale para as 3 últimas).

```java
when(senhaHistoricoRepository.findByUsuarioOrderByCriadoEmDesc(usuario)).thenReturn(List.of(
        historico("senhaAtual1"), historico("segunda22"), historico("terceira3"),
        historico("quarta444")));                         // 4a mais antiga
...
usuarioService.alterarSenha(EMAIL, "senhaAtual1", "quarta444", "quarta444");
assertThat(usuario.getSenha()).isEqualTo("hash:quarta444");   // aceita
```

#### 4. Cache desatualizado — `ProdutoServiceUnitarioTest`

O `ProdutoService` usa a `TabelaHash` como índice SKU → id. O índice é um **cache** e pode
estar desatualizado. Este teste usa o `IndiceProdutos` real e prova a autocorreção:

```java
@Test
@DisplayName("indice defasado (o id agora tem outro SKU) e corrigido pelo banco")
void indiceDefasado() {
    indice.indexar("PF-10", 5L);                                   // indice diz: PF-10 -> 5
    when(produtoRepository.findById(5L)).thenReturn(Optional.of(produto(5L, "OUTRO-SKU")));  // mas o 5 mudou
    Produto correto = produto(8L, "PF-10");
    when(produtoRepository.findBySkuNormalizado("PF-10")).thenReturn(Optional.of(correto));

    assertThat(produtoService.buscarPorSku("PF-10")).isSameAs(correto);   // achou o certo
    assertThat(indice.buscarId("PF-10")).isEqualTo(8L);                   // e corrigiu o indice
}
```

O mesmo arquivo cobre a **margem negativa por perfil**: atendente nunca grava preço
abaixo do custo, gerente precisa confirmar e a confirmação vai para a auditoria.

#### 5. Limites de validação — `DespesaServiceUnitarioTest`

Os testes ficam nas bordas de cada regra:

| Regra | Recusado | Aceito |
|---|---|---|
| descrição 3–120 caracteres úteis | `null`, `""`, `"ab"`, `"   ab   "`, 121 chars | 3 e 120 chars |
| valor > 0 e no máximo 2 casas | `0`, `-15.90`, `10.001` | `0.01`, `10.5000` (zeros à direita não contam) |
| competência | 1º dia do **mês seguinte** | **último dia do mês corrente**, meses passados |
| documento ≤ 60 / observação ≤ 500 | 61 / 501 | 60 com espaços nas pontas (são removidos) |

### B.4 O que os testes revelaram

- **Arredondamento diferente na tela.** `Formatos.moeda` usa o padrão do `NumberFormat`
  (**HALF_EVEN**: `0,005` → `R$ 0,00`, `0,015` → `R$ 0,02`), enquanto os serviços gravam
  com **HALF_UP**. Hoje não aparece, porque os valores chegam com 2 casas. O teste
  `FormatosTest.meioCentavo` registra o comportamento atual, sem alterá-lo.
- Nenhum defeito encontrado nas regras de negócio: todas as falhas durante a escrita eram
  dos próprios testes (detalhes na Parte E).

---

## Parte C — Testes do frontend (Vitest + Testing Library)

### C.1 O que foi instalado e por quê

Seguindo o guia oficial da versão instalada do Next
(`node_modules/next/dist/docs/01-app/02-guides/testing/vitest.md`), todas como
`devDependencies`:

| Pacote | Versão | Para quê |
|---|---|---|
| `vitest` | 4.1.12 | runner de testes (usa o Vite 8.3.8) |
| `jsdom` | 31.0.1 | DOM simulado no Node |
| `@testing-library/react` | 16.3.3 | renderizar componentes e buscar como o usuário vê |
| `@testing-library/dom` | 10.4.2 | *peer* obrigatório do anterior |
| `@testing-library/user-event` | 14.6.12 | digitar e clicar como uma pessoa (foco, teclado, eventos na ordem real) |
| `@testing-library/jest-dom` | 7.0.1 | asserções legíveis: `toBeInTheDocument`, `toHaveAttribute`, `toBeDisabled`... |

**Desvios do guia, com motivo:**

1. **Sem `@vitejs/plugin-react`.** Na versão atual (6.x) ele puxa, por *peers*
   opcionais, o Babel 8, que conflita com o Babel 7 do `shadcn` do projeto. O npm recusa
   a instalação. Forçar com `--legacy-peer-deps` esconderia um conflito real. Em testes
   o plugin é dispensável: ele serve para Fast Refresh, e o Vite 8 já transforma JSX.
2. **Sem `vite-tsconfig-paths`.** O alias `@/` é uma linha no `vitest.config.mts`.
3. **Vitest 4, não 5.** O Vitest 5 exige `@types/node` ≥ 22, e o projeto declara `^20`.
   A imagem Docker já roda Node 22, então atualizar os tipos é correto, mas isso mexe numa
   dependência existente. Fica como recomendação (Parte F). O Vitest 4 é uma linha
   mantida (tag `V4` no npm).

Os testes de `src/lib`, escritos antes com o runner nativo do Node (`node:test`), foram
**migrados** para o Vitest, e o hook de alias que eles usavam foi apagado. Fica um runner
só.

### C.2 Configuração, linha a linha

`oncar/frontend/vitest.config.mts`:

```ts
export default defineConfig({
  resolve: {
    // "@/lib/api" -> src/lib/api.ts, igual ao "paths" do tsconfig.json
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  test: {
    environment: "jsdom",               // padrao: DOM simulado (componentes)
    setupFiles: ["./vitest.setup.ts"],  // roda antes de cada arquivo de teste
    include: ["src/**/*.test.{ts,tsx}"],
    restoreMocks: true,                 // desfaz vi.spyOn entre testes
    unstubGlobals: true,                // desfaz vi.stubGlobal entre testes
    css: false,                         // CSS nao importa para comportamento
  },
});
```

`restoreMocks` e `unstubGlobals` garantem que **nenhum teste dependa da ordem**: o `fetch`
simulado num teste não vaza para o seguinte.

`oncar/frontend/vitest.setup.ts` preenche **só** o que o jsdom não implementa e os
componentes usam:

| Polyfill | Quem precisa | O que imita |
|---|---|---|
| `window.matchMedia` | seletor de tema, lista de produtos, Motion | nenhuma media query casa; o teste troca com `vi.spyOn` quando precisa |
| `HTMLDialogElement.showModal/close` | `Dialogo` (busca global, assistente) | atributo `open` + evento `close`, que é o que o Esc dispara de verdade |
| `Element.prototype.scrollTo` | assistente (rola até a última mensagem) | não faz nada |

Cada polyfill só entra **se não existir** (`if (!window.matchMedia)`). Se um jsdom futuro
implementar o recurso, o real passa a valer.

Os testes de `src/lib` não usam DOM e declaram no topo:

```ts
// @vitest-environment node
// Sem DOM de proposito: no Node nao existe `document`, como na renderizacao do servidor.
```

### C.3 Utilitários de teste

- `src/lib/fixtures.test-utils.ts`: fábricas `produto()`, `movimentacao()` e `painel()` no
  formato dos DTOs da API. Cada teste muda só o campo que importa:
  `produto({ saldo: "0", abaixoDoMinimo: true })`.
- `src/components/oncar/oficina.test-utils.tsx`: `renderizarNaOficina(elemento, { sessao,
  dados })` envolve o componente com os dois contextos que o `AppShell` fornece em
  produção (usuário da sessão e dados da oficina). `usuario("ATENDENTE")` gera uma
  sessão com o perfil pedido.

O sufixo `.test-utils` não casa com `*.test.ts`, então esses arquivos não rodam como teste.

### C.4 Padrões usados (e por quê)

**1. Buscar como o usuário enxerga, nunca por classe CSS.**

```ts
screen.getByRole("button", { name: "Entrar" });
screen.getByLabelText("E-mail");
screen.getByRole("dialog", { name: "Busca global" });
```

Se um botão perde o rótulo acessível, o teste quebra. Isso protege acessibilidade e
comportamento ao mesmo tempo.

**2. Simular só o que é externo, com `vi.hoisted` + `vi.mock`.** O `useRouter` do Next só
funciona dentro do App Router de verdade:

```ts
// vi.mock e "icado" para o topo do arquivo; vi.hoisted cria o objeto antes disso,
// para o teste poder inspecionar as navegacoes depois.
const { roteador } = vi.hoisted(() => ({ roteador: { push: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => roteador }));
...
expect(roteador.push).toHaveBeenCalledWith("/produtos/3");
```

**3. Relógio falso só onde precisa.** No toast, apenas o `setTimeout` é falso, porque a
animação de saída do Motion precisa do relógio real:

```ts
vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout"] });
...
act(() => vi.advanceTimersByTime(4499));
expect(regiao()).toHaveTextContent("Entrada registrada");   // ainda na tela
act(() => vi.advanceTimersByTime(1));                       // 4500 ms
vi.useRealTimers();
await waitFor(() => expect(regiao()).not.toHaveTextContent("Entrada registrada"));
```

**4. Promessa controlada para testar o estado "carregando".**

```ts
let responder: (resposta: Response) => void = () => undefined;
api(() => new Promise((resolver) => (responder = resolver)));   // a API "nao responde"
...
await usuario.click(screen.getByRole("button", { name: "Entrar" }));
expect(screen.getByRole("button", { name: "Entrando…" })).toBeDisabled();   // sem login duplo
responder(json({ id: 1 }, 200));                                           // agora responde
expect(await screen.findByRole("button", { name: "Entrar" })).toBeEnabled();
```

**5. Armadilha documentada: `vi.resetModules` e contexto React.** A busca global guarda os
clientes num cache **de módulo**. Para cada teste começar sem cache, o módulo é
reimportado, e o helper de renderização **também**. Se não for, o componente e o helper
usariam cópias diferentes de `contexto.tsx`, e o componente acusaria *"useOficina
precisa estar dentro do AppShell"*. Isso aconteceu na primeira versão do teste (13
falhas) e está explicado no comentário de `paleta-de-comandos.test.tsx`.

### C.5 Mapa dos testes

| Arquivo (`frontend/src/...`) | O que garante | Testes |
|---|---|---:|
| `app/login/page.test.tsx` | envia `POST /api/sessao`, vai a `/painel`; erro do servidor; servidor fora do ar sem vazar detalhe técnico; botão desabilitado durante o envio; `required`; mostrar/ocultar senha; `autocomplete` para gerenciador de senhas | 8 |
| `app/(oficina)/produtos/lista.test.tsx` | esqueleto de carregamento; contagem "para repor"; **coluna de custo só para quem administra estoque**; busca com *debounce* (1 pedido); filtros na URL da API; filtro de nível local; limpar filtros; erro da API; visão em cartões lembrada | 12 |
| `components/oncar/paleta-de-comandos.test.tsx` | **atalhos por perfil** (proprietário, gerente, atendente, mecânico); SKU exato primeiro; navegação; Enter; clientes da API com cache; documentos agrupados e URL codificada; placa desabilitada | 13 |
| `components/oncar/assistente.test.tsx` | sugestões; pergunta → resposta; Enter; "Pensando…" bloqueia novas perguntas; falha vira resposta educada; links fecham o painel | 9 |
| `components/oncar/cartao-peca.test.tsx` | **custo só com permissão**; inativo; régua acessível e proporcional; cascata limitada | 11 |
| `components/oncar/dialogo.test.tsx` | monta só aberto; fundo fecha, painel não; evento `close` (Esc) avisa | 6 |
| `components/oncar/toasts.test.tsx` | região `status/polite`; máximo de 3; fechar; some em 4,5 s; seguro fora do provedor | 5 |
| `components/oncar/seletor-de-tema.test.tsx` | troca e salva; sem `localStorage`; View Transitions; menos movimento; transição abortada | 7 |
| `components/oncar/etiquetas.test.tsx` | limites de nível (1,5× o mínimo); texto além da cor; R$ em partes | 9 |
| `components/oncar/barras-de-giro.test.tsx` | link; singular/plural; **receita só quando a API manda**; proporção e mínimo de 2% | 5 |
| `components/oncar/sparkline.test.tsx` | geometria, série constante sem dividir por zero, marcador | 6 |
| `components/oncar/contexto.test.tsx` | `useOficina` fora do AppShell falha claro; `useGerenciaEstoque` por perfil | 6 |
| `components/oncar/avisos`, `chip`, `campo`, `estado-vazio` | alerta, `aria-pressed`, rótulo ligado ao campo, estado vazio | 13 |
| `hooks/use-debounce`, `use-media-query`, `use-modo-foco` | espera e reinício; ouvintes removidos; foco/ociosidade | 13 |
| `lib/tipos.test.ts` | **matriz de permissões** da interface | 5 |
| `lib/api`, `assistente`, `painel`, `sinais` (migrados) | cliente HTTP + CSRF, assistente, leituras do painel | 60 |
| **Total** | | **188** |

### C.6 Testes ligados à segurança no frontend

A interface **não protege nada sozinha**: quem autoriza é o backend. Mesmo assim, ela
precisa concordar com ele. Senão oferece um botão que a API recusa com 403, ou mostra um
dado que o perfil não deveria ver se a API um dia mandar a mais. Os testes que cuidam
disso:

- `lib/tipos.test.ts`: a matriz espelha `SecurityConfig`/`PermissoesApi`:

  | Perfil | Estoque/custos | Registrar saída | Financeiro | Relatórios |
  |---|:-:|:-:|:-:|:-:|
  | PROPRIETARIO | ✔ | ✔ | ✔ | ✔ |
  | GERENTE_ESTOQUE | ✔ | ✔ | — | ✔ |
  | ATENDENTE | — | ✔ | — | — |
  | MECANICO | — | — | — | — |

- `paleta-de-comandos.test.tsx`: o mecânico não vê **nenhum** atalho de lançamento, e o
  gerente não vê "Lançar despesa".
- `cartao-peca.test.tsx` e `lista.test.tsx`: custo médio não aparece para atendente.
- `barras-de-giro.test.tsx`: receita só aparece quando a API a envia.
- `lib/api.test.ts`: o token CSRF é lido do cookie `XSRF-TOKEN`, **decodificado** e
  devolvido no cabeçalho `X-XSRF-TOKEN`; sem cookie, a escrita busca a sessão antes;
  toda chamada vai com `credentials: "include"` e `cache: "no-store"`.
- `app/login/page.test.tsx`: erro de rede não expõe a mensagem técnica (`Failed to
  fetch`), e o botão bloqueia o envio duplo.

---

## Parte D — Como rodar

### Backend

```bash
cd oncar/backend
./mvnw test                                    # tudo (integracao + unitarios)
./mvnw test -Dtest='*UnitarioTest'             # so os unitarios dos servicos
./mvnw test -Dtest=SessoesAtivasServiceTest    # uma classe
```

No Windows, use `mvnw.cmd`. Os relatórios ficam em `target/surefire-reports/`.

### Frontend

```bash
cd oncar/frontend
npm test                                       # uma passada (CI)
npm run test:watch                             # modo observacao: reroda ao salvar
npx vitest run src/components/oncar/dialogo.test.tsx   # um arquivo
npx vitest run -t "custo"                      # so testes cujo nome contem "custo"
```

Antes de commitar, rode também `npx tsc --noEmit` e `npm run lint`. Os arquivos de teste
entram no `tsconfig` e no ESLint como o resto do código.

---

## Parte E — Validação: o que foi executado de verdade

| Comando | Resultado |
|---|---|
| `./mvnw test` **antes** de qualquer teste novo | 110 testes, 0 falhas |
| `./mvnw test` no final | **386 testes, 0 falhas, 0 ignorados — BUILD SUCCESS** |
| `npm test` (Vitest) | **24 arquivos, 188 testes, 0 falhas**, sem avisos no stderr |
| `npx tsc --noEmit` | sem erros |
| `npm run lint` | sem erros |

**Teste de mutação** ("o teste realmente pega um defeito?"): altera-se a regra de
produção de propósito, roda-se a suíte e depois se restaura o arquivo (`git diff`
conferido limpo).

| Mutação | Resultado |
|---|---|
| `EstoqueService`: justificativa mínima 10 → 9 e `>` → `>=` na verificação de saldo | **5 testes falharam** |
| `painel.ts`: limite das 12h da saudação e proteção contra divisão por zero | **2 testes falharam** |
| Componentes (atalho de despesa liberado ao gerente; custo exibido sem permissão) | **não executado.** O classificador de segurança do modo automático bloqueou a alteração temporária de código de produção. Se quiser, rode manualmente: troque `podeVerFinanceiro(usuario)` por `podeGerenciarEstoque(usuario)` em `paleta-de-comandos.tsx` e confira que `paleta-de-comandos.test.tsx` falha |

**Falhas encontradas durante a escrita**, todas nos próprios testes e corrigidas:

- `Thread.ofPlatform()` (Java 21) num projeto que compila para Java 17;
- um *stub* sem uso no Mockito (strict stubs);
- uma asserção vaga (`isIn`) trocada pelo comportamento real;
- um teste de colisão na `TabelaHash` que não provava o que dizia;
- erro de sintaxe (`() {`);
- o caso `vi.resetModules` + contexto (C.4, item 5);
- um aviso do jsdom (*"navigation to another Document"*) quando o `next/link` tentava
  navegar de verdade; o teste agora barra só a navegação nativa;
- um tipo frouxo no mock do `fetch` (`tsc`) e um *wrapper* sem nome (ESLint).

Nenhuma falha pré-existente na aplicação foi encontrada.

---

## Parte F — Pendências e recomendações

**Prioridade alta**
1. **Atualizar o Next.js** para uma versão fora da faixa do alerta crítico (A.9).
2. `npm audit fix` para as demais vulnerabilidades, revisando o resultado.

**Prioridade média**
3. Atualizar `@types/node` para `^22` (é o que a imagem Docker roda) e então subir para o
   **Vitest 5**.
4. Configurar o Mockito como `-javaagent` no `maven-surefire-plugin` (aviso do JDK 26).
5. Medir cobertura: JaCoCo no backend e `@vitest/coverage-v8` no frontend. Nenhum
   percentual foi medido, então nenhum é informado aqui.

**Cobertura que ainda falta**
6. Telas grandes do frontend ainda sem teste: `movimentacoes.tsx`, `despesas.tsx`,
   `relatorios.tsx`, `painel/page.tsx`, `produtos/[id]/extrato.tsx` e `app-shell.tsx`.
   Os padrões da Parte C se aplicam diretamente.
7. Componentes puramente visuais (`garage-wallpaper`, `curva-do-saldo`,
   `grafico-receita-despesa`, `kokonutui/*`): o retorno de testá-los é baixo. Se quiser
   proteger o visual, o caminho é teste de regressão visual (Playwright), não unitário.
8. Controllers do backend: hoje cobertos só pelos testes de integração. `@WebMvcTest`
   seria uma camada intermediária.

**Escolha de produto**
9. Decidir se `Formatos.moeda` deve arredondar como os serviços (HALF_UP). Ver B.4.

---

## Apêndice — Arquivos criados e modificados

### Nesta sessão de testes

**Backend — criados** (`oncar/backend/src/test/java/br/com/oncar/`):
`service/{Estoque,Produto,Usuario,Despesa,Relatorio,Auditoria}ServiceUnitarioTest.java`,
`service/{SessoesAtivas,DetalhesUsuario,Cliente,Fornecedor}ServiceTest.java`,
`service/relatorio/PeriodoTest.java`,
`web/controller/{Formatos,GraficoMensal}Test.java`,
`web/api/{PermissoesApi,TratadorDeErrosApi}Test.java`,
`config/{PrimeiroAcesso,ConversoresConfig}Test.java`,
`domain/model/{Produto,Usuario,Movimentacao}Test.java`,
`domain/enums/{MotivoMovimentacao,Perfil}Test.java`,
`estrutura/IndiceProdutosTest.java`.

**Backend — modificado:** `estrutura/TabelaHashTest.java` (+6 testes; os 5 originais
intactos).

**Frontend — criados** (`oncar/frontend/`):
`vitest.config.mts`, `vitest.setup.ts`,
`src/lib/{api,assistente,painel,sinais,tipos}.test.ts`, `src/lib/fixtures.test-utils.ts`,
`src/components/oncar/*.test.tsx` (14 arquivos), `src/components/oncar/oficina.test-utils.tsx`,
`src/hooks/{use-debounce,use-media-query}.test.ts`, `src/hooks/use-modo-foco.test.tsx`,
`src/app/login/page.test.tsx`, `src/app/(oficina)/produtos/lista.test.tsx`.

**Frontend — modificados:** `package.json` (scripts `test` e `test:watch`, 6
`devDependencies`) e `package-lock.json`.

**Código de produção alterado nesta sessão:** nenhum.

### Pendentes de antes desta sessão (Parte A)

`pom.xml`, `SecurityConfig.java`, `UsuarioService.java`, `SessaoApiController.java`,
`ContaController.java`, `application.properties`, `application-prod.properties`,
`templates/login.html`, `templates/home/inicio.html`, `templates/relatorio/fragmentos.html`,
`frontend/next.config.ts`, `frontend/.env.example`, `REQUIREMENTS.md`, `backend/README.md`
(modificados) e `SessoesConfig.java`, `VerificacaoDeProducao.java`,
`SessoesAtivasService.java`, `application-dev.properties`, `ConfiguracaoDePerfisTest.java`,
`RevogacaoDeSessoesTest.java` (novos), além dos ajustes nos testes de integração
(`ApiRestTest`, `ControleDeAcessoTest`, `FinanceiroWebTest`, `RenderizacaoDePaginasTest`).
