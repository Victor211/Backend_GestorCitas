package com.victor.appointmentmanager.api.modules.customers.repository;

import com.victor.appointmentmanager.api.modules.customers.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByIdAndBusinessIdAndActiveTrue(Long id, Long businessId);

    long countByBusinessIdAndActiveTrue(Long businessId);

    Optional<Customer> findByBusinessIdAndPhoneAndActiveTrue(Long businessId, String phone);

    boolean existsByBusinessIdAndPhone(Long businessId, String phone);

    @Query("""
            SELECT c FROM Customer c
            WHERE c.business.id = :businessId
              AND c.active = true
              AND (LOWER(c.firstName) LIKE LOWER(CONCAT('%', :name, '%'))
                   OR LOWER(c.lastName) LIKE LOWER(CONCAT('%', :name, '%')))
            """)
    Page<Customer> searchActiveByBusinessAndName(@Param("businessId") Long businessId,
                                                  @Param("name") String name,
                                                  Pageable pageable);

}
