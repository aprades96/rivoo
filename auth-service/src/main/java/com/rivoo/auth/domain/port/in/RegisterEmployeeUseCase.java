package com.rivoo.auth.domain.port.in;

import com.rivoo.auth.application.dto.RegisterEmployeeRequest;
import com.rivoo.auth.application.dto.RegisterEmployeeResponse;

public interface RegisterEmployeeUseCase {
    RegisterEmployeeResponse registerEmployee(RegisterEmployeeRequest request);
}
