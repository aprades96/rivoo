package com.rivoo.salon.infrastructure.adapter.in.web;

import com.rivoo.common.web.GlobalExceptionHandler;
import com.rivoo.salon.domain.exception.BillingServiceException;
import com.rivoo.salon.domain.port.in.GetSalonUseCase;
import com.rivoo.salon.domain.port.in.ListSalonsUseCase;
import com.rivoo.salon.domain.port.in.ManageBusinessHoursUseCase;
import com.rivoo.salon.domain.port.in.ManageSalonStatusUseCase;
import com.rivoo.salon.domain.port.in.RegisterSalonUseCase;
import com.rivoo.salon.domain.port.in.UpdateSalonUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for the defect fixed by making {@link BillingServiceException}
 * extend {@code RivooException}: before that change it extended a bare
 * {@code RuntimeException} with no {@code @ExceptionHandler} anywhere in the
 * monorepo (verified: {@code grep -rn "BillingServiceException"
 * salon-service/src} returned only its own declaration, its throw site in
 * {@code BillingServiceAdapter}, and the port method — zero handlers), so it
 * always fell through to {@link GlobalExceptionHandler}'s generic
 * {@code @ExceptionHandler(Exception.class)} catch-all: a deterministic 500
 * "An unexpected error occurred" on every billing-service outage during salon
 * registration, the same class of bug already fixed for
 * {@code SalonNotFoundException} and for {@code AuthServiceException}.
 * <p>
 * Once {@link BillingServiceException} extends {@code RivooException}, it is
 * matched by {@link GlobalExceptionHandler#handleRivooException(com.rivoo.common.exception.RivooException)}
 * (no dedicated handler needed in {@link SalonExceptionHandler}, so ordering
 * between the two advices is not the concern here — only the exception's own
 * declared {@code HttpStatus} is), giving a 502 that carries the real
 * "billing-service is down" signal instead of masking it as an internal
 * server error.
 */
class BillingServiceExceptionHandlingTest {

    private RegisterSalonUseCase registerSalonUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        registerSalonUseCase = mock(RegisterSalonUseCase.class);
        SalonController controller = new SalonController(
                registerSalonUseCase,
                mock(GetSalonUseCase.class),
                mock(UpdateSalonUseCase.class),
                mock(ManageBusinessHoursUseCase.class),
                mock(ManageSalonStatusUseCase.class),
                mock(ListSalonsUseCase.class));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new SalonExceptionHandler())
                .build();
    }

    @Test
    void register_billingServiceUnreachable_returns502NotAnInternalServerError() throws Exception {
        when(registerSalonUseCase.register(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new BillingServiceException(
                        "Failed to create subscription in billing-service for tenant: sal_new"));

        String requestBody = """
                {
                  "name": "Demo Salon",
                  "email": "owner@example.com",
                  "phone": "+34600000000",
                  "addressStreet": "Carrer Demo 1",
                  "addressPostalCode": "08001",
                  "ownerFirstName": "Ana",
                  "ownerLastName": "Lopez",
                  "ownerPassword": "supersecret"
                }
                """;

        mockMvc.perform(post("/api/v1/salons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.detail").value("Failed to create subscription in billing-service for tenant: sal_new"))
                .andExpect(jsonPath("$.title").value("Billing Service Error"))
                .andExpect(jsonPath("$.type").value("https://rivoo.com/errors/billing-service-error"));
    }
}
