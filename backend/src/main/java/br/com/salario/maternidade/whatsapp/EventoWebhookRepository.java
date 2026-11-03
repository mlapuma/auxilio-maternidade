package br.com.salario.maternidade.whatsapp;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoWebhookRepository extends JpaRepository<EventoWebhook, UUID> {
    boolean existsByIdentificadorExterno(String identificadorExterno);
}
