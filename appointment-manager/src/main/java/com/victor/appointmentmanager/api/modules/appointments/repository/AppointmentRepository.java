package com.victor.appointmentmanager.api.modules.appointments.repository;

import com.victor.appointmentmanager.api.modules.appointments.entity.Appointment;
import com.victor.appointmentmanager.api.modules.appointments.enums.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long>,
        JpaSpecificationExecutor<Appointment> {

    Optional<Appointment> findByIdAndBusinessId(Long id, Long businessId);

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
