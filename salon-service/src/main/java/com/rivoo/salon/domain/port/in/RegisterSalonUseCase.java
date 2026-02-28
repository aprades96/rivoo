package com.rivoo.salon.domain.port.in;

import com.rivoo.salon.application.dto.RegisterSalonRequest;
import com.rivoo.salon.application.dto.RegisterSalonResponse;

public interface RegisterSalonUseCase {

    RegisterSalonResponse register(RegisterSalonRequest request);
}
