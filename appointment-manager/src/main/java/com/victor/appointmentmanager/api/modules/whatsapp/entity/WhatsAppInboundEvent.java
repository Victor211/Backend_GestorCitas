package com.victor.appointmentmanager.api.modules.whatsapp.entity;

import com.victor.appointmentmanager.api.common.entity.BaseEntity;
import com.victor.appointmentmanager.api.modules.whatsapp.enums.WhatsAppEventProcessingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "whatsapp_inbound_events",
        indexes = {
                @Index(name = "idx_whatsapp_events_business_id", columnList = "business_id"),
                @Index(name = "idx_whatsapp_events_processing_status", columnList = "processing_status")
        }
)
public class WhatsAppInboundEvent extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String externalMessageId;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(nullable = false, length = 30)
    private String senderPhone;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WhatsAppEventProcessingStatus processingStatus;

    @Column(length = 500)
    private String errorMessage;

    private Instant processedAt;

}
