package br.com.oncar.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.oncar.TesteIntegracao;
import br.com.oncar.domain.enums.MotivoMovimentacao;
import br.com.oncar.domain.model.Cliente;
import br.com.oncar.domain.model.Fornecedor;
import br.com.oncar.domain.model.Produto;
import br.com.oncar.domain.model.Veiculo;
import br.com.oncar.service.ClienteService;
import br.com.oncar.service.FornecedorService;
import br.com.oncar.service.EstoqueService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@WithMockUser(username = "proprietario@teste.com.br", roles = "PROPRIETARIO")
class RenderizacaoDePaginasTest extends TesteIntegracao {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstoqueService estoqueService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private FornecedorService fornecedorService;

    private Produto produto;

    @BeforeEach
    void prepararDados() {
        criarGerente();
        produto = criarProduto("FL-RENDER", new BigDecimal("5"));
        produto.setFornecedorPadrao(cadastrarFornecedor());
        produtoRepository.save(produto);
        estoqueService.registrarEntrada(produto.getId(), new BigDecimal("10"), new BigDecimal("20.00"),
                null, "NF-e 77", null, EMAIL_GERENTE);
        estoqueService.registrarSaida(produto.getId(), new BigDecimal("6"), MotivoMovimentacao.VENDA_BALCAO,
                null, "OS 1", null, EMAIL_GERENTE);

        Cliente cliente = new Cliente();
        cliente.setNome("Joao da Silva");
        cliente.setDocumento("123.456.789-00");
        Veiculo veiculo = new Veiculo();
        veiculo.setPlaca("ABC1D23");
        veiculo.setModelo("Gol 1.0");
        veiculo.setAno(2018);
        cliente.adicionarVeiculo(veiculo);
        clienteService.cadastrar(cliente);
    }

    private Fornecedor cadastrarFornecedor() {
        Fornecedor fornecedor = new Fornecedor();
        fornecedor.setRazaoSocial("Distribuidora Teste Ltda");
        fornecedor.setCnpj("12.345.678/0001-90");
        return fornecedorService.cadastrar(fornecedor);
    }

    @Test
    @DisplayName("A tela inicial mostra as movimentacoes e a lista de reposicao")
    void telaInicialRenderizaComDados() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("FL-RENDER")));
    }

    @Test
    @DisplayName("A lista de reposicao mostra o produto abaixo do minimo com o fornecedor")
    void listaDeReposicaoRenderiza() throws Exception {
        mockMvc.perform(get("/produtos/reposicao")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A lista de movimentacoes e o extrato renderizam com produto e usuario")
    void movimentacoesRenderizam() throws Exception {
        mockMvc.perform(get("/movimentacoes")).andExpect(status().isOk());
        mockMvc.perform(get("/produtos/{id}/extrato", produto.getId())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A lista de clientes mostra os veiculos associados")
    void clientesRenderizamComVeiculos() throws Exception {
        mockMvc.perform(get("/clientes"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ABC1D23")));
    }

    @Test
    @DisplayName("Os formularios de cadastro e edicao renderizam")
    void formulariosRenderizam() throws Exception {
        mockMvc.perform(get("/produtos/{id}/editar", produto.getId())).andExpect(status().isOk());
        mockMvc.perform(get("/clientes/novo")).andExpect(status().isOk());
        mockMvc.perform(get("/usuarios/novo")).andExpect(status().isOk());
        mockMvc.perform(get("/conta/senha")).andExpect(status().isOk());
    }
}
