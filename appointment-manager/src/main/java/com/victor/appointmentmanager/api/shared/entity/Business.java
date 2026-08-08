package com.victor.appointmentmanager.api.shared.entity;

import com.victor.appointmentmanager.api.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "businesses")
public class Business extends AuditableEntity {

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String name;

    @Size(max = 30)
    @Column(length = 30)
    private String phone;

    @Email
    @Column
    private String email;

    @Column
    private String address;

    @NotBlank
    @Column(nullable = false)
    private String timezone;

    @Column(unique = true)
    private String whatsappPhoneNumberId;

    @Column
    private String whatsappBusinessAccountId;

}
