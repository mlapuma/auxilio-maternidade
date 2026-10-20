package br.com.salario.maternidade.triagem;

import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TriagemRepository extends JpaRepository<Triagem, UUID> {
    List<Triagem> findByLeadIdOrderByCriadoEmDesc(UUID leadId);
}
