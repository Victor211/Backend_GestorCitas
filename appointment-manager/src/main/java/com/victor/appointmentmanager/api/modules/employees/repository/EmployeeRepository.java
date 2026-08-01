package com.victor.appointmentmanager.api.modules.employees.repository;

import com.victor.appointmentmanager.api.modules.employees.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByIdAndActiveTrue(Long id);

    Optional<Employee> findByIdAndBusinessIdAndActiveTrue(Long id, Long businessId);

    Page<Employee> findByFirstNameContainingIgnoreCaseAndActiveTrue(String firstName, Pageable pageable);

    Page<Employee> findByBusinessIdAndFirstNameContainingIgnoreCaseAndActiveTrue(Long businessId, String firstName,
                                                                                  Pageable pageable);

    boolean existsByPhone(String phone);

}
