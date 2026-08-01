package com.victor.appointmentmanager.api.modules.appointments.repository;

import com.victor.appointmentmanager.api.modules.appointments.entity.Appointment;
import com.victor.appointmentmanager.api.modules.appointments.enums.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByIdAndBusinessId(Long id, Long businessId);

    @Query("""
            SELECT a FROM Appointment a
            WHERE a.business.id = :businessId
              AND (:employeeId IS NULL OR a.employee.id = :employeeId)
              AND (:customerId IS NULL OR a.customer.id = :customerId)
              AND (:status IS NULL OR a.status = :status)
              AND (:from IS NULL OR a.startAt >= :from)
              AND (:to IS NULL OR a.startAt <= :to)
            """)
    Page<Appointment> search(@Param("businessId") Long businessId,
                              @Param("employeeId") Long employeeId,
                              @Param("customerId") Long customerId,
                              @Param("status") AppointmentStatus status,
                              @Param("from") Instant from,
                              @Param("to") Instant to,
                              Pageable pageable);

    @Query("""
            SELECT COUNT(a) > 0 FROM Appointment a
            WHERE a.employee.id = :employeeId
              AND a.status <> :cancelledStatus
              AND (:excludeId IS NULL OR a.id <> :excludeId)
              AND a.startAt < :endAt
              AND a.endAt > :startAt
            """)
    boolean existsOverlapping(@Param("employeeId") Long employeeId,
                               @Param("startAt") Instant startAt,
                               @Param("endAt") Instant endAt,
                               @Param("excludeId") Long excludeId,
                               @Param("cancelledStatus") AppointmentStatus cancelledStatus);

}
