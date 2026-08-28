package com.rivoo.salon.application;

import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonStatus;
import com.rivoo.salon.domain.model.SubscriptionPlan;
import com.rivoo.salon.domain.port.in.UpdateSalonUseCase;
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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves {@code SalonService#completeOnboarding} keeps its compare-and-set write
 * ({@code markOnboardingCompleted}) and its re-read ({@code findByTenantId}) inside one shared
 * transaction, per that method's own javadoc: without it, the re-read could run in a transaction
 * that starts after a concurrent commit lands in the gap between the two calls, contradicting what
 * this same call just wrote - which the frontend would misread as a failed onboarding completion,
 * retaining the owner in the setup assistant. Removing {@code @Transactional} from that method
 * leaves the wider 92-test suite green, because every other test exercising
 * {@code completeOnboarding} ({@code SalonOnboardingCompletionTest}) runs against a hand-written
 * in-memory {@link SalonPersistencePort} that has no notion of a transaction at all.
 * <p>
 * Same shape and same justification as {@link SalonServiceTransactionBoundaryTest} (this method
 * calls nobody outside the database, so the reason THAT test guards against - a JDBC connection held
 * open across an outbound HTTP call - does not apply here; this one guards the opposite failure mode,
 * a transaction boundary that is missing where one is required): a minimal real Spring context
 * ({@code @EnableTransactionManagement} plus a resourceless {@link PlatformTransactionManager}, no
 * {@code DataSource} needed) around a genuinely mocked {@link SalonPersistencePort}, autowired
 * through the port interface ({@link UpdateSalonUseCase}, not the concrete class) for the same
 * reason: {@code SalonService} implements several interfaces, so Spring's default non-CGLIB
 * auto-proxying returns a JDK dynamic proxy assignable only to those interfaces.
 * <p>
 * <b>What actually gets checked, and why it is enough.</b> The mocked
 * {@link SalonPersistencePort#markOnboardingCompleted} and
 * {@link SalonPersistencePort#findByTenantId} both assert
 * {@code TransactionSynchronizationManager.isActualTransactionActive()} from inside their answer, and
 * additionally read back a marker this test's own {@link NoOpTransactionManager} binds in
 * {@code doBegin} and unbinds in {@code doCommit}/{@code doRollback} - so the second call also proves
 * it is observing the SAME transaction the first call ran in, not merely that some transaction was
 * active independently for each. This is not vacuous: the port is a plain Mockito mock, never wrapped
 * in its own transactional proxy, so the only thing that can make either assertion pass is
 * {@code @Transactional} on {@code completeOnboarding} itself. Remove that annotation and both mocked
 * calls run with no active transaction at all - {@code isActualTransactionActive()} is {@code false}
 * for both - and this test fails.
 */
@SpringJUnitConfig
@ContextConfiguration(classes = SalonServiceCompleteOnboardingTransactionBoundaryTest.TxBoundaryConfig.class)
class SalonServiceCompleteOnboardingTransactionBoundaryTest {

    private static final String TENANT_ID = "sal_complete_onboarding_boundary";

    // Autowired by the port interface, not the concrete SalonService class - see class javadoc.
    @Autowired
    private UpdateSalonUseCase salonService;

    @Test
    void completeOnboarding_writeAndRereadShareOneTransaction() {
        // The mocked markOnboardingCompleted/findByTenantId below throw AssertionError if either
        // runs without an active transaction, or if the re-read does not observe the same
        // transaction the write ran in; if either happens, this call throws.
        assertThatCode(() -> salonService.completeOnboarding(TENANT_ID)).doesNotThrowAnyException();
    }

    @Configuration
    @EnableTransactionManagement
    static class TxBoundaryConfig {

        @Bean
        NoOpTransactionManager transactionManager() {
            return new NoOpTransactionManager();
        }

        @Bean
        SalonPersistencePort salonPersistencePort(NoOpTransactionManager transactionManager) {
            SalonPersistencePort port = mock(SalonPersistencePort.class);
            when(port.markOnboardingCompleted(TENANT_ID)).thenAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                        .as("the compare-and-set write must run inside an active transaction")
                        .isTrue();
                transactionManager.recordMarkerSeenDuringWrite();
                return 1;
            });
            when(port.findByTenantId(TENANT_ID)).thenAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                        .as("the re-read must run inside an active transaction")
                        .isTrue();
                assertThat(transactionManager.currentMarkerMatchesTheOneSeenDuringWrite())
                        .as("the re-read must observe the SAME transaction the write ran in, not a "
                                + "new one that could have missed a concurrent commit in between")
                        .isTrue();
                return Optional.of(activeSalon());
            });
            return port;
        }

        @Bean
        BusinessHoursPersistencePort businessHoursPersistencePort() {
            return mock(BusinessHoursPersistencePort.class);
        }

        @Bean
        StaffServicePort staffServicePort() {
            return mock(StaffServicePort.class);
        }

        @Bean
        NotificationServicePort notificationServicePort() {
            // completeOnboarding never sends mail; a call here would be a bug, but nothing in this
            // test triggers one.
            return mock(NotificationServicePort.class);
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
                                   SalonPublicSnapshotLoader salonPublicSnapshotLoader,
                                   NotificationServicePort notificationServicePort) {
            return new SalonService(salonPersistencePort, businessHoursPersistencePort, staffServicePort,
                    salonDtoMapper, salonPublicSnapshotLoader, notificationServicePort);
        }

        private static Salon activeSalon() {
            return Salon.builder()
                    .id(1L)
                    .externalId(TENANT_ID)
                    .tenantId(TENANT_ID)
                    .name("Demo Salon")
                    .slug("salon-demo")
                    .phone("+34600000000")
                    .status(SalonStatus.ACTIVE)
                    .subscriptionPlan(SubscriptionPlan.FREE_TRIAL)
                    .build();
        }
    }

    /**
     * A {@link PlatformTransactionManager} that manages no real resource, extended (beyond the
     * shape already used by {@link SalonServiceTransactionBoundaryTest}) to bind an
     * incrementing marker in {@code doBegin} and unbind it in {@code doCommit}/{@code doRollback} -
     * a transaction-scoped resource, the same lifecycle a real {@code DataSourceTransactionManager}
     * gives its {@code Connection}. Two port calls reading back the SAME marker value is what
     * proves they ran inside the SAME transaction rather than two independent ones that each merely
     * happened to be active.
     */
    static class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        private static final Object MARKER_KEY = new Object();

        private final AtomicInteger transactionSequence = new AtomicInteger();
        private volatile Integer markerSeenDuringWrite;

        void recordMarkerSeenDuringWrite() {
            markerSeenDuringWrite = (Integer) TransactionSynchronizationManager.getResource(MARKER_KEY);
        }

        boolean currentMarkerMatchesTheOneSeenDuringWrite() {
            Integer current = (Integer) TransactionSynchronizationManager.getResource(MARKER_KEY);
            return markerSeenDuringWrite != null && markerSeenDuringWrite.equals(current);
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            TransactionSynchronizationManager.bindResource(MARKER_KEY, transactionSequence.incrementAndGet());
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            TransactionSynchronizationManager.unbindResource(MARKER_KEY);
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            TransactionSynchronizationManager.unbindResourceIfPossible(MARKER_KEY);
        }
    }
}
