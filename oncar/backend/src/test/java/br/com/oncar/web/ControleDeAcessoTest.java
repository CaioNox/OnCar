package br.com.oncar.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.oncar.TesteIntegracao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * RN007: as telas sao liberadas conforme o perfil do usuario autenticado e as
 * tentativas de acesso nao autorizado sao bloqueadas pelo servidor.
 */
@AutoConfigureMockMvc
class ControleDeAcessoTest extends TesteIntegracao {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("RF01: usuario nao autenticado e enviado para a tela de login")
    void visitanteEhRedirecionadoParaLogin() throws Exception {
        mockMvc.perform(get("/produtos"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "atendente@teste.com.br", roles = "ATENDENTE")
    @DisplayName("RN007: o atendente nao acessa o cadastro de fornecedores")
    void atendenteNaoAcessaFornecedores() throws Exception {
        mockMvc.perform(get("/fornecedores"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/acesso-negado"));
    }

    @Test
    @WithMockUser(username = "atendente@teste.com.br", roles = "ATENDENTE")
    @DisplayName("RN007: o atendente nao registra entrada de mercadoria")
    void atendenteNaoRegistraEntrada() throws Exception {
        mockMvc.perform(get("/movimentacoes/entrada"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/acesso-negado"));
    }

    @Test
    @WithMockUser(username = "mecanico@teste.com.br", roles = "MECANICO")
    @DisplayName("RN007: o mecanico apenas consulta, sem registrar saidas")
    void mecanicoApenasConsulta() throws Exception {
        mockMvc.perform(get("/produtos"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/movimentacoes/saida"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/acesso-negado"));
    }

    @Test
    @WithMockUser(username = "gerente@teste.com.br", roles = "GERENTE_ESTOQUE")
    @DisplayName("RN007: o gerente de estoque nao administra usuarios nem a auditoria")
    void gerenteNaoAdministraUsuarios() throws Exception {
        mockMvc.perform(get("/usuarios"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/acesso-negado"));
        mockMvc.perform(get("/auditoria"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/acesso-negado"));
    }

    @Test
    @WithMockUser(username = "proprietario@teste.com.br", roles = "PROPRIETARIO")
    @DisplayName("RN007: o proprietario tem acesso integral")
    void proprietarioAcessaTodasAsTelas() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/usuarios")).andExpect(status().isOk());
        mockMvc.perform(get("/auditoria")).andExpect(status().isOk());
        mockMvc.perform(get("/fornecedores")).andExpect(status().isOk());
        mockMvc.perform(get("/movimentacoes/entrada")).andExpect(status().isOk());
    }
}
