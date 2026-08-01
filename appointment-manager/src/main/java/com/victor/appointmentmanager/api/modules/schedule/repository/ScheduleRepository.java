package com.victor.appointmentmanager.api.modules.schedule.repository;

import com.victor.appointmentmanager.api.modules.schedule.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    Optional<Schedule> findByIdAndActiveTrue(Long id);

    @Query("""
            SELECT s FROM Schedule s
            WHERE s.id = :id
              AND s.employee.business.id = :businessId
              AND s.active = true
            """)
    Optional<Schedule> findByIdAndBusinessIdAndActiveTrue(@Param("id") Long id, @Param("businessId") Long businessId);

    List<Schedule> findByEmployeeIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(Long employeeId);

    List<Schedule> findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(Long employeeId, DayOfWeek dayOfWeek);

    @Query("""
            SELECT s FROM Schedule s
            WHERE s.employee.business.id = :businessId
              AND s.active = true
              AND (:employeeId IS NULL OR s.employee.id = :employeeId)
              AND (:dayOfWeek IS NULL OR s.dayOfWeek = :dayOfWeek)
            ORDER BY s.dayOfWeek ASC, s.startTime ASC
            """)
    List<Schedule> findActiveByBusiness(@Param("businessId") Long businessId,
                                         @Param("employeeId") Long employeeId,
                                         @Param("dayOfWeek") DayOfWeek dayOfWeek);

    @Query("""
            SELECT COUNT(s) > 0 FROM Schedule s
            WHERE s.employee.id = :employeeId
              AND s.dayOfWeek = :dayOfWeek
              AND s.active = true
              AND (:excludeId IS NULL OR s.id <> :excludeId)
              AND s.startTime < :endTime
              AND s.endTime > :startTime
            """)
    boolean existsOverlapping(@Param("employeeId") Long employeeId,
                               @Param("dayOfWeek") DayOfWeek dayOfWeek,
                               @Param("startTime") LocalTime startTime,
                               @Param("endTime") LocalTime endTime,
                               @Param("excludeId") Long excludeId);

}
