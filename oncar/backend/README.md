# OnCar - Aplicacao Web

Sistema de gestao de estoque e controle financeiro para oficinas mecanicas de pequeno
e medio porte, implementado a partir do documento *OnCar - Documentacao da Aplicacao Web,
versao 2.0* (09/09/2026).

Esta entrega cobre o **nucleo do controle de estoque**: autenticacao e perfis, cadastros
de apoio, movimentacoes com saldo automatico, extrato, alerta de estoque minimo e auditoria.
As demais funcionalidades previstas na documentacao estao listadas em *Proximas etapas*.

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
cada perfil e alguns produtos. Todos os usuarios de demonstracao usam a senha `oncar2026`:

| E-mail | Perfil |
| --- | --- |
| proprietario@oncar.com.br | Proprietario |
| gerente@oncar.com.br | Gerente de Estoque |
| atendente@oncar.com.br | Atendente |
| mecanico@oncar.com.br | Mecanico |

O console do H2 fica em <http://localhost:8080/h2-console> (JDBC `jdbc:h2:mem:oncar`,
usuario `sa`, sem senha).

### Producao (PostgreSQL)

```bash
docker compose up --build
```

Ou, com um PostgreSQL proprio:

```bash
export ONCAR_DB_URL=jdbc:postgresql://localhost:5432/oncar
export ONCAR_DB_USER=oncar
export ONCAR_DB_PASSWORD=suasenha
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

## Roadmap

Ordens de servico, despesas, relatorios mensais, painel gerencial com graficos, exportacao em PDF
e Excel e assistente com IA.
