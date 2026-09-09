package br.com.oncar;

import br.com.oncar.domain.enums.CategoriaProduto;
import br.com.oncar.domain.enums.Perfil;
import br.com.oncar.domain.enums.UnidadeMedida;
import br.com.oncar.domain.model.Produto;
import br.com.oncar.domain.model.Usuario;
import br.com.oncar.repository.ClienteRepository;
import br.com.oncar.repository.FornecedorRepository;
import br.com.oncar.repository.LogAuditoriaRepository;
import br.com.oncar.repository.MovimentacaoRepository;
import br.com.oncar.repository.ProdutoRepository;
import br.com.oncar.repository.SenhaHistoricoRepository;
import br.com.oncar.repository.UsuarioRepository;
import br.com.oncar.service.ProdutoService;
import br.com.oncar.service.UsuarioService;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base dos testes de integracao: sobe o contexto com o banco em memoria e
 * garante o mesmo estado inicial para cada cenario.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class TesteIntegracao {

    protected static final String EMAIL_GERENTE = "gerente@teste.com.br";

    @Autowired
    protected UsuarioService usuarioService;

    @Autowired
    protected ProdutoService produtoService;

    @Autowired
    protected UsuarioRepository usuarioRepository;

    @Autowired
    protected ProdutoRepository produtoRepository;

    @Autowired
    protected MovimentacaoRepository movimentacaoRepository;

    @Autowired
    protected FornecedorRepository fornecedorRepository;

    @Autowired
    protected ClienteRepository clienteRepository;

    @Autowired
    protected SenhaHistoricoRepository senhaHistoricoRepository;

    @Autowired
    protected LogAuditoriaRepository logAuditoriaRepository;

    @BeforeEach
    void limparBase() {
        movimentacaoRepository.deleteAll();
        produtoRepository.deleteAll();
        clienteRepository.deleteAll();
        fornecedorRepository.deleteAll();
        senhaHistoricoRepository.deleteAll();
        usuarioRepository.deleteAll();
        logAuditoriaRepository.deleteAll();
    }

    @AfterEach
    void limparAutenticacao() {
        SecurityContextHolder.clearContext();
    }

    protected Usuario criarGerente() {
        Usuario usuario = new Usuario();
        usuario.setNome("Gerente de Estoque");
        usuario.setEmail(EMAIL_GERENTE);
        usuario.setPerfil(Perfil.GERENTE_ESTOQUE);
        return usuarioService.cadastrar(usuario, "oncar2026");
    }

    protected Produto criarProduto(String sku, BigDecimal estoqueMinimo) {
        Produto produto = new Produto();
        produto.setSku(sku);
        produto.setDescricao("Filtro de oleo " + sku);
        produto.setCategoria(CategoriaProduto.FILTRO);
        produto.setUnidadeMedida(UnidadeMedida.UNIDADE);
        produto.setPrecoVenda(new BigDecimal("100.00"));
        produto.setEstoqueMinimo(estoqueMinimo);
        return produtoService.cadastrar(produto, false);
    }
}
