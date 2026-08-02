package com.victor.appointmentmanager.api.modules.appointments.specification;

import com.victor.appointmentmanager.api.modules.appointments.entity.Appointment;
import com.victor.appointmentmanager.api.modules.appointments.enums.AppointmentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public final class AppointmentSpecifications {

    private AppointmentSpecifications() {
    }

    public static Specification<Appointment> belongsToBusiness(Long businessId) {
        return (root, query, cb) -> cb.equal(root.get("business").get("id"), businessId);
    }

    public static Specification<Appointment> hasEmployee(Long employeeId) {
        return (root, query, cb) -> cb.equal(root.get("employee").get("id"), employeeId);
    }

    public static Specification<Appointment> hasCustomer(Long customerId) {
        return (root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId);
    }

    public static Specification<Appointment> hasStatus(AppointmentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Appointment> startAtGreaterThanOrEqualTo(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startAt"), from);
    }

    public static Specification<Appointment> startAtLessThanOrEqualTo(Instant to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("startAt"), to);
    }

    /**
     * Combines the filters above, adding a predicate only when its value is non-null.
     * businessId is always required and never optional, enforcing multi-tenant isolation
     * at the query level.
     */
    public static Specification<Appointment> filterBy(Long businessId, Long employeeId, Long customerId,
                                                        AppointmentStatus status, Instant from, Instant to) {
        Specification<Appointment> specification = belongsToBusiness(businessId);

        if (employeeId != null) {
            specification = specification.and(hasEmployee(employeeId));
        }
        if (customerId != null) {
            specification = specification.and(hasCustomer(customerId));
        }
        if (status != null) {
            specification = specification.and(hasStatus(status));
        }
        if (from != null) {
            specification = specification.and(startAtGreaterThanOrEqualTo(from));
        }
        if (to != null) {
            specification = specification.and(startAtLessThanOrEqualTo(to));
        }

        return specification;
    }

}
