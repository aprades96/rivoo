package com.rivoo.staff.infrastructure.adapter.out.persistence.repository;

import com.rivoo.staff.infrastructure.adapter.out.persistence.entity.EmployeeServiceId;
import com.rivoo.staff.infrastructure.adapter.out.persistence.entity.EmployeeServiceJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeServiceJpaRepository extends JpaRepository<EmployeeServiceJpaEntity, EmployeeServiceId> {

    List<EmployeeServiceJpaEntity> findByEmployeeId(Long employeeId);

    void deleteByEmployeeId(Long employeeId);
}
