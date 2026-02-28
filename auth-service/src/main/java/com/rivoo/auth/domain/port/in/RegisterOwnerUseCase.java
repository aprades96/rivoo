package com.rivoo.auth.domain.port.in;

import com.rivoo.auth.application.dto.RegisterOwnerRequest;
import com.rivoo.auth.application.dto.RegisterOwnerResponse;

public interface RegisterOwnerUseCase {
    RegisterOwnerResponse registerOwner(RegisterOwnerRequest request);
}
