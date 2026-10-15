package br.com.salario.maternidade.lead;

import jakarta.validation.constraints.*;

public record CriarLeadRequest(
    @NotBlank @Size(max = 160) String nome,
    @NotBlank @Pattern(regexp = "^\\+?[0-9 ()-]{10,20}$") String telefone,
    @Email @Size(max = 254) String email,
    @Size(max = 80) String origem,
    @NotBlank @Size(max = 20) String avisoPrivacidadeVersao,
    @NotNull Boolean consentimentoContato
) {}
