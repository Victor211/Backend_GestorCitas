package com.victor.appointmentmanager.api.modules.whatsapp.repository;

import com.victor.appointmentmanager.api.modules.whatsapp.entity.WhatsAppInboundEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WhatsAppInboundEventRepository extends JpaRepository<WhatsAppInboundEvent, Long> {

    Optional<WhatsAppInboundEvent> findByExternalMessageId(String externalMessageId);

}
