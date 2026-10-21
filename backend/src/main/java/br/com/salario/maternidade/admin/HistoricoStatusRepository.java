package br.com.salario.maternidade.admin;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoricoStatusRepository extends JpaRepository<HistoricoStatus, UUID> {
    List<HistoricoStatus> findByLeadIdOrderByAlteradoEmDesc(UUID leadId);
}
