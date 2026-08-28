package com.rivoo.salon.application;

import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonStatus;
import com.rivoo.salon.domain.port.in.GetSalonUseCase;
import com.rivoo.salon.domain.port.out.BusinessHoursPersistencePort;
import com.rivoo.salon.domain.port.out.NotificationServicePort;
import com.rivoo.salon.domain.port.out.SalonPersistencePort;
import com.rivoo.salon.domain.port.out.StaffServicePort;
import com.rivoo.salon.infrastructure.mapper.SalonDtoMapper;
import com.rivoo.salon.infrastructure.mapper.SalonDtoMapperImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves the actual root cause fix for the public booking pool-exhaustion
 * bug: that {@code SalonService.getPublicBySlug} does NOT hold a database
 * transaction open while it calls staff-service over HTTP.
 * <p>
 * This needs a real Spring transactional proxy around
 * {@link SalonPublicSnapshotLoader} — a plain Mockito unit test (as in
 * {@link SalonServicePublicAggregateTest}) cannot exercise
 * {@code @Transactional} at all, since that annotation is only enforced by
 * an AOP proxy that Mockito never creates. So this test wires a minimal real
 * Spring context: {@code @EnableTransactionManagement} + a resourceless,
 * no-op {@link PlatformTransactionManager} (no DataSource needed — we only
 * care about the transaction *boundary*, not persistence), with the ports
 * mocked underneath.
 * <p>
 * The mocked {@link SalonPersistencePort} asserts that
 * {@code TransactionSynchronizationManager.isActualTransactionActive()} is
 * {@code true} while the DB read happens — proving the harness genuinely
 * starts a transaction around the loader (so the later assertion isn't
 * vacuously true because no transaction ever started). The mocked
 * {@link StaffServicePort} then asserts it is {@code false} at the moment
 * each HTTP call would fire — proving the connection has already been
 * released by the time staff-service is called.
 */
@SpringJUnitConfig
@ContextConfiguration(classes = SalonServiceTransactionBoundaryTest.TxBoundaryConfig.class)
class SalonServiceTransactionBoundaryTest {

    private static final String SLUG = "salon-demo";
    private static final String TENANT_ID = "sal_demo";

    // Autowired by the port interface (not the concrete SalonService class):
    // SalonService also has other @Transactional methods (getBySlug, update...),
    // so Spring's default (non-CGLIB) auto-proxying wraps it in a JDK dynamic proxy
    // that implements GetSalonUseCase and friends, but is not assignable to the
    // concrete SalonService type.
    @Autowired
    private GetSalonUseCase salonService;

    @Test
    void getPublicBySlug_releasesTransactionBeforeCallingStaffService() {
        // The two staff-service mocks assert isActualTransactionActive() == false
        // from inside their answer; if the transaction were still open when they
        // are invoked (the pre-fix behavior) this call throws an AssertionError.
        assertThatCode(() -> salonService.getPublicBySlug(SLUG)).doesNotThrowAnyException();
    }

    @Configuration
    @EnableTransactionManagement
    static class TxBoundaryConfig {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new NoOpTransactionManager();
        }

        @Bean
        SalonPersistencePort salonPersistencePort() {
            SalonPersistencePort port = mock(SalonPersistencePort.class);
            when(port.findBySlug(SLUG)).thenAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                        .as("DB read must run inside an active transaction")
                        .isTrue();
                return Optional.of(activeSalon());
            });
            return port;
        }

        @Bean
        BusinessHoursPersistencePort businessHoursPersistencePort() {
            BusinessHoursPersistencePort port = mock(BusinessHoursPersistencePort.class);
            when(port.findBySalonId(anyLong())).thenAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                        .as("Business hours read must run inside an active transaction")
                        .isTrue();
                return List.<com.rivoo.salon.domain.model.SalonBusinessHours>of();
            });
            return port;
        }

        @Bean
        StaffServicePort staffServicePort() {
            StaffServicePort port = mock(StaffServicePort.class);
            when(port.getPublicServices(anyString())).thenAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                        .as("staff-service HTTP call must NOT run inside a transaction")
                        .isFalse();
                return Optional.of(List.<StaffServicePort.ServicePublicInfo>of());
            });
            when(port.getPublicEmployees(anyString())).thenAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                        .as("staff-service HTTP call must NOT run inside a transaction")
                        .isFalse();
                return Optional.of(List.<StaffServicePort.EmployeePublicInfo>of());
            });
            return port;
        }

        @Bean
        SalonDtoMapper salonDtoMapper() {
            return new SalonDtoMapperImpl();
        }

        @Bean
        SalonPublicSnapshotLoader salonPublicSnapshotLoader(SalonPersistencePort salonPersistencePort,
                                                              BusinessHoursPersistencePort businessHoursPersistencePort) {
            return new SalonPublicSnapshotLoader(salonPersistencePort, businessHoursPersistencePort);
        }

        @Bean
        SalonService salonService(SalonPersistencePort salonPersistencePort,
                                   BusinessHoursPersistencePort businessHoursPersistencePort,
                                   StaffServicePort staffServicePort,
                                   SalonDtoMapper salonDtoMapper,
                                   SalonPublicSnapshotLoader salonPublicSnapshotLoader) {
            // The welcome mail is irrelevant to this test's subject (the transaction boundary of
            // getPublicBySlug) and is never reached from it: an ACTIVE salon read by slug never
            // touches the publication path.
            return new SalonService(salonPersistencePort, businessHoursPersistencePort, staffServicePort,
                    salonDtoMapper, salonPublicSnapshotLoader, mock(NotificationServicePort.class));
        }

        private static Salon activeSalon() {
            return Salon.builder()
                    .id(1L)
                    .externalId(TENANT_ID)
                    .tenantId(TENANT_ID)
                    .name("Demo Salon")
                    .slug(SLUG)
                    .phone("+34600000000")
                    .status(SalonStatus.ACTIVE)
                    .build();
        }
    }

    /**
     * A {@link PlatformTransactionManager} that manages no real resource. It only
     * needs to make {@code TransactionSynchronizationManager.isActualTransactionActive()}
     * flip to {@code true} for the duration of the transaction, which
     * {@link AbstractPlatformTransactionManager} already does around calls to
     * {@link #doBegin}/{@link #doCommit}/{@link #doRollback} regardless of their
     * (empty) implementation.
     */
    static class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // no real resource to start
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // no real resource to commit
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // no real resource to roll back
        }
    }
}
