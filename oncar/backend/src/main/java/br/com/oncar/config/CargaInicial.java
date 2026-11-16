package br.com.oncar.config;

import br.com.oncar.domain.enums.CategoriaDespesa;
import br.com.oncar.domain.enums.CategoriaProduto;
import br.com.oncar.domain.enums.MotivoMovimentacao;
import br.com.oncar.domain.enums.Perfil;
import br.com.oncar.domain.enums.UnidadeMedida;
import br.com.oncar.domain.model.Fornecedor;
import br.com.oncar.domain.model.Produto;
import br.com.oncar.domain.model.Usuario;
import br.com.oncar.repository.FornecedorRepository;
import br.com.oncar.repository.ProdutoRepository;
import br.com.oncar.repository.UsuarioRepository;
import br.com.oncar.service.DespesaService;
import br.com.oncar.service.EstoqueService;
import br.com.oncar.service.UsuarioService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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
                                           EstoqueService estoqueService,
                                           DespesaService despesaService) {
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
            Produto pastilha = criarProduto(produtoRepository, "PS-4410", "Pastilha de freio dianteira",
                    CategoriaProduto.PECA, UnidadeMedida.PECA, "Bosch",
                    new BigDecimal("189.90"), new BigDecimal("4"), distribuidora);

            // seis meses de operacao para o painel e os relatorios terem o que mostrar
            YearMonth mesAtual = YearMonth.now();
            YearMonth primeiroMes = mesAtual.minusMonths(5);
            String gerente = "gerente@oncar.com.br";
            estoqueService.registrarEntrada(oleo.getId(), new BigDecimal("60"), new BigDecimal("32.50"),
                    distribuidora, "NF-e 10021", dia(primeiroMes, 3), gerente);
            estoqueService.registrarEntrada(filtro.getId(), new BigDecimal("30"), new BigDecimal("18.90"),
                    distribuidora, "NF-e 10021", dia(primeiroMes, 3), gerente);
            estoqueService.registrarEntrada(pastilha.getId(), new BigDecimal("16"), new BigDecimal("95.00"),
                    distribuidora, "NF-e 10388", dia(primeiroMes.plusMonths(1), 5), gerente);

            int[] litrosDeOleo = {6, 8, 7, 9, 8, 7};
            int[] filtros = {4, 4, 3, 5, 4, 4};
            int[] pastilhas = {0, 2, 2, 2, 2, 2};
            BigDecimal[] energia = {new BigDecimal("412.30"), new BigDecimal("398.75"), new BigDecimal("455.10"),
                    new BigDecimal("501.42"), new BigDecimal("476.88"), new BigDecimal("430.05")};
            for (int indice = 0; indice < 6; indice++) {
                YearMonth mes = primeiroMes.plusMonths(indice);
                estoqueService.registrarSaida(oleo.getId(), BigDecimal.valueOf(litrosDeOleo[indice]),
                        MotivoMovimentacao.ORDEM_SERVICO, null, "OS " + (200 + indice), dia(mes, 12), gerente);
                estoqueService.registrarSaida(filtro.getId(), BigDecimal.valueOf(filtros[indice]),
                        MotivoMovimentacao.VENDA_BALCAO, null, null, dia(mes, 15), gerente);
                if (pastilhas[indice] > 0) {
                    estoqueService.registrarSaida(pastilha.getId(), BigDecimal.valueOf(pastilhas[indice]),
                            MotivoMovimentacao.ORDEM_SERVICO, null, "OS " + (300 + indice), dia(mes, 18), gerente);
                }
                lancarDespesa(despesaService, "Aluguel do galpao", CategoriaDespesa.ALUGUEL,
                        new BigDecimal("2500.00"), dia(mes, 5));
                lancarDespesa(despesaService, "Conta de energia", CategoriaDespesa.ENERGIA, energia[indice],
                        dia(mes, 10));
                lancarDespesa(despesaService, "Internet e telefone fixo", CategoriaDespesa.TELEFONE_INTERNET,
                        new BigDecimal("149.90"), dia(mes, 20));
            }
            lancarDespesa(despesaService, "Jogo de chaves combinadas", CategoriaDespesa.FERRAMENTAS,
                    new BigDecimal("389.00"), dia(mesAtual.minusMonths(2), 8));
        };
    }

    /** Dia do mes, sem passar de hoje quando o mes e o corrente. */
    private static LocalDate dia(YearMonth mes, int dia) {
        LocalDate data = mes.atDay(Math.min(dia, mes.lengthOfMonth()));
        LocalDate hoje = LocalDate.now();
        return data.isAfter(hoje) ? hoje : data;
    }

    private void lancarDespesa(DespesaService despesaService, String descricao, CategoriaDespesa categoria,
                               BigDecimal valor, LocalDate competencia) {
        despesaService.lancar(new DespesaService.DadosDespesa(descricao, categoria, valor, competencia,
                null, null, null), "proprietario@oncar.com.br");
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
