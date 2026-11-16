# OnCar - Aplicacao Web

Sistema de gestao de estoque e controle financeiro para oficinas mecanicas de pequeno
e medio porte, implementado a partir do documento *OnCar - Documentacao da Aplicacao Web,
versao 2.0* (09/09/2026).

Esta entrega cobre o **controle de estoque** (autenticacao e perfis, cadastros de apoio,
movimentacoes com saldo automatico, extrato, alerta de estoque minimo e auditoria) e a
**gestao financeira** (despesas operacionais, relatorios mensais e painel gerencial).
As demais funcionalidades previstas na documentacao estao listadas no *Roadmap*.

## Tecnologias

| Camada | Tecnologia |
| --- | --- |
| Linguagem | Java 17 |
| Back-end | Spring Boot 3.3, Spring MVC, Spring Data JPA/Hibernate |
| Seguranca | Spring Security 6 com hash BCrypt |
| Apresentacao | Thymeleaf + Bootstrap 5 (responsivo) |
| Banco (desenvolvimento) | H2 em memoria |
| Banco (producao) | PostgreSQL |
| Testes | JUnit 5, Spring Boot Test, MockMvc |
| Empacotamento | Maven, Docker e docker compose |

A organizacao segue o padrao MVC com separacao entre apresentacao (`web`), servico
(`service`), persistencia (`repository`) e dominio (`domain`).

## Como executar

### Desenvolvimento (H2 em memoria, com dados de demonstracao)

```bash
./mvnw spring-boot:run
```

> No Windows use `.\mvnw.cmd spring-boot:run`. O wrapper baixa o Maven 3.9 na
> primeira execucao, entao nao e preciso instalar o Maven. Requer apenas um JDK
> (17 ou mais recente; testado ate o 26).

A aplicacao sobe em <http://localhost:8080> com o perfil `dev`, que cria um usuario de
cada perfil, alguns produtos e seis meses de vendas e despesas, para o painel e os relatorios
terem o que mostrar. Todos os usuarios de demonstracao usam a senha `oncar2026`:

| E-mail | Perfil |
| --- | --- |
| proprietario@oncar.com.br | Proprietario |
| gerente@oncar.com.br | Gerente de Estoque |
| atendente@oncar.com.br | Atendente |
| mecanico@oncar.com.br | Mecanico |

O console do H2 fica em <http://localhost:8080/h2-console> (JDBC `jdbc:h2:mem:oncar`,
usuario `sa`, sem senha).

### Producao (PostgreSQL)

Com Docker, nesta pasta (PostgreSQL + API com as telas Thymeleaf):

```bash
cp .env.example .env   # senha do banco e o primeiro proprietario
docker compose up --build
```

Para subir tambem o front-end Next.js, use o `docker-compose.yml` da pasta `oncar/`.

O perfil `prod` comeca sem usuarios e sem dados de demonstracao. Enquanto a base nao tiver
nenhum usuario, a aplicacao cria um proprietario com `ONCAR_ADMIN_EMAIL` e `ONCAR_ADMIN_SENHA`
(senha com 8 caracteres ou mais, letra e numero); depois disso as duas variaveis sao ignoradas.

Ou, com um PostgreSQL proprio:

