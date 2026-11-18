package br.com.oncar.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.oncar.domain.model.Fornecedor;
import br.com.oncar.service.FornecedorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.format.support.FormattingConversionService;

/** Conversao do campo de fornecedor dos formularios (id em texto <-> entidade). */
@ExtendWith(MockitoExtension.class)
class ConversoresConfigTest {

    @Mock
    private FornecedorService fornecedorService;

    private FormattingConversionService conversao;

    @BeforeEach
    void registrarConversores() {
        conversao = new FormattingConversionService();
        new ConversoresConfig(fornecedorService).addFormatters(conversao);
    }

    @Test
    @DisplayName("o id escolhido no formulario e carregado pelo servico")
    void idViraFornecedor() {
        Fornecedor fornecedor = new Fornecedor();
        when(fornecedorService.buscarPorId(7L)).thenReturn(fornecedor);

        assertThat(conversao.convert("7", Fornecedor.class)).isSameAs(fornecedor);
    }

    @Test
    @DisplayName("a opcao vazia do formulario vira fornecedor nenhum, sem consultar o servico")
    void opcaoVazia() {
        assertThat(conversao.convert("  ", Fornecedor.class)).isNull();
        verifyNoInteractions(fornecedorService);
    }

    @Test
    @DisplayName("o fornecedor vira o id em texto, e o ainda nao salvo vira texto vazio")
    void fornecedorViraTexto() {
        Fornecedor salvo = new Fornecedor();
        salvo.setId(12L);

        assertThat(conversao.convert(salvo, String.class)).isEqualTo("12");
        assertThat(conversao.convert(new Fornecedor(), String.class)).isEmpty();
    }
}
