package br.com.oncar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.oncar.domain.enums.AcaoAuditoria;
import br.com.oncar.domain.model.Fornecedor;
import br.com.oncar.repository.DespesaRepository;
import br.com.oncar.repository.FornecedorRepository;
import br.com.oncar.repository.MovimentacaoRepository;
import br.com.oncar.service.exception.RegistroNaoEncontradoException;
import br.com.oncar.service.exception.RegraNegocioException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FornecedorServiceTest {

    private static final String CNPJ = "12.345.678/0001-90";

    @Mock
    private FornecedorRepository fornecedorRepository;
    @Mock
    private MovimentacaoRepository movimentacaoRepository;
    @Mock
    private DespesaRepository despesaRepository;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private FornecedorService fornecedorService;

    private static Fornecedor fornecedor(Long id, String cnpj) {
        Fornecedor fornecedor = new Fornecedor();
        fornecedor.setId(id);
        fornecedor.setRazaoSocial("Auto Pecas " + id);
        fornecedor.setCnpj(cnpj);
        return fornecedor;
    }

    @Test
    @DisplayName("CNPJ ja cadastrado e recusado com o nome de quem o usa")
    void cnpjRepetido() {
        when(fornecedorRepository.findByCnpj(CNPJ)).thenReturn(Optional.of(fornecedor(1L, CNPJ)));

        assertThatThrownBy(() -> fornecedorService.cadastrar(fornecedor(null, CNPJ)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("ja esta cadastrado para Auto Pecas 1");
        verify(fornecedorRepository, never()).save(any());
    }

    @Test
    @DisplayName("o cadastro grava e audita")
    void cadastra() {
        Fornecedor novo = fornecedor(null, CNPJ);
        when(fornecedorRepository.findByCnpj(CNPJ)).thenReturn(Optional.empty());
        when(fornecedorRepository.save(novo)).thenAnswer(chamada -> {
            novo.setId(6L);
            return novo;
        });

        fornecedorService.cadastrar(novo);

        verify(auditoriaService).registrar(eq(AcaoAuditoria.INCLUSAO), eq("Fornecedor"), eq(6L), anyString());
    }

    @Test
    @DisplayName("a alteracao copia os dados e pode manter o proprio CNPJ")
    void alteracao() {
        Fornecedor atual = fornecedor(1L, CNPJ);
        when(fornecedorRepository.findById(1L)).thenReturn(Optional.of(atual));
        when(fornecedorRepository.findByCnpj(CNPJ)).thenReturn(Optional.of(atual));
        when(fornecedorRepository.save(atual)).thenReturn(atual);
        Fornecedor dados = fornecedor(null, CNPJ);
        dados.setRazaoSocial("Razao Nova");
        dados.setPrazoMedioEntrega(5);

        Fornecedor salvo = fornecedorService.atualizar(1L, dados);

        assertThat(salvo.getRazaoSocial()).isEqualTo("Razao Nova");
        assertThat(salvo.getPrazoMedioEntrega()).isEqualTo(5);
    }

    @Test
    @DisplayName("fornecedor com entrada de mercadoria nao e excluido")
    void exclusaoComMovimentacao() {
        when(fornecedorRepository.findById(1L)).thenReturn(Optional.of(fornecedor(1L, CNPJ)));
        when(movimentacaoRepository.existsByFornecedorId(1L)).thenReturn(true);

        assertThatThrownBy(() -> fornecedorService.excluir(1L)).isInstanceOf(RegraNegocioException.class);
        verify(fornecedorRepository, never()).delete(any());
    }

    @Test
    @DisplayName("fornecedor com despesa vinculada nao e excluido")
    void exclusaoComDespesa() {
        when(fornecedorRepository.findById(1L)).thenReturn(Optional.of(fornecedor(1L, CNPJ)));
        when(movimentacaoRepository.existsByFornecedorId(1L)).thenReturn(false);
        when(despesaRepository.existsByFornecedorId(1L)).thenReturn(true);

        assertThatThrownBy(() -> fornecedorService.excluir(1L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Inative o cadastro");
        verify(fornecedorRepository, never()).delete(any());
    }

    @Test
    @DisplayName("fornecedor sem lancamentos e excluido e auditado")
    void exclui() {
        Fornecedor existente = fornecedor(1L, CNPJ);
        when(fornecedorRepository.findById(1L)).thenReturn(Optional.of(existente));

        fornecedorService.excluir(1L);

        verify(fornecedorRepository).delete(existente);
        verify(auditoriaService).registrar(eq(AcaoAuditoria.EXCLUSAO), eq("Fornecedor"), eq(1L), anyString());
    }

    @Test
    @DisplayName("fornecedor inexistente e recusado")
    void inexistente() {
        when(fornecedorRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fornecedorService.inativar(9L))
                .isInstanceOf(RegistroNaoEncontradoException.class);
    }
}
