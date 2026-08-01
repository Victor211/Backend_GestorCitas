package com.victor.appointmentmanager.api.modules.services.repository;

import com.victor.appointmentmanager.api.modules.services.entity.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServiceRepository extends JpaRepository<Service, Long> {

    Optional<Service> findByIdAndActiveTrue(Long id);

    Optional<Service> findByIdAndBusinessIdAndActiveTrue(Long id, Long businessId);

    Page<Service> findByNameContainingIgnoreCaseAndActiveTrue(String name, Pageable pageable);

    Page<Service> findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(Long businessId, String name,
                                                                            Pageable pageable);

    boolean existsByNameIgnoreCase(String name);

}
