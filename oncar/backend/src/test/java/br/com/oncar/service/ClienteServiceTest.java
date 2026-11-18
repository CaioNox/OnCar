package br.com.oncar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.oncar.domain.enums.AcaoAuditoria;
import br.com.oncar.domain.model.Cliente;
import br.com.oncar.domain.model.Veiculo;
import br.com.oncar.repository.ClienteRepository;
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
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private MovimentacaoRepository movimentacaoRepository;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private ClienteService clienteService;

    private static Cliente cliente(Long id, String documento) {
        Cliente cliente = new Cliente();
        cliente.setId(id);
        cliente.setNome("Cliente " + documento);
        cliente.setDocumento(documento);
        return cliente;
    }

    private static Veiculo veiculo(String placa) {
        Veiculo veiculo = new Veiculo();
        veiculo.setPlaca(placa);
        return veiculo;
    }

    @Test
    @DisplayName("o cadastro descarta veiculos sem placa e liga os demais ao cliente")
    void cadastroDescartaVeiculosSemPlaca() {
        Cliente novo = cliente(null, "123.456.789-00");
        novo.getVeiculos().add(veiculo("ABC1D23"));
        novo.getVeiculos().add(veiculo(null));
        novo.getVeiculos().add(veiculo("   "));
        when(clienteRepository.findByDocumento("123.456.789-00")).thenReturn(Optional.empty());
        when(clienteRepository.save(novo)).thenReturn(novo);

        Cliente salvo = clienteService.cadastrar(novo);

        assertThat(salvo.getVeiculos()).singleElement().satisfies(veiculo -> {
            assertThat(veiculo.getPlaca()).isEqualTo("ABC1D23");
            assertThat(veiculo.getCliente()).isSameAs(salvo);
        });
        verify(auditoriaService).registrar(eq(AcaoAuditoria.INCLUSAO), eq("Cliente"), any(), anyString());
    }

    @Test
    @DisplayName("documento ja cadastrado para outro cliente e recusado")
    void documentoRepetido() {
        when(clienteRepository.findByDocumento("111")).thenReturn(Optional.of(cliente(2L, "111")));

        assertThatThrownBy(() -> clienteService.cadastrar(cliente(null, "111")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("ja esta cadastrado para o cliente Cliente 111");
        verify(clienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("a alteracao pode manter o proprio documento e substitui a lista de veiculos")
    void alteracaoSubstituiVeiculos() {
        Cliente atual = cliente(2L, "111");
        atual.adicionarVeiculo(veiculo("OLD0A00"));
        when(clienteRepository.findById(2L)).thenReturn(Optional.of(atual));
        when(clienteRepository.findByDocumento("111")).thenReturn(Optional.of(atual));
        when(clienteRepository.save(atual)).thenReturn(atual);
        Cliente dados = cliente(null, "111");
        dados.setNome("Nome Corrigido");
        dados.getVeiculos().add(veiculo("NEW1B11"));
        dados.getVeiculos().add(veiculo(""));

        Cliente salvo = clienteService.atualizar(2L, dados);

        assertThat(salvo.getNome()).isEqualTo("Nome Corrigido");
        assertThat(salvo.getVeiculos()).extracting(Veiculo::getPlaca).containsExactly("NEW1B11");
    }

    @Test
    @DisplayName("cliente com movimentacao nao e excluido")
    void exclusaoComMovimentacao() {
        when(clienteRepository.findById(2L)).thenReturn(Optional.of(cliente(2L, "111")));
        when(movimentacaoRepository.existsByClienteId(2L)).thenReturn(true);

        assertThatThrownBy(() -> clienteService.excluir(2L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Inative o cadastro");
        verify(clienteRepository, never()).delete(any());
    }

    @Test
    @DisplayName("cliente sem movimentacao e excluido e auditado")
    void exclusaoSemMovimentacao() {
        Cliente existente = cliente(2L, "111");
        when(clienteRepository.findById(2L)).thenReturn(Optional.of(existente));
        when(movimentacaoRepository.existsByClienteId(2L)).thenReturn(false);

        assertThatCode(() -> clienteService.excluir(2L)).doesNotThrowAnyException();

        verify(clienteRepository).delete(existente);
        verify(auditoriaService).registrar(eq(AcaoAuditoria.EXCLUSAO), eq("Cliente"), eq(2L), anyString());
    }

    @Test
    @DisplayName("inativar e reativar alternam a situacao")
    void inativaEReativa() {
        Cliente existente = cliente(2L, "111");
        when(clienteRepository.findById(2L)).thenReturn(Optional.of(existente));

        clienteService.inativar(2L);
        assertThat(existente.isAtivo()).isFalse();

        clienteService.reativar(2L);
        assertThat(existente.isAtivo()).isTrue();
    }

    @Test
    @DisplayName("cliente inexistente e recusado")
    void inexistente() {
        when(clienteRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clienteService.buscarPorId(9L))
                .isInstanceOf(RegistroNaoEncontradoException.class)
                .hasMessage("Cliente nao encontrado.");
    }
}
