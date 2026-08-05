package com.victor.appointmentmanager.api.modules.services.repository;

import com.victor.appointmentmanager.api.modules.services.entity.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ServiceRepository extends JpaRepository<Service, Long> {

    Optional<Service> findByIdAndBusinessIdAndActiveTrue(Long id, Long businessId);

    long countByBusinessIdAndActiveTrue(Long businessId);

    Page<Service> findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(Long businessId, String name,
                                                                            Pageable pageable);

    boolean existsByNameIgnoreCaseAndBusinessId(String name, Long businessId);

    List<Service> findAllByIdInAndBusinessIdAndActiveTrue(Set<Long> ids, Long businessId);

}
