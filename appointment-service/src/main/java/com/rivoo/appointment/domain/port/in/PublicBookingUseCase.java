package com.rivoo.appointment.domain.port.in;

import com.rivoo.appointment.application.dto.PublicBookingRequest;
import com.rivoo.appointment.application.dto.PublicBookingResponse;

public interface PublicBookingUseCase {

    PublicBookingResponse book(PublicBookingRequest request);
}
