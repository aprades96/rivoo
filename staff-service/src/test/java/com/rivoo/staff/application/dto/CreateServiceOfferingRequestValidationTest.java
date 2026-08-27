package com.rivoo.staff.application.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the corrupted-currency bug: {@link CreateServiceOfferingRequest#currency()}
 * had no validation at all, so values like {@code ""}, {@code "EU"} or {@code null} explicit reached
 * the database and later crashed {@code Intl.NumberFormat} on the frontend (see
 * {@code rivoo-frontend/src/lib/utils/format.test.ts}). The {@code @Pattern(regexp = "^[A-Z]{3}$")}
 * added to the field closes the entry point on the backend side.
 */
class CreateServiceOfferingRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private CreateServiceOfferingRequest requestWithCurrency(String currency) {
        return new CreateServiceOfferingRequest("Corte caballero", "Corte y peinado", 30,
                new BigDecimal("15.00"), currency);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EU", "EURO", "eur", "€", "12E"})
    void rejectsInvalidCurrencyCodes(String invalidCurrency) {
        Set<ConstraintViolation<CreateServiceOfferingRequest>> violations =
                validator.validate(requestWithCurrency(invalidCurrency));

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("currency"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void acceptsNullOrEmptyCurrency(String currency) {
        // @Pattern (unlike @NotBlank) treats null as valid per Bean Validation spec;
        // an explicit empty string DOES violate the 3-letter pattern.
        Set<ConstraintViolation<CreateServiceOfferingRequest>> violations =
                validator.validate(requestWithCurrency(currency));

        boolean hasCurrencyViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("currency"));

        if (currency == null) {
            assertThat(hasCurrencyViolation).isFalse();
        } else {
            assertThat(hasCurrencyViolation).isTrue();
        }
    }

    @Test
    void acceptsValidUppercaseCurrencyCode() {
        Set<ConstraintViolation<CreateServiceOfferingRequest>> violations =
                validator.validate(requestWithCurrency("EUR"));

        assertThat(violations).isEmpty();
    }
}
