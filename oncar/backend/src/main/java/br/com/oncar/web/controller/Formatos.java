package br.com.oncar.web.controller;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Formatacao em portugues usada pelas telas: `${@formatos.moeda(valor)}`. */
@Component("formatos")
public class Formatos {

    private static final Locale PORTUGUES = Locale.forLanguageTag("pt-BR");

    public String moeda(BigDecimal valor) {
        if (valor == null) {
            return "-";
        }
        return NumberFormat.getCurrencyInstance(PORTUGUES).format(valor);
    }

    /** Quantidades sem zeros sobrando: 5.000 vira "5" e 2.500 vira "2,5". */
    public String quantidade(BigDecimal valor) {
        if (valor == null) {
            return "-";
        }
        NumberFormat formato = NumberFormat.getNumberInstance(PORTUGUES);
        formato.setMaximumFractionDigits(3);
        return formato.format(valor);
    }

    /** Quantidade com sinal explicito, para variacoes de saldo. */
    public String variacao(BigDecimal valor) {
        if (valor == null || valor.signum() == 0) {
            return "0";
        }
        return (valor.signum() > 0 ? "+" : "-") + quantidade(valor.abs());
    }
}
