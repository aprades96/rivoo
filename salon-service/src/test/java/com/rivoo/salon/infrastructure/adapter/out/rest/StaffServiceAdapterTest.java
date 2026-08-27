package com.rivoo.salon.infrastructure.adapter.out.rest;

import com.rivoo.salon.domain.port.out.StaffServicePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

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
    void getPublicEmployees_sendsExplicitTenantIdHeader() {
        String tenantId = "sal_A";
        server.expect(requestTo(STAFF_SERVICE_URL + "/api/internal/staff/sal_A/employees/public"))
                .andExpect(method(GET))
                .andExpect(header("X-Tenant-Id", tenantId))
                .andRespond(withSuccess("""
                        [
                          {"id":"emp_1","firstName":"Ana","lastName":"Lopez","jobTitle":"Stylist","serviceIds":["svc_1","svc_2"]}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<StaffServicePort.EmployeePublicInfo> result = adapter.getPublicEmployees(tenantId);

        server.verify();
        assertThat(result).hasSize(1);
        StaffServicePort.EmployeePublicInfo employee = result.get(0);
        assertThat(employee.id()).isEqualTo("emp_1");
        assertThat(employee.firstName()).isEqualTo("Ana");
        assertThat(employee.lastName()).isEqualTo("Lopez");
        assertThat(employee.jobTitle()).isEqualTo("Stylist");
        assertThat(employee.serviceIds()).containsExactly("svc_1", "svc_2");
    }

    @Test
    void getPublicServices_sendsExplicitTenantIdHeader() {
        String tenantId = "sal_A";
        server.expect(requestTo(STAFF_SERVICE_URL + "/api/internal/staff/sal_A/services/public"))
                .andExpect(method(GET))
                .andExpect(header("X-Tenant-Id", tenantId))
                .andRespond(withSuccess("""
                        [
                          {"id":"svc_1","name":"Haircut","description":"Basic haircut","durationMinutes":30,"price":25.00,"currency":"EUR"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<StaffServicePort.ServicePublicInfo> result = adapter.getPublicServices(tenantId);

        server.verify();
        assertThat(result).hasSize(1);
        StaffServicePort.ServicePublicInfo service = result.get(0);
        assertThat(service.id()).isEqualTo("svc_1");
        assertThat(service.name()).isEqualTo("Haircut");
        assertThat(service.description()).isEqualTo("Basic haircut");
        assertThat(service.durationMinutes()).isEqualTo(30);
        assertThat(service.price()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(service.currency()).isEqualTo("EUR");
    }

    @Test
    void getPublicEmployees_returnsEmptyListWhenStaffServiceIsDown() {
        String tenantId = "sal_A";
        server.expect(requestTo(STAFF_SERVICE_URL + "/api/internal/staff/sal_A/employees/public"))
                .andExpect(method(GET))
                .andRespond(withStatus(SERVICE_UNAVAILABLE));

        List<StaffServicePort.EmployeePublicInfo> result = adapter.getPublicEmployees(tenantId);

        assertThat(result).isEmpty();
    }

    @Test
    void getPublicServices_returnsEmptyListWhenStaffServiceIsDown() {
        String tenantId = "sal_A";
        server.expect(requestTo(STAFF_SERVICE_URL + "/api/internal/staff/sal_A/services/public"))
                .andExpect(method(GET))
                .andRespond(withStatus(SERVICE_UNAVAILABLE));

        List<StaffServicePort.ServicePublicInfo> result = adapter.getPublicServices(tenantId);

        assertThat(result).isEmpty();
    }
}
