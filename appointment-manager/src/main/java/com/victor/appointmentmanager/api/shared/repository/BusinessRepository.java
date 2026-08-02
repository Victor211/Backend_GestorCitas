package com.victor.appointmentmanager.api.shared.repository;

import com.victor.appointmentmanager.api.shared.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessRepository extends JpaRepository<Business, Long> {

    Optional<Business> findByIdAndActiveTrue(Long id);

    Optional<Business> findByWhatsappPhoneNumberIdAndActiveTrue(String whatsappPhoneNumberId);

}
