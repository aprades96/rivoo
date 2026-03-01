package com.rivoo.staff.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "employee_services")
@IdClass(EmployeeServiceId.class)
public class EmployeeServiceJpaEntity {

    @Id
    @Column(name = "employee_id")
    private Long employeeId;

    @Id
    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "tenant_id", nullable = false, length = 44)
    private String tenantId;

    @Column(name = "custom_duration")
    private Integer customDuration;

    @Column(name = "custom_price", precision = 10, scale = 2)
    private BigDecimal customPrice;
}
