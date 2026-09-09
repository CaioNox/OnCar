package br.com.oncar.config;

import br.com.oncar.domain.model.Fornecedor;
import br.com.oncar.service.FornecedorService;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Converte o identificador enviado pelos formularios na entidade correspondente.
 */
@Configuration
public class ConversoresConfig implements WebMvcConfigurer {

    private final FornecedorService fornecedorService;

    public ConversoresConfig(FornecedorService fornecedorService) {
        this.fornecedorService = fornecedorService;
    }

    @Override
    public void addFormatters(@NonNull FormatterRegistry registro) {
        registro.addConverter(new Converter<String, Fornecedor>() {
            @Override
            public Fornecedor convert(@NonNull String id) {
                return id.isBlank() ? null : fornecedorService.buscarPorId(Long.valueOf(id));
            }
        });
        registro.addConverter(new Converter<Fornecedor, String>() {
            @Override
            public String convert(@NonNull Fornecedor fornecedor) {
                return fornecedor.getId() == null ? "" : String.valueOf(fornecedor.getId());
            }
        });
    }
}
