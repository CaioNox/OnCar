package br.com.oncar.config;

import br.com.oncar.domain.enums.CategoriaProduto;
import br.com.oncar.domain.enums.Perfil;
import br.com.oncar.domain.enums.UnidadeMedida;
import br.com.oncar.domain.model.Fornecedor;
import br.com.oncar.domain.model.Produto;
import br.com.oncar.domain.model.Usuario;
import br.com.oncar.repository.FornecedorRepository;
import br.com.oncar.repository.ProdutoRepository;
import br.com.oncar.repository.UsuarioRepository;
import br.com.oncar.service.EstoqueService;
import br.com.oncar.service.UsuarioService;
import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("dev")
public class CargaInicial {

    @Bean
    public CommandLineRunner carregarDados(UsuarioRepository usuarioRepository,
                                           FornecedorRepository fornecedorRepository,
                                           ProdutoRepository produtoRepository,
                                           UsuarioService usuarioService,
                                           EstoqueService estoqueService) {
        return argumentos -> {
            if (usuarioRepository.count() > 0) {
                return;
            }
            criarUsuario(usuarioService, "Caio Fabio", "proprietario@oncar.com.br", Perfil.PROPRIETARIO);
            criarUsuario(usuarioService, "Gabriel de Sousa", "gerente@oncar.com.br", Perfil.GERENTE_ESTOQUE);
            criarUsuario(usuarioService, "Ana Balcao", "atendente@oncar.com.br", Perfil.ATENDENTE);
            criarUsuario(usuarioService, "Marcos Mecanico", "mecanico@oncar.com.br", Perfil.MECANICO);

            Fornecedor distribuidora = new Fornecedor();
            distribuidora.setRazaoSocial("Distribuidora Automotiva Brasilia Ltda");
            distribuidora.setCnpj("12.345.678/0001-90");
            distribuidora.setContato("Roberto Almeida");
            distribuidora.setTelefone("(61) 3333-4444");
            distribuidora.setEmail("vendas@distribuidoradf.com.br");
            distribuidora.setPrazoMedioEntrega(5);
            fornecedorRepository.save(distribuidora);

            Produto oleo = criarProduto(produtoRepository, "OL-5W30", "Oleo sintetico 5W30 1L",
                    CategoriaProduto.LUBRIFICANTE, UnidadeMedida.LITRO, "Lubrax",
                    new BigDecimal("59.90"), new BigDecimal("12"), distribuidora);
            Produto filtro = criarProduto(produtoRepository, "FL-1023", "Filtro de oleo motor 1.0",
                    CategoriaProduto.FILTRO, UnidadeMedida.UNIDADE, "Tecfil",
                    new BigDecimal("38.00"), new BigDecimal("6"), distribuidora);
            criarProduto(produtoRepository, "PS-4410", "Pastilha de freio dianteira",
                    CategoriaProduto.PECA, UnidadeMedida.PECA, "Bosch",
                    new BigDecimal("189.90"), new BigDecimal("4"), distribuidora);

            estoqueService.registrarEntrada(oleo.getId(), new BigDecimal("20"), new BigDecimal("32.50"),
                    distribuidora, "NF-e 10021", null, "gerente@oncar.com.br");
            estoqueService.registrarEntrada(filtro.getId(), new BigDecimal("5"), new BigDecimal("18.90"),
                    distribuidora, "NF-e 10021", null, "gerente@oncar.com.br");
        };
    }

    private void criarUsuario(UsuarioService usuarioService, String nome, String email, Perfil perfil) {
        Usuario usuario = new Usuario();
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setPerfil(perfil);
        usuarioService.cadastrar(usuario, "oncar2026");
    }

    private Produto criarProduto(ProdutoRepository repositorio, String sku, String descricao,
                                 CategoriaProduto categoria, UnidadeMedida unidade, String marca,
                                 BigDecimal precoVenda, BigDecimal estoqueMinimo, Fornecedor fornecedor) {
        Produto produto = new Produto();
        produto.setSku(sku);
        produto.setDescricao(descricao);
        produto.setCategoria(categoria);
        produto.setUnidadeMedida(unidade);
        produto.setMarca(marca);
        produto.setPrecoVenda(precoVenda);
        produto.setEstoqueMinimo(estoqueMinimo);
        produto.setFornecedorPadrao(fornecedor);
        return repositorio.save(produto);
    }
}
