package br.com.salario.maternidade.triagem;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import java.util.Map;

public record RegistrarTriagemRequest(@NotNull UUID leadId, @NotNull @Valid TriagemRequest respostas, Map<String, String> respostasDetalhadas) {}
