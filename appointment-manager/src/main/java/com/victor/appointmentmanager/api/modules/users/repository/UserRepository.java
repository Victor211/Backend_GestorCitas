package com.victor.appointmentmanager.api.modules.users.repository;

import com.victor.appointmentmanager.api.modules.users.entity.User;
import com.victor.appointmentmanager.api.modules.users.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u JOIN FETCH u.business WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("SELECT u FROM User u JOIN FETCH u.business WHERE u.id = :id AND u.active = true")
    Optional<User> findByIdAndActiveTrue(@Param("id") Long id);

    @Query("SELECT u FROM User u WHERE u.id = :id AND u.business.id = :businessId")
    Optional<User> findByIdAndBusinessId(@Param("id") Long id, @Param("businessId") Long businessId);

    @Query(value = "SELECT u FROM User u WHERE u.business.id = :businessId",
            countQuery = "SELECT COUNT(u) FROM User u WHERE u.business.id = :businessId")
    Page<User> findAllByBusinessId(@Param("businessId") Long businessId, Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u WHERE u.business.id = :businessId AND u.role = :role AND u.active = true")
    long countActiveByBusinessIdAndRole(@Param("businessId") Long businessId, @Param("role") UserRole role);

}
