package br.com.salario.maternidade.atendimento;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AtendimentoRepository extends JpaRepository<Atendimento, UUID> {
    Optional<Atendimento> findByLeadId(UUID leadId);
    Optional<Atendimento> findByCodigoPublico(String codigoPublico);
}
