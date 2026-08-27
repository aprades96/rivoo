package com.rivoo.staff.application.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sibling of {@link CreateServiceOfferingRequestValidationTest} for the update DTO: same
 * corrupted-currency bug, same {@code @Pattern(regexp = "^[A-Z]{3}$")} fix.
 */
class UpdateServiceOfferingRequestValidationTest {

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

    private UpdateServiceOfferingRequest requestWithCurrency(String currency) {
        return new UpdateServiceOfferingRequest("Corte caballero", "Corte y peinado", 30,
                new BigDecimal("15.00"), currency, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EU", "EURO", "eur", "€", ""})
    void rejectsInvalidCurrencyCodes(String invalidCurrency) {
        Set<ConstraintViolation<UpdateServiceOfferingRequest>> violations =
                validator.validate(requestWithCurrency(invalidCurrency));

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("currency"));
    }

    @Test
    void acceptsNullCurrencyAsNoOpUpdate() {
        // In UpdateServiceOfferingRequest a null currency means "leave unchanged"
        // (see ServiceOfferingService#update), so @Pattern must not reject it.
        Set<ConstraintViolation<UpdateServiceOfferingRequest>> violations =
                validator.validate(requestWithCurrency(null));

        assertThat(violations).isEmpty();
    }

    @Test
    void acceptsValidUppercaseCurrencyCode() {
        Set<ConstraintViolation<UpdateServiceOfferingRequest>> violations =
                validator.validate(requestWithCurrency("USD"));

        assertThat(violations).isEmpty();
    }
}
