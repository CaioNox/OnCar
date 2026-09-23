package br.com.oncar.config;

import br.com.oncar.domain.enums.AcaoAuditoria;
import br.com.oncar.domain.enums.Perfil;
import br.com.oncar.service.AuditoriaService;
import br.com.oncar.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String PROPRIETARIO = Perfil.PROPRIETARIO.name();
    private static final String GERENTE = Perfil.GERENTE_ESTOQUE.name();
    private static final String ATENDENTE = Perfil.ATENDENTE.name();
    private static final AntPathRequestMatcher REQUISICOES_DA_API = new AntPathRequestMatcher("/api/**");

    @Value("${oncar.frontend.origens:http://localhost:3000}")
    private List<String> origensDoFrontend;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuracao)
            throws Exception {
        return configuracao.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           AuthenticationSuccessHandler successHandler,
                                           AuthenticationFailureHandler failureHandler,
                                           AccessDeniedHandler accessDeniedHandler,
                                           CorsConfigurationSource corsConfigurationSource) throws Exception {
        // O token do CSRF vai em cookie legivel pelo navegador para que o front-end
        // possa devolve-lo no cabecalho X-XSRF-TOKEN; os formularios Thymeleaf
        // continuam recebendo o campo oculto normalmente.
        CsrfTokenRequestAttributeHandler tratadorDeCsrf = new CsrfTokenRequestAttributeHandler();
        tratadorDeCsrf.setCsrfRequestAttributeName(null);

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(tratadorDeCsrf))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/js/**", "/webjars/**", "/h2-console/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/sessao").permitAll()
                        // RN007: as mesmas regras de perfil valem para as telas e para a API
                        .requestMatchers("/usuarios/**", "/auditoria/**", "/api/auditoria").hasRole(PROPRIETARIO)
                        .requestMatchers("/fornecedores/**").hasAnyRole(PROPRIETARIO, GERENTE)
                        .requestMatchers("/produtos/novo", "/produtos/*/editar", "/produtos/salvar",
                                "/produtos/*/inativar", "/produtos/*/reativar", "/produtos/*/excluir")
                        .hasAnyRole(PROPRIETARIO, GERENTE)
                        .requestMatchers("/movimentacoes/entrada/**", "/movimentacoes/ajuste/**",
                                "/movimentacoes/*/estorno").hasAnyRole(PROPRIETARIO, GERENTE)
                        .requestMatchers("/movimentacoes/saida/**").hasAnyRole(PROPRIETARIO, GERENTE, ATENDENTE)
                        .requestMatchers(HttpMethod.POST, "/api/movimentacoes/entrada",
                                "/api/movimentacoes/ajuste", "/api/movimentacoes/*/estorno")
                        .hasAnyRole(PROPRIETARIO, GERENTE)
                        .requestMatchers(HttpMethod.POST, "/api/movimentacoes/saida")
                        .hasAnyRole(PROPRIETARIO, GERENTE, ATENDENTE)
                        .requestMatchers("/clientes/**").hasAnyRole(PROPRIETARIO, GERENTE, ATENDENTE)
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        .passwordParameter("senha")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                // RN015: sessao expirada devolve o usuario ao login com o motivo do encerramento
                .sessionManagement(session -> session
                        .invalidSessionUrl("/login?expirada"))
                .exceptionHandling(handling -> handling
                        // A API responde 401; as telas continuam sendo levadas ao formulario de login
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), REQUISICOES_DA_API)
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"), AnyRequestMatcher.INSTANCE)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuracao = new CorsConfiguration();
        configuracao.setAllowedOrigins(origensDoFrontend);
        configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuracao.setAllowedHeaders(List.of("*"));
        configuracao.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/api/**", configuracao);
        return fonte;
    }

    @Bean
    public AuthenticationSuccessHandler successHandler(@Lazy UsuarioService usuarioService) {
        return (HttpServletRequest request, HttpServletResponse response, Authentication authentication) -> {
            usuarioService.registrarLoginComSucesso(authentication.getName());
            response.sendRedirect(request.getContextPath() + "/");
        };
    }

    @Bean
    public AuthenticationFailureHandler failureHandler(@Lazy UsuarioService usuarioService) {
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) -> {
            String email = request.getParameter("email");
            if (email != null && !email.isBlank() && !(exception instanceof LockedException)) {
                usuarioService.registrarFalhaDeLogin(email);
            }
            String parametro = exception instanceof LockedException ? "bloqueada" : "erro";
            response.sendRedirect(request.getContextPath() + "/login?" + parametro);
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler(@Lazy AuditoriaService auditoriaService) {
        return (HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) -> {
            auditoriaService.registrar(AcaoAuditoria.ACESSO_NEGADO, "Aplicacao", null,
                    "Acesso negado a " + request.getMethod() + " " + request.getRequestURI());
            if (REQUISICOES_DA_API.matches(request)) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(
                        "{\"mensagem\":\"O seu perfil de acesso nao permite esta operacao.\",\"detalhes\":[]}");
                return;
            }
            response.sendRedirect(request.getContextPath() + "/acesso-negado");
        };
    }
}
