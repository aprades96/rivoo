package com.rivoo.appointment.infrastructure.adapter.out.rest;

import com.rivoo.appointment.application.dto.EmployeeWorkingHoursDto;
import com.rivoo.appointment.domain.port.out.StaffServicePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Exercises {@link StaffServiceAdapter} against the exact JSON shapes staff-service actually
 * emits for its three internal endpoints, using {@link MockRestServiceServer} to run the real
 * Jackson 3 deserializer wired into the production {@code RestClient} — not a hand-built DTO,
 * which would never catch a field-naming drift between the two services.
 * <p>
 * The payloads below are taken field-for-field from staff-service's own response records:
 * {@code EmployeeInternalResponse}, {@code ServiceOfferingInternalResponse} and
 * {@code WorkingHoursResponse} (see {@code staff-service/.../application/dto/}).
 * <p>
 * The {@code getEmployeeWorkingHours} case is a regression test for the {@code isOpen}/{@code open}
 * incident: commit 9b8061b renamed the record component in {@code WorkingHoursResponse} from
 * {@code open} to {@code isOpen} to match the frontend contract, but this adapter's
 * {@code WorkingHoursInternalDto} was left declaring {@code open}, so Jackson 3's
 * {@code FAIL_ON_NULL_FOR_PRIMITIVES} (on by default, unlike Jackson 2) threw
 * {@code MismatchedInputException} on every call, turning
 * {@code GET /api/v1/appointments/public/availability} and
 * {@code POST /api/v1/appointments/book} into 500s. Every other test in this codebase mocks
 * {@link StaffServicePort} and builds the DTO by hand, so none of them exercised the real
 * deserialization path where the bug actually lived.
 */
class StaffServiceAdapterTest {

    private static final String STAFF_SERVICE_URL = "http://staff";

    private MockRestServiceServer server;
    private StaffServiceAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new StaffServiceAdapter(builder, STAFF_SERVICE_URL);
    }

    @Test
    void getEmployee_deserializesTheExactPayloadEmittedByEmployeeInternalResponse() {
        String tenantId = "sal_A";
        String employeeId = "emp_1";
        server.expect(requestTo(STAFF_SERVICE_URL + "/api/internal/staff/sal_A/employees/emp_1"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"id":"emp_1","firstName":"Ana","lastName":"Lopez","email":"ana@example.com",
                         "phone":"600111222","role":"EMPLOYEE","active":true}
                        """, MediaType.APPLICATION_JSON));

        StaffServicePort.StaffEmployeeInfo result = adapter.getEmployee(tenantId, employeeId);

        server.verify();
        assertThat(result.externalId()).isEqualTo("emp_1");
        assertThat(result.firstName()).isEqualTo("Ana");
        assertThat(result.lastName()).isEqualTo("Lopez");
        assertThat(result.active()).isTrue();
    }

    @Test
    void getService_deserializesTheExactPayloadEmittedByServiceOfferingInternalResponse() {
        String tenantId = "sal_A";
        String serviceId = "svc_1";
        server.expect(requestTo(STAFF_SERVICE_URL + "/api/internal/staff/sal_A/services/svc_1"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"id":"svc_1","name":"Haircut","durationMinutes":30,"price":25.00,
                         "currency":"EUR","active":true}
                        """, MediaType.APPLICATION_JSON));

        StaffServicePort.StaffServiceInfo result = adapter.getService(tenantId, serviceId);

        server.verify();
        assertThat(result.externalId()).isEqualTo("svc_1");
        assertThat(result.name()).isEqualTo("Haircut");
        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(result.durationMinutes()).isEqualTo(30);
        assertThat(result.active()).isTrue();
    }

    @Test
    void getEmployeeWorkingHours_deserializesTheExactPayloadEmittedByWorkingHoursResponse() {
        // Regression test for the isOpen/open incident (commit 9b8061b): staff-service's
        // WorkingHoursResponse emits "isOpen", not "open". Before the fix in this same
        // commit, this exact payload made Jackson 3 throw MismatchedInputException
        // (FAIL_ON_NULL_FOR_PRIMITIVES) because WorkingHoursInternalDto still declared
        // a primitive boolean component named "open".
        String tenantId = "sal_A";
        String employeeId = "emp_1";
        server.expect(requestTo(STAFF_SERVICE_URL + "/api/internal/staff/sal_A/employees/emp_1/working-hours"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        [
                          {"dayOfWeek":1,"isOpen":true,"openTime":"09:00:00","closeTime":"18:00:00",
                           "breakStartTime":"13:00:00","breakEndTime":"14:00:00"},
                          {"dayOfWeek":7,"isOpen":false,"openTime":null,"closeTime":null,
                           "breakStartTime":null,"breakEndTime":null}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<EmployeeWorkingHoursDto> result = adapter.getEmployeeWorkingHours(tenantId, employeeId);

        server.verify();
        assertThat(result).hasSize(2);
        EmployeeWorkingHoursDto monday = result.get(0);
        assertThat(monday.dayOfWeek()).isEqualTo(1);
        assertThat(monday.open()).as("Monday must be read as open").isTrue();
        assertThat(monday.openTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(monday.closeTime()).isEqualTo(LocalTime.of(18, 0));
        EmployeeWorkingHoursDto sunday = result.get(1);
        assertThat(sunday.dayOfWeek()).isEqualTo(7);
        assertThat(sunday.open()).as("Sunday must be read as closed, not defaulted to false by chance").isFalse();
    }
}
