package com.rivoo.salon.domain.port.in;

import com.rivoo.salon.application.dto.SalonResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListSalonsUseCase {

    Page<SalonResponse> listAll(Pageable pageable);
}
