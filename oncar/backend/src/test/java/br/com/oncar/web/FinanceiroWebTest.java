package br.com.oncar.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.oncar.TesteIntegracao;
import br.com.oncar.domain.enums.CategoriaDespesa;
import br.com.oncar.domain.enums.MotivoMovimentacao;
import br.com.oncar.domain.model.Despesa;
import br.com.oncar.domain.model.Produto;
import br.com.oncar.service.DespesaService;
import br.com.oncar.service.EstoqueService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Despesas, relatorios e painel gerencial: telas, API e acesso por perfil. */
@AutoConfigureMockMvc
class FinanceiroWebTest extends TesteIntegracao {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstoqueService estoqueService;

    @Autowired
    private DespesaService despesaService;

    private Produto produto;
    private Despesa aluguel;

    @BeforeEach
    void prepararDados() {
        criarGerente();
        criarProprietario();
        produto = criarProduto("FL-FIN", new BigDecimal("2"));
        estoqueService.registrarEntrada(produto.getId(), new BigDecimal("10"), new BigDecimal("40.00"),
                null, "NF-e 5", null, EMAIL_GERENTE);
        estoqueService.registrarSaida(produto.getId(), new BigDecimal("3"), MotivoMovimentacao.VENDA_BALCAO,
                null, null, null, EMAIL_GERENTE);
        aluguel = despesaService.lancar(new DespesaService.DadosDespesa("Aluguel do galpao",
                CategoriaDespesa.ALUGUEL, new BigDecimal("1500.00"), LocalDate.now(), null, "Recibo 10", null),
                EMAIL_PROPRIETARIO);
    }

