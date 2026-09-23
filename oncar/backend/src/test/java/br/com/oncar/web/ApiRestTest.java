package br.com.oncar.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.oncar.TesteIntegracao;
import br.com.oncar.domain.model.Produto;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ApiRestTest extends TesteIntegracao {

    @Autowired
    private MockMvc mockMvc;

    private Produto produto;

    @BeforeEach
    void prepararCenario() {
        criarGerente();
        produto = criarProduto("API-001", new BigDecimal("5"));
    }

    @Test
    @DisplayName("RF01: o login pela API devolve o perfil do usuario autenticado")
    void loginPelaApiDevolvePerfil() throws Exception {
        mockMvc.perform(post("/api/sessao").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL_GERENTE + "\",\"senha\":\"oncar2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL_GERENTE))
                .andExpect(jsonPath("$.perfil").value("GERENTE_ESTOQUE"));
    }

    @Test
    @DisplayName("RF01: credenciais invalidas devolvem 401 com mensagem em portugues")
    void loginInvalidoDevolve401() throws Exception {
        mockMvc.perform(post("/api/sessao").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL_GERENTE + "\",\"senha\":\"errada123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("invalidos")));
    }

    @Test
    @DisplayName("RF01: requisicao sem sessao recebe 401 em vez de redirecionamento")
    void apiSemSessaoDevolve401() throws Exception {
        mockMvc.perform(get("/api/painel")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "gerente@teste.com.br", roles = "GERENTE_ESTOQUE")
    @DisplayName("RF08 e RF10: a entrada pela API atualiza o saldo do produto")
    void entradaPelaApiAtualizaSaldo() throws Exception {
        mockMvc.perform(post("/api/movimentacoes/entrada").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\":" + produto.getId()
                                + ",\"quantidade\":10,\"custoUnitario\":20.00,\"documento\":\"NF-e 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoResultante").value(10.000));

        mockMvc.perform(get("/api/produtos/{id}", produto.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(10.000))
                .andExpect(jsonPath("$.precoCusto").value(20.0000));
    }

    @Test
    @WithMockUser(username = "gerente@teste.com.br", roles = "GERENTE_ESTOQUE")
    @DisplayName("RN002: a saida acima do saldo devolve 400 com a mensagem da regra")
    void saidaAcimaDoSaldoDevolve400() throws Exception {
        mockMvc.perform(post("/api/movimentacoes/saida").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\":" + produto.getId()
                                + ",\"quantidade\":5,\"motivo\":\"VENDA_BALCAO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem")
                        .value(org.hamcrest.Matchers.containsString("Saldo insuficiente")));
    }

    @Test
    @WithMockUser(username = "gerente@teste.com.br", roles = "GERENTE_ESTOQUE")
    @DisplayName("RN006: o ajuste sem justificativa suficiente devolve 400 com o detalhe do campo")
    void ajusteSemJustificativaDevolve400() throws Exception {
        mockMvc.perform(post("/api/movimentacoes/ajuste").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\":" + produto.getId()
                                + ",\"saldoApurado\":3,\"justificativa\":\"erro\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalhes[0]")
                        .value(org.hamcrest.Matchers.containsString("10 caracteres")));
    }

    @Test
    @WithMockUser(username = "atendente@teste.com.br", roles = "ATENDENTE")
    @DisplayName("RN007: o atendente consulta produtos sem enxergar o custo medio")
    void atendenteNaoRecebeCustoNaApi() throws Exception {
        mockMvc.perform(get("/api/produtos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("API-001"))
                .andExpect(jsonPath("$[0].precoVenda").exists())
                .andExpect(jsonPath("$[0].precoCusto").doesNotExist());
    }

    @Test
    @WithMockUser(username = "atendente@teste.com.br", roles = "ATENDENTE")
    @DisplayName("RN007: o atendente nao registra entrada e nao le a auditoria pela API")
    void atendenteEhBloqueadoNaApi() throws Exception {
        mockMvc.perform(post("/api/movimentacoes/entrada").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\":" + produto.getId()
                                + ",\"quantidade\":1,\"custoUnitario\":10.00}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem")
                        .value(org.hamcrest.Matchers.containsString("nao permite")));

        mockMvc.perform(get("/api/auditoria")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "proprietario@teste.com.br", roles = "PROPRIETARIO")
    @DisplayName("RF12: o painel devolve indicadores, lista de reposicao e ultimas movimentacoes")
    void painelDevolveIndicadores() throws Exception {
        mockMvc.perform(get("/api/painel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.produtosAtivos").value(1))
                .andExpect(jsonPath("$.itensAbaixoDoMinimo").value(1))
                .andExpect(jsonPath("$.listaDeReposicao[0].sku").value("API-001"))
                .andExpect(jsonPath("$.valorTotalEmEstoque").exists());
    }

    @Test
    @WithMockUser(username = "gerente@teste.com.br", roles = "GERENTE_ESTOQUE")
    @DisplayName("RF13: o extrato do produto e exposto pela API")
    void extratoDisponivelNaApi() throws Exception {
        mockMvc.perform(post("/api/movimentacoes/entrada").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\":" + produto.getId()
                                + ",\"quantidade\":4,\"custoUnitario\":15.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/produtos/{id}/extrato", produto.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("ENTRADA"))
                .andExpect(jsonPath("$[0].estornavel").value(true));
    }
}
