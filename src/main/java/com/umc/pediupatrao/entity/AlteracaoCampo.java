package com.umc.pediupatrao.entity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Um campo modificado, com valor anterior e novo (imutável). */
public class AlteracaoCampo {

    private final String campo;
    private final String valorAnterior;
    private final String valorNovo;

    public AlteracaoCampo(String campo, String valorAnterior, String valorNovo) {
        this.campo = campo;
        this.valorAnterior = valorAnterior;
        this.valorNovo = valorNovo;
    }

    public String getCampo() {
        return campo;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public String getValorNovo() {
        return valorNovo;
    }

    /** Compara dois "retratos" (campo -> valor) e devolve apenas o que mudou. */
    public static List<AlteracaoCampo> diferencas(Map<String, String> antes, Map<String, String> depois) {
        Set<String> campos = new LinkedHashSet<>();
        campos.addAll(antes.keySet());
        campos.addAll(depois.keySet());
        List<AlteracaoCampo> resultado = new ArrayList<>();
        for (String campo : campos) {
            String a = antes.get(campo);
            String d = depois.get(campo);
            if (!Objects.equals(a, d)) {
                resultado.add(new AlteracaoCampo(campo, a, d));
            }
        }
        return resultado;
    }
}