    @Test
    @WithMockUser(username = EMAIL_PROPRIETARIO, roles = "PROPRIETARIO")
    @DisplayName("o proprietario abre despesas, formulario e todos os relatorios")
    void proprietarioRenderizaTelas() throws Exception {
        mockMvc.perform(get("/despesas"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Aluguel do galpao")));
        mockMvc.perform(get("/despesas/nova")).andExpect(status().isOk());
        mockMvc.perform(get("/despesas/{id}/editar", aluguel.getId())).andExpect(status().isOk());
        mockMvc.perform(get("/relatorios")).andExpect(redirectedUrl("/relatorios/entradas"));
        mockMvc.perform(get("/relatorios/entradas")).andExpect(status().isOk())
                .andExpect(content().string(containsString("FL-FIN")));
        mockMvc.perform(get("/relatorios/saidas")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Venda de balcao")));
        mockMvc.perform(get("/relatorios/gastos")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Aluguel do galpao")));
        mockMvc.perform(get("/relatorios/posicao")).andExpect(status().isOk())
                .andExpect(content().string(containsString("FL-FIN")));
    }

    @Test
    @WithMockUser(username = EMAIL_PROPRIETARIO, roles = "PROPRIETARIO")
    @DisplayName("a tela inicial do proprietario mostra receita, despesa e o maior giro")
    void telaInicialComPainelGerencial() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Receita do mes")))
                .andExpect(content().string(containsString("Receita x despesa")))
                .andExpect(content().string(containsString("Pecas de maior giro")));
    }

    @Test
    @WithMockUser(username = EMAIL_PROPRIETARIO, roles = "PROPRIETARIO")
    @DisplayName("o formulario de despesa devolve a mensagem da regra sem perder o que foi digitado")
    void formularioMostraErroDeRegra() throws Exception {
        String proximoMes = YearMonth.now().plusMonths(1).atDay(1).toString();
        mockMvc.perform(post("/despesas/salvar").with(csrf())
                        .param("descricao", "Aluguel adiantado")
                        .param("categoria", "ALUGUEL")
                        .param("valor", "1500")
                        .param("competencia", proximoMes))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("mes futuro")))
                .andExpect(content().string(containsString("Aluguel adiantado")));
    }

    @Test
    @WithMockUser(username = EMAIL_PROPRIETARIO, roles = "PROPRIETARIO")
    @DisplayName("o formulario de despesa grava e volta para o mes da competencia")
    void formularioGravaDespesa() throws Exception {
        mockMvc.perform(post("/despesas/salvar").with(csrf())
                        .param("descricao", "Conta de energia")
                        .param("categoria", "ENERGIA")
                        .param("valor", "420.50")
                        .param("competencia", LocalDate.now().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/despesas?inicio=" + YearMonth.now()));
    }

    @Test
    @WithMockUser(username = EMAIL_GERENTE, roles = "GERENTE_ESTOQUE")
    @DisplayName("o gerente emite os relatorios operacionais, mas nao ve gastos nem despesas")
    void gerenteSoVeRelatoriosOperacionais() throws Exception {
        mockMvc.perform(get("/relatorios/entradas")).andExpect(status().isOk());
        mockMvc.perform(get("/relatorios/posicao")).andExpect(status().isOk());
        mockMvc.perform(get("/relatorios/gastos")).andExpect(redirectedUrl("/acesso-negado"));
        mockMvc.perform(get("/despesas")).andExpect(redirectedUrl("/acesso-negado"));
        mockMvc.perform(get("/api/relatorios/saidas")).andExpect(status().isOk());
        mockMvc.perform(get("/api/relatorios/gastos")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/despesas")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "atendente@teste.com.br", roles = "ATENDENTE")
    @DisplayName("o atendente nao acessa relatorios nem despesas")
    void atendenteSemRelatorios() throws Exception {
        mockMvc.perform(get("/relatorios/saidas")).andExpect(redirectedUrl("/acesso-negado"));
        mockMvc.perform(get("/api/relatorios/posicao")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/despesas")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("visitante sem sessao e levado ao login nas telas novas")
    void visitanteVaiAoLogin() throws Exception {
        mockMvc.perform(get("/despesas"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/api/relatorios/entradas")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = EMAIL_PROPRIETARIO, roles = "PROPRIETARIO")
    @DisplayName("a API lanca, lista e exclui despesas")
    void apiDeDespesas() throws Exception {
        mockMvc.perform(post("/api/despesas").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\":\"Conta de agua\",\"categoria\":\"AGUA\",\"valor\":89.90,"
                                + "\"competencia\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriaDescricao").value("Agua e esgoto"))
                .andExpect(jsonPath("$.usuario").value("Dono da Oficina"));

        mockMvc.perform(get("/api/despesas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(delete("/api/despesas/{id}", aluguel.getId()).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/despesas")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(username = EMAIL_PROPRIETARIO, roles = "PROPRIETARIO")
    @DisplayName("a API recusa categoria invalida e mes mal formatado com mensagem em portugues")
    void apiRecusaParametrosInvalidos() throws Exception {
        mockMvc.perform(post("/api/despesas").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\":\"Conta de agua\",\"categoria\":\"PIZZA\",\"valor\":10,"
                                + "\"competencia\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(containsString("categoria de despesa valida")));

        mockMvc.perform(get("/api/relatorios/entradas").param("inicio", "outubro"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(containsString("inicio")));
    }

    @Test
    @WithMockUser(username = EMAIL_PROPRIETARIO, roles = "PROPRIETARIO")
    @DisplayName("os relatorios da API devolvem totais do periodo")
    void apiDeRelatorios() throws Exception {
        String mes = YearMonth.now().toString();
        mockMvc.perform(get("/api/relatorios/saidas").param("inicio", mes).param("fim", mes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receita").value(300.00))
                .andExpect(jsonPath("$.custoDasVendas").value(120.00));
        mockMvc.perform(get("/api/relatorios/gastos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDespesas").value(1500.00))
                .andExpect(jsonPath("$.consolidado.resultado").value(-1320.00));
        mockMvc.perform(get("/api/relatorios/filtros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriasDeDespesa[0].valor").value("ALUGUEL"));
    }

    @Test
    @WithMockUser(username = EMAIL_PROPRIETARIO, roles = "PROPRIETARIO")
    @DisplayName("o painel do proprietario traz receita, despesa e maior giro")
    void painelDoProprietario() throws Exception {
        mockMvc.perform(get("/api/painel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financeiro.mesAtual.receita").value(300.00))
                .andExpect(jsonPath("$.financeiro.mesAtual.despesasOperacionais").value(1500.00))
                .andExpect(jsonPath("$.financeiro.historico.length()").value(6))
                .andExpect(jsonPath("$.maiorGiro[0].sku").value("FL-FIN"))
                .andExpect(jsonPath("$.maiorGiro[0].receita").value(300.00));
    }

    @Test
    @WithMockUser(username = EMAIL_GERENTE, roles = "GERENTE_ESTOQUE")
    @DisplayName("o painel do gerente traz o maior giro sem receita nem financeiro")
    void painelDoGerente() throws Exception {
        mockMvc.perform(get("/api/painel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financeiro").doesNotExist())
                .andExpect(jsonPath("$.maiorGiro[0].sku").value("FL-FIN"))
                .andExpect(jsonPath("$.maiorGiro[0].receita").doesNotExist());
    }
}
