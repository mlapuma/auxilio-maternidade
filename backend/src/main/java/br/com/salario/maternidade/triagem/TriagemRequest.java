package br.com.salario.maternidade.triagem;

import jakarta.validation.constraints.NotNull;

public record TriagemRequest(
    @NotNull Boolean houveNascimentoOuAdocao,
    @NotNull Boolean possuiVinculoOuContribuicao,
    @NotNull Boolean desejaAtendimento
) {}
