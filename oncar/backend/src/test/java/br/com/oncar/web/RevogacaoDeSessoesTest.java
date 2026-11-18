package br.com.oncar.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.oncar.TesteIntegracao;
import br.com.oncar.domain.enums.Perfil;
import br.com.oncar.domain.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Sessoes ja abertas precisam cair quando o acesso do usuario muda; caso contrario o
 * perfil e a situacao lidos no login continuariam valendo enquanto a aba estivesse aberta.
 */
@AutoConfigureMockMvc
class RevogacaoDeSessoesTest extends TesteIntegracao {

    private static final String SENHA = "oncar2026";

    @Autowired
    private MockMvc mockMvc;

    private Usuario gerente;

    @BeforeEach
    void prepararUsuarios() {
        gerente = criarGerente();
        criarProprietario();
    }

    @Test
    @DisplayName("usuario inativado perde a sessao aberta pela tela de login")
    void inativarDerrubaSessaoDoFormulario() throws Exception {
        MockHttpSession sessao = entrarPeloFormulario(EMAIL_GERENTE);
        mockMvc.perform(get("/").session(sessao)).andExpect(status().isOk());

        usuarioService.inativar(gerente.getId());

        mockMvc.perform(get("/").session(sessao))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?encerrada"));
    }

    @Test
    @DisplayName("usuario inativado perde a sessao aberta pela API e recebe 401 em JSON")
    void inativarDerrubaSessaoDaApi() throws Exception {
        MockHttpSession sessao = entrarPelaApi(EMAIL_GERENTE);
        mockMvc.perform(get("/api/painel").session(sessao)).andExpect(status().isOk());

        usuarioService.inativar(gerente.getId());

        mockMvc.perform(get("/api/painel").session(sessao))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value(containsString("encerrada")));
    }

    @Test
    @DisplayName("rebaixar o perfil derruba a sessao, e o novo login ja vem com o perfil novo")
    void mudarPerfilDerrubaSessao() throws Exception {
        MockHttpSession sessao = entrarPelaApi(EMAIL_GERENTE);
        // relatorios sao do proprietario e do gerente; o atendente nao os acessa
        mockMvc.perform(get("/api/relatorios/filtros").session(sessao)).andExpect(status().isOk());

        usuarioService.atualizar(gerente.getId(), dados("Gerente de Estoque", EMAIL_GERENTE, Perfil.ATENDENTE));

        mockMvc.perform(get("/api/relatorios/filtros").session(sessao)).andExpect(status().isUnauthorized());

        MockHttpSession novaSessao = entrarPelaApi(EMAIL_GERENTE);
        mockMvc.perform(get("/api/relatorios/filtros").session(novaSessao)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("trocar o e-mail derruba a sessao aberta com o e-mail antigo")
    void mudarEmailDerrubaSessao() throws Exception {
        MockHttpSession sessao = entrarPelaApi(EMAIL_GERENTE);

        usuarioService.atualizar(gerente.getId(),
                dados("Gerente de Estoque", "novo.gerente@teste.com.br", Perfil.GERENTE_ESTOQUE));

        mockMvc.perform(get("/api/sessao").session(sessao)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("alterar so o nome mantem a sessao aberta")
    void mudarSoONomeMantemSessao() throws Exception {
        MockHttpSession sessao = entrarPelaApi(EMAIL_GERENTE);

        usuarioService.atualizar(gerente.getId(), dados("Gerente Renomeado", EMAIL_GERENTE, Perfil.GERENTE_ESTOQUE));

        mockMvc.perform(get("/api/painel").session(sessao)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("redefinir a senha pelo proprietario derruba as sessoes do usuario")
    void redefinirSenhaDerrubaSessao() throws Exception {
        MockHttpSession sessao = entrarPelaApi(EMAIL_GERENTE);

        usuarioService.redefinirSenha(gerente.getId(), "novaSenha123");

        mockMvc.perform(get("/api/painel").session(sessao)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("excluir o usuario derruba as sessoes dele")
    void excluirDerrubaSessao() throws Exception {
        MockHttpSession sessao = entrarPelaApi(EMAIL_GERENTE);

        usuarioService.excluir(gerente.getId());

        mockMvc.perform(get("/api/painel").session(sessao)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("quem troca a propria senha continua conectado e derruba os outros dispositivos")
    void alterarAPropriaSenhaDerrubaSoOsOutrosDispositivos() throws Exception {
        MockHttpSession nesteNavegador = entrarPeloFormulario(EMAIL_GERENTE);
        MockHttpSession emOutroDispositivo = entrarPelaApi(EMAIL_GERENTE);

        mockMvc.perform(post("/conta/senha").session(nesteNavegador).with(csrf())
                        .param("senhaAtual", SENHA)
                        .param("novaSenha", "trocada2027")
                        .param("confirmacao", "trocada2027"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        mockMvc.perform(get("/").session(nesteNavegador)).andExpect(status().isOk());
        mockMvc.perform(get("/api/painel").session(emOutroDispositivo)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("encerrar as sessoes de um usuario nao afeta as dos outros")
    void naoAfetaOutrosUsuarios() throws Exception {
        MockHttpSession doProprietario = entrarPelaApi(EMAIL_PROPRIETARIO);

        usuarioService.inativar(gerente.getId());

        mockMvc.perform(get("/api/painel").session(doProprietario)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("cookie de sessao vencida na API recebe 401 em JSON, e nao redirecionamento")
    void sessaoVencidaNaApiRecebe401() throws Exception {
        mockMvc.perform(get("/api/painel").with(cookieDeSessaoVencida()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("cookie de sessao vencida nao impede um novo login pela API")
    void sessaoVencidaNaoImpedeNovoLogin() throws Exception {
        mockMvc.perform(post("/api/sessao").with(csrf()).with(cookieDeSessaoVencida())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credenciais(EMAIL_GERENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL_GERENTE));
    }

    @Test
    @DisplayName("nas telas, cookie de sessao vencida leva ao login com o aviso de expiracao")
    void sessaoVencidaNaTelaAvisaExpiracao() throws Exception {
        mockMvc.perform(get("/produtos").with(cookieDeSessaoVencida()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expirada"));
    }

    @Test
    @DisplayName("visitante sem cookie algum vai ao login sem aviso de expiracao")
    void visitanteSemCookieVaiAoLogin() throws Exception {
        mockMvc.perform(get("/produtos"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("a tela de login explica o encerramento da sessao")
    void telaDeLoginExplicaEncerramento() throws Exception {
        mockMvc.perform(get("/login").param("encerrada", ""))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("Sua sessao foi encerrada porque o seu acesso foi alterado")));
    }

    private MockHttpSession entrarPeloFormulario(String email) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
                        .param("email", email)
                        .param("senha", SENHA))
                .andExpect(redirectedUrl("/"))
                .andReturn().getRequest().getSession(false);
    }

    private MockHttpSession entrarPelaApi(String email) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/sessao").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credenciais(email)))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private static String credenciais(String email) {
        return "{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\"}";
    }

    private static Usuario dados(String nome, String email, Perfil perfil) {
        Usuario dados = new Usuario();
        dados.setNome(nome);
        dados.setEmail(email);
        dados.setPerfil(perfil);
        return dados;
    }

    /** Navegador que ainda envia o JSESSIONID de uma sessao que o servidor ja descartou. */
    private static RequestPostProcessor cookieDeSessaoVencida() {
        return request -> {
            request.setRequestedSessionId("sessao-que-venceu");
            request.setRequestedSessionIdValid(false);
            return request;
        };
    }
}
