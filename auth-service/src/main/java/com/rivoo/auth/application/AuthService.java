package com.rivoo.auth.application;

import com.rivoo.auth.application.dto.*;
import com.rivoo.auth.domain.exception.KeycloakOperationException;
import com.rivoo.auth.domain.model.EventType;
import com.rivoo.auth.domain.model.OnboardingEvent;
import com.rivoo.auth.domain.model.TenantUserMapping;
import com.rivoo.auth.domain.model.UserRole;
import com.rivoo.auth.domain.port.in.*;
import com.rivoo.auth.domain.port.out.KeycloakAdminPort;
import com.rivoo.auth.domain.port.out.OnboardingEventPort;
import com.rivoo.auth.domain.port.out.TenantUserMappingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class AuthService implements RegisterOwnerUseCase, RegisterEmployeeUseCase,
        ManageTenantStatusUseCase, UpdateTenantAttributeUseCase, ListTenantUsersUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final KeycloakAdminPort keycloakAdminPort;
    private final OnboardingEventPort onboardingEventPort;
    private final TenantUserMappingPort tenantUserMappingPort;

    public AuthService(KeycloakAdminPort keycloakAdminPort,
                       OnboardingEventPort onboardingEventPort,
                       TenantUserMappingPort tenantUserMappingPort) {
        this.keycloakAdminPort = keycloakAdminPort;
        this.onboardingEventPort = onboardingEventPort;
        this.tenantUserMappingPort = tenantUserMappingPort;
    }

    @Override
    @Transactional
    public RegisterOwnerResponse registerOwner(RegisterOwnerRequest request) {
        log.info("Registering owner for tenant {} with email {}", request.tenantId(), request.email());

        String keycloakUserId = keycloakAdminPort.createUser(
                request.email(), request.password(), request.firstName(), request.lastName());

        try {
            String plan = request.subscriptionPlan() != null ? request.subscriptionPlan() : "FREE_TRIAL";
            keycloakAdminPort.setUserAttributes(keycloakUserId, Map.of(
                    "tenant_id", List.of(request.tenantId()),
                    "subscription_plan", List.of(plan),
                    "salon_name", List.of(request.salonName())
            ));

            keycloakAdminPort.assignRealmRole(keycloakUserId, UserRole.SALON_OWNER.toKeycloakRole());

            tenantUserMappingPort.save(new TenantUserMapping(
                    request.tenantId(), keycloakUserId, UserRole.SALON_OWNER));

            onboardingEventPort.save(new OnboardingEvent(
                    request.tenantId(), keycloakUserId, request.email(),
                    EventType.OWNER_CREATED, null));

            log.info("Owner registered successfully: keycloakUserId={}, tenant={}",
                    keycloakUserId, request.tenantId());

            return new RegisterOwnerResponse(keycloakUserId, request.email(), UserRole.SALON_OWNER.name());

        } catch (Exception e) {
            log.error("Owner registration failed after user creation, compensating: keycloakUserId={}",
                    keycloakUserId, e);
            compensateUserCreation(keycloakUserId);
            throw e;
        }
    }

    @Override
    @Transactional
    public RegisterEmployeeResponse registerEmployee(RegisterEmployeeRequest request) {
        log.info("Registering employee for tenant {} with email {}", request.tenantId(), request.email());

        String keycloakUserId = keycloakAdminPort.createUser(
                request.email(), request.password(), request.firstName(), request.lastName());

        try {
            keycloakAdminPort.setUserAttributes(keycloakUserId, Map.of(
                    "tenant_id", List.of(request.tenantId())
            ));

            keycloakAdminPort.assignRealmRole(keycloakUserId, UserRole.EMPLOYEE.toKeycloakRole());

            tenantUserMappingPort.save(new TenantUserMapping(
                    request.tenantId(), keycloakUserId, UserRole.EMPLOYEE));

            onboardingEventPort.save(new OnboardingEvent(
                    request.tenantId(), keycloakUserId, request.email(),
                    EventType.EMPLOYEE_CREATED, null));

            log.info("Employee registered successfully: keycloakUserId={}, tenant={}",
                    keycloakUserId, request.tenantId());

            return new RegisterEmployeeResponse(keycloakUserId, request.email(), UserRole.EMPLOYEE.name());

        } catch (Exception e) {
            log.error("Employee registration failed after user creation, compensating: keycloakUserId={}",
                    keycloakUserId, e);
            compensateUserCreation(keycloakUserId);
            throw e;
        }
    }

    @Override
    @Transactional
    public void disableTenant(String tenantId) {
        log.info("Disabling all users for tenant {}", tenantId);
        setTenantStatus(tenantId, false);
    }

    @Override
    @Transactional
    public void setTenantStatus(String tenantId, boolean enabled) {
        log.info("Setting tenant {} status to enabled={}", tenantId, enabled);

        List<String> userIds = keycloakAdminPort.searchUserIdsByAttribute("tenant_id", tenantId);

        for (String userId : userIds) {
            keycloakAdminPort.setUserEnabled(userId, enabled);
        }

        tenantUserMappingPort.updateActiveStatusByTenantId(tenantId, enabled);

        EventType eventType = enabled ? EventType.USER_ENABLED : EventType.USER_DISABLED;
        for (String userId : userIds) {
            onboardingEventPort.save(new OnboardingEvent(
                    tenantId, userId, "",
                    eventType, "{\"usersAffected\":%d}".formatted(userIds.size())));
        }

        log.info("Tenant {} status set to enabled={}, {} users affected", tenantId, enabled, userIds.size());
    }

    @Override
    public void updateTenantAttributes(String tenantId, UpdateAttributeRequest request) {
        log.info("Updating attributes for tenant {}: {}", tenantId, request.attributes().keySet());

        List<String> userIds = keycloakAdminPort.searchUserIdsByAttribute("tenant_id", tenantId);

        for (String userId : userIds) {
            for (var entry : request.attributes().entrySet()) {
                keycloakAdminPort.updateUserAttribute(userId, entry.getKey(), entry.getValue());
            }
        }

        log.info("Updated attributes for {} users in tenant {}", userIds.size(), tenantId);
    }

    @Override
    public List<TenantUserResponse> listTenantUsers(String tenantId) {
        log.debug("Listing users for tenant {}", tenantId);

        return tenantUserMappingPort.findByTenantId(tenantId).stream()
                .map(mapping -> new TenantUserResponse(
                        mapping.getKeycloakUserId(),
                        mapping.getTenantId(),
                        mapping.getRole().name(),
                        mapping.isActive()))
                .toList();
    }

    private void compensateUserCreation(String keycloakUserId) {
        try {
            keycloakAdminPort.deleteUser(keycloakUserId);
            log.info("Compensation: deleted Keycloak user {}", keycloakUserId);
        } catch (Exception compensationError) {
            log.error("Compensation FAILED: could not delete Keycloak user {}. Manual cleanup required.",
                    keycloakUserId, compensationError);
        }
    }
}
