package com.rivoo.staff.infrastructure.adapter.out.rest;

import com.rivoo.staff.domain.port.out.BillingServicePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BillingServiceStubAdapter implements BillingServicePort {

    @Override
    public int getMaxEmployees(String tenantId) {
        log.atDebug().log("BillingServiceStub: returning unlimited (-1)");
        return -1;
    }
}
