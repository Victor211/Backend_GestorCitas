package com.victor.appointmentmanager.api.modules.appointments.repository;

import com.victor.appointmentmanager.api.modules.appointments.entity.Appointment;
import com.victor.appointmentmanager.api.modules.appointments.enums.AppointmentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long>,
        JpaSpecificationExecutor<Appointment> {

    Optional<Appointment> findByIdAndBusinessId(Long id, Long businessId);

    /** Lectura filtrada por dueño: una cita ajena (otro cliente o negocio) nunca se devuelve. */
    @EntityGraph(attributePaths = {"customer", "employee", "service"})
    Optional<Appointment> findByIdAndBusinessIdAndCustomerId(Long id, Long businessId, Long customerId);

    /**
     * Igual que la anterior pero con bloqueo de escritura sobre la fila, para que dos
     * cancelaciones/reprogramaciones concurrentes de la misma cita se serialicen y la segunda
     * revalide el estado ya modificado por la primera.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.id = :id AND a.business.id = :businessId AND a.customer.id = :customerId
            """)
    Optional<Appointment> findOwnedForUpdate(@Param("id") Long id,
                                              @Param("businessId") Long businessId,
                                              @Param("customerId") Long customerId);

    @EntityGraph(attributePaths = {"customer", "employee", "service"})
    List<Appointment> findByBusinessIdAndCustomerIdAndStatusInAndStartAtGreaterThanEqualOrderByStartAtAsc(
            Long businessId, Long customerId, List<AppointmentStatus> statuses, Instant startAt, Pageable pageable);

    long countByBusinessIdAndStartAtGreaterThanEqualAndStartAtLessThanAndStatusIn(
            Long businessId, Instant startOfDay, Instant endOfDay, List<AppointmentStatus> statuses);

    @EntityGraph(attributePaths = {"customer", "employee", "service"})
    List<Appointment> findByBusinessIdAndStartAtGreaterThanEqualAndStatusInOrderByStartAtAsc(
            Long businessId, Instant startAt, List<AppointmentStatus> statuses, Pageable pageable);

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
