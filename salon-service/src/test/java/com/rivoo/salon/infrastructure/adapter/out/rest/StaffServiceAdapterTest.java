package com.rivoo.salon.infrastructure.adapter.out.rest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rivoo.common.web.RivooHeaders;
import com.rivoo.salon.domain.port.out.StaffServicePort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

class StaffServiceAdapterTest {

    private static final String STAFF_SERVICE_URL = "http://staff";

    private MockRestServiceServer server;
    private StaffServiceAdapter adapter;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new StaffServiceAdapter(builder, STAFF_SERVICE_URL);

        logAppender = new ListAppender<>();
        logAppender.start();
        adapterLogger().addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        adapterLogger().detachAppender(logAppender);
    }

    private static Logger adapterLogger() {
        return (Logger) LoggerFactory.getLogger(StaffServiceAdapter.class);
    }

    @Test
    void getPublicEmployees_sendsExplicitTenantIdHeader() {
        String tenantId = "sal_A";
        server.expect(requestTo(STAFF_SERVICE_URL + "/api/internal/staff/sal_A/public/employees"))
                .andExpect(method(GET))
                .andExpect(header(RivooHeaders.TENANT_ID, tenantId))
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
        server.expect(requestTo(STAFF_SERVICE_URL + "/api/internal/staff/sal_A/public/services"))
                .andExpect(method(GET))
                .andExpect(header(RivooHeaders.TENANT_ID, tenantId))
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
        server.expect(requestTo(STAFF_SERVICE_URL + "/api/internal/staff/sal_A/public/employees"))
                .andExpect(method(GET))
                .andRespond(withStatus(SERVICE_UNAVAILABLE));

        List<StaffServicePort.EmployeePublicInfo> result = adapter.getPublicEmployees(tenantId);

        assertThat(result).isEmpty();
        assertThat(logAppender.list)
                .as("a 5xx from staff-service must degrade quietly (WARN), not raise an alert")
                .anySatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
        assertThat(logAppender.list).noneMatch(event -> event.getLevel() == Level.ERROR);
    }

    @Test
    void getPublicServices_returnsEmptyListWhenStaffServiceIsDown() {
        String tenantId = "sal_A";
        server.expect(requestTo(STAFF_SERVICE_URL + "/api/internal/staff/sal_A/public/services"))
                .andExpect(method(GET))
                .andRespond(withStatus(SERVICE_UNAVAILABLE));

        List<StaffServicePort.ServicePublicInfo> result = adapter.getPublicServices(tenantId);

        assertThat(result).isEmpty();
        assertThat(logAppender.list)
                .as("a 5xx from staff-service must degrade quietly (WARN), not raise an alert")
                .anySatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
        assertThat(logAppender.list).noneMatch(event -> event.getLevel() == Level.ERROR);
    }

    @Test
    void getPublicEmployees_returnsEmptyListButLogsErrorWhenStaffServiceRejectsWithClientError() {
        String tenantId = "sal_A";
        server.expect(requestTo(STAFF_SERVICE_URL + "/api/internal/staff/sal_A/public/employees"))
                .andExpect(method(GET))
                .andRespond(withStatus(FORBIDDEN));

        List<StaffServicePort.EmployeePublicInfo> result = adapter.getPublicEmployees(tenantId);

        assertThat(result)
                .as("the public page must not break even when a 4xx points at a misconfiguration on our side")
                .isEmpty();
        assertThat(logAppender.list)
                .as("a 4xx is our own misconfiguration (e.g. a rotated internal-service-key) and must page someone, not be silently degraded like a 5xx")
                .anySatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
        assertThat(logAppender.list).noneMatch(event -> event.getLevel() == Level.WARN);
    }

    @Test
    void getPublicServices_returnsEmptyListButLogsErrorWhenStaffServiceRejectsWithClientError() {
        String tenantId = "sal_A";
        server.expect(requestTo(STAFF_SERVICE_URL + "/api/internal/staff/sal_A/public/services"))
                .andExpect(method(GET))
                .andRespond(withStatus(FORBIDDEN));

        List<StaffServicePort.ServicePublicInfo> result = adapter.getPublicServices(tenantId);

        assertThat(result)
                .as("the public page must not break even when a 4xx points at a misconfiguration on our side")
                .isEmpty();
        assertThat(logAppender.list)
                .as("a 4xx is our own misconfiguration (e.g. a rotated internal-service-key) and must page someone, not be silently degraded like a 5xx")
                .anySatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
        assertThat(logAppender.list).noneMatch(event -> event.getLevel() == Level.WARN);
    }
}
