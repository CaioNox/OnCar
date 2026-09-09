# OnCar - Aplicacao Web

Sistema de gestao de estoque e controle financeiro para oficinas mecanicas de pequeno
e medio porte, implementado a partir do documento *OnCar - Documentacao da Aplicacao Web,
versao 2.0* (09/09/2026).

Esta entrega cobre o **nucleo do controle de estoque**: autenticacao e perfis, cadastros
de apoio, movimentacoes com saldo automatico, extrato, alerta de estoque minimo e auditoria.
As demais funcionalidades previstas na documentacao estao listadas em *Proximas etapas*.

## Tecnologias (RNF01, RNF02, RNF11 e RNF12)

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
mvn spring-boot:run
```

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
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### Testes

```bash
mvn test
```

## Requisitos implementados

### Funcionais

| Requisito | Onde |
| --- | --- |
| RF01 Autenticar usuario | `SecurityConfig`, `DetalhesUsuarioService`, `templates/login.html` |
| RF02 Manter usuarios | `UsuarioService`, `UsuarioController` |
| RF03 Recuperar e alterar senha | `UsuarioService.alterarSenha` e `redefinirSenha`, `ContaController` |
| RF04 Manter produtos | `ProdutoService`, `ProdutoController` |
| RF05 Manter fornecedores | `FornecedorService`, `FornecedorController` |
| RF06 Manter clientes e veiculos | `ClienteService`, `ClienteController` |
| RF07 Pesquisar produtos | `ProdutoRepository.pesquisar`, tela `produto/lista` |
| RF08 Registrar entrada | `EstoqueService.registrarEntrada` |
| RF09 Registrar saida | `EstoqueService.registrarSaida` |
| RF10 Atualizar saldo automaticamente | `EstoqueService` (todas as operacoes) |
| RF11 Ajuste de inventario | `EstoqueService.ajustarInventario` |
| RF12 Alertar estoque minimo | `ProdutoRepository.findAbaixoDoMinimo`, tela inicial e `/produtos/reposicao` |
| RF13 Historico de movimentacoes (kardex) | `EstoqueService.listarExtrato`, tela `produto/extrato` |
| RF20 Log de auditoria | `AuditoriaService`, tela `/auditoria` |

### Regras de negocio

| Regra | Onde |
| --- | --- |
| RN001 Unicidade do SKU (ignora caixa, espacos e inativos) | `ProdutoService.validarSkuUnico` |
| RN002 Impedimento de saldo negativo | `EstoqueService.registrarSaida` |
| RN003 Baixa e reposicao automaticas, em transacao unica | `EstoqueService` (`@Transactional`) |
| RN004 Custo medio ponderado | `EstoqueService.calcularCustoMedioPonderado` |
| RN005 Alerta de estoque minimo | `Produto.isAbaixoDoMinimo`, `ProdutoService.listarAbaixoDoMinimo` |
| RN006 Justificativa obrigatoria no ajuste | `EstoqueService.ajustarInventario` |
| RN007 Controle de acesso por perfil | `SecurityConfig`, `sec:authorize` nas telas |
| RN008 Imutabilidade das movimentacoes e estorno | `EstoqueService.estornar` |
| RN009 Inativacao em vez de exclusao | `inativar`/`excluir` de cada servico |
| RN010 Preco de venda x custo medio | `ProdutoService.validarMargem` |
| RN014 Politica de senhas e bloqueio de conta | `UsuarioService`, `SecurityConfig` |
| RN015 Expiracao de sessao em 30 minutos | `application.properties`, `SecurityConfig` |

### Nao-funcionais atendidos nesta entrega

RNF01 (Java 17 + Spring Boot + Thymeleaf/Bootstrap), RNF02 (PostgreSQL com transacoes),
RNF03 (layout responsivo do Bootstrap), RNF05 (Spring Security e BCrypt), RNF07 (operacoes
diarias em ate tres cliques a partir do menu), RNF11 (Docker e Git), RNF12 (MVC em camadas),
RNF13 (mensagens de erro em portugues e nao tecnicas) e RNF15 (testes automatizados das
regras de negocio, executados via JUnit).

## Observacoes de implementacao

- **Autenticacao por sessao.** O RNF05 cita JWT; como a aplicacao e renderizada no servidor
  com Thymeleaf, a autenticacao usa sessao do Spring Security (com expiracao de 30 minutos,
  conforme a RN015). O JWT sera adotado quando a API REST for exposta para consumo externo.
- **Envio de e-mail.** O RF03 preve redefinicao por link enviado por e-mail. Sem servidor SMTP
  configurado, a redefinicao e feita pelo perfil Proprietario na tela de usuarios, e a troca
  da propria senha fica em *Alterar senha*.
- **Custo medio no estorno.** Ao estornar uma entrada, o custo medio e recalculado para o valor
  anterior a compra. Quando o saldo remanescente e zero, o custo e mantido por nao ser apuravel.

## Proximas etapas

RF14 (ordens de servico), RF15 (despesas), RF16 (relatorios mensais), RF17 (painel gerencial
com graficos), RF18 (exportacao em PDF e Excel), RF19 (assistente de IA no chat interno) e as
regras RN011, RN012 e RN013.