```bash
export ONCAR_DB_URL=jdbc:postgresql://localhost:5432/oncar
export ONCAR_DB_USER=oncar
export ONCAR_DB_PASSWORD=suasenha
export ONCAR_ADMIN_EMAIL=proprietario@suaoficina.com.br
export ONCAR_ADMIN_SENHA=troque123
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

### Testes

```bash
./mvnw test
```

## Funcionalidades

| Funcionalidade | Onde |
| --- | --- |
| Autenticacao | `SecurityConfig`, `DetalhesUsuarioService`, `templates/login.html` |
| Usuarios | `UsuarioService`, `UsuarioController` |
| Troca e redefinicao de senha | `UsuarioService.alterarSenha` e `redefinirSenha`, `ContaController` |
| Produtos | `ProdutoService`, `ProdutoController` |
| Fornecedores | `FornecedorService`, `FornecedorController` |
| Clientes e veiculos | `ClienteService`, `ClienteController` |
| Pesquisa de produtos | `ProdutoRepository.pesquisar`, tela `produto/lista` |
| Entrada e saida de mercadoria | `EstoqueService.registrarEntrada` e `registrarSaida` |
| Ajuste de inventario | `EstoqueService.ajustarInventario` |
| Alerta de estoque minimo | `ProdutoRepository.findAbaixoDoMinimo`, tela inicial e `/produtos/reposicao` |
| Kardex por produto | `EstoqueService.listarExtrato`, tela `produto/extrato` |
| Auditoria | `AuditoriaService`, tela `/auditoria` |
| Despesas operacionais | `DespesaService`, `DespesaController` (`/despesas`), `DespesaApiController` |
| Relatorios mensais (entradas, saidas, gastos e resultado, posicao de estoque) | `RelatorioService`, `RelatorioController` (`/relatorios`), `RelatorioApiController` |
| Painel gerencial (receita x despesa, maior giro) | `RelatorioService.indicadoresFinanceiros` e `maiorGiro`, tela inicial e `/api/painel` |

## Regras de negocio

| Regra | Onde |
| --- | --- |
| SKU unico (ignora caixa, espacos e inativos) | `ProdutoService.validarSkuUnico` |
| Saldo nunca fica negativo | `EstoqueService.registrarSaida` |
| Baixa e reposicao em transacao unica | `EstoqueService` (`@Transactional`) |
| Custo medio ponderado | `EstoqueService.calcularCustoMedioPonderado` |
| Alerta de estoque minimo | `Produto.isAbaixoDoMinimo`, `ProdutoService.listarAbaixoDoMinimo` |
| Justificativa obrigatoria no ajuste | `EstoqueService.ajustarInventario` |
| Controle de acesso por perfil | `SecurityConfig`, `sec:authorize` nas telas |
| Movimentacoes imutaveis, corrigidas por estorno | `EstoqueService.estornar` |
| Inativacao em vez de exclusao | `inativar`/`excluir` de cada servico |
| Preco de venda abaixo do custo exige confirmacao | `ProdutoService.validarMargem` |
| Politica de senhas e bloqueio de conta | `UsuarioService`, `SecurityConfig` |
| Sessao expira apos 30 minutos | `application.properties`, `SecurityConfig` |
| Relatorios e painel apurados pelo mes de competencia | `RelatorioService`, `Relatorios.Periodo` |
| Despesas, gastos e resultado so para o proprietario; gerente emite os relatorios operacionais | `SecurityConfig`, `PermissoesApi.podeVerFinanceiro` |

## Stack

Java 17, Spring Boot, Spring Security (BCrypt), Thymeleaf/Bootstrap, PostgreSQL em producao e H2
em desenvolvimento, Docker e testes com JUnit.

## Observacoes de implementacao

- **Autenticacao por sessao.** O front-end e o backend compartilham a mesma origem, entao a
  autenticacao usa a sessao do Spring Security com CSRF e expiracao de 30 minutos.
- **Redefinicao de senha.** Sem servidor SMTP configurado, a redefinicao e feita pelo perfil
  Proprietario na tela de usuarios; a troca da propria senha fica em *Alterar senha*.
- **Custo medio no estorno.** Ao estornar uma entrada, o custo medio volta ao valor anterior a
  compra. Quando o saldo remanescente e zero, o custo e mantido por nao ser apuravel.
- **Apuracao do resultado.** Receita = vendas de balcao e pecas aplicadas em OS, pelo preco de
  venda gravado em cada saida. Despesa = custo medio das pecas vendidas + perdas e avarias +
  despesas operacionais. Compras de mercadoria viram estoque e aparecem a parte, sem reduzir o
  resultado. Lancamentos estornados e os seus estornos ficam fora dos relatorios.
- **Preco e custo historicos.** Cada movimentacao grava o preco de venda praticado e o custo medio
  resultante, para que mudancas posteriores no cadastro nao reescrevam meses ja apurados.
  Lancamentos gravados antes dessas colunas usam o preco e o custo atuais.
- **Posicao de estoque.** Por ser fisica, segue a data de cada lancamento (saldo inicial +
  entradas - saidas + ajustes e estornos = saldo final); os demais relatorios seguem a competencia.
- **Despesas.** A competencia nao pode ser de um mes futuro. Fornecedores e usuarios com despesas
  vinculadas so podem ser inativados.

## Roadmap

Ordens de servico, exportacao dos relatorios em PDF e Excel, bloqueio de lancamentos retroativos
em meses ja encerrados e assistente com IA.
