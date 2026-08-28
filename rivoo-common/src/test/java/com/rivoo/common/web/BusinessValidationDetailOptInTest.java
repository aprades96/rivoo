package com.rivoo.common.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rivoo.common.exception.BusinessValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link BusinessValidationException} is thrown from BOTH anonymous and authenticated paths, so it
 * is the one shared base class where the publish/don't-publish decision cannot be made once for
 * the class. This pins the three-way behaviour that makes a PER-SITE decision possible, end to end
 * through the real {@link GlobalExceptionHandler}:
 * <ol>
 *   <li>the plain constructor publishes nothing;</li>
 *   <li>{@link BusinessValidationException#clientSafe(String)} publishes the message;</li>
 *   <li>a SUBTYPE does not inherit an opt-in made by some other throw site.</li>
 * </ol>
 * (3) is the one that protects {@code AppointmentConflictException}, whose message names an
 * employee: the fixture below is built exactly like it, {@code super(message)} and no override,
 * and must come out generic no matter how many call sites elsewhere use the factory.
 * <p>
 * The fixture subtype lives in this package on purpose. {@code CommonExceptionDetailPolicyTest}
 * scans {@code com.rivoo.common.exception} recursively, including {@code target/test-classes}, so
 * declaring it there - or in any subpackage of it - would make it a phantom entry in that test's
 * pinned map.
 */
class BusinessValidationDetailOptInTest {

    private static final String OWNER_FACING_MESSAGE = "closeTime must be after openTime";
    private static final String LEAKY_MESSAGE = "Employee 'Ana Garcia' already has an appointment during 10:00-11:00";

    private MockMvc mockMvc;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BusinessValidationController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        logAppender = new ListAppender<>();
        logAppender.start();
        handlerLogger().addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        handlerLogger().detachAppender(logAppender);
    }

    private static Logger handlerLogger() {
        return (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    }

    @Test
    void plainConstructor_publishesTheGenericDetailAndKeepsTheMessageInTheLog() throws Exception {
        String body = mockMvc.perform(get("/business-validation/restrictive"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Business Validation Failed"))
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.GENERIC_DETAIL))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(LEAKY_MESSAGE);
        assertMessageReachedTheLog(LEAKY_MESSAGE);
    }

    @Test
    void clientSafeFactory_publishesTheMessageItself() throws Exception {
        mockMvc.perform(get("/business-validation/opted-in"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Business Validation Failed"))
                .andExpect(jsonPath("$.detail").value(OWNER_FACING_MESSAGE));

        // The opt-in publishes; it does not stop the diagnostic from being logged as well.
        assertMessageReachedTheLog(OWNER_FACING_MESSAGE);
    }

    @Test
    void subtype_doesNotInheritTheOptInMadeByOtherThrowSites() throws Exception {
        String body = mockMvc.perform(get("/business-validation/subtype"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.GENERIC_DETAIL))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .as("a subtype reaches the base class through super(message); if that path ever "
                        + "starts publishing, every existing subtype leaks at once")
                .doesNotContain("Ana Garcia");
        assertMessageReachedTheLog(LEAKY_MESSAGE);
    }

    private void assertMessageReachedTheLog(String expectedMessage) {
        assertThat(logAppender.list)
                .as("moving a message out of the response body must not delete it")
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isIn(Level.WARN, Level.ERROR);
                    assertThat(event.getKeyValuePairs())
                            .anySatisfy(pair -> assertThat(String.valueOf(pair.value)).contains(expectedMessage));
                    assertThat(event.getThrowableProxy()).isNotNull();
                });
    }

    /** Built like {@code AppointmentConflictException}: {@code super(message)}, no override. */
    private static class FixtureConflictException extends BusinessValidationException {
        FixtureConflictException(String message) {
            super(message);
        }
    }

    @RestController
    static class BusinessValidationController {

        @GetMapping("/business-validation/restrictive")
        void restrictive() {
            throw new BusinessValidationException(LEAKY_MESSAGE);
        }

        @GetMapping("/business-validation/opted-in")
        void optedIn() {
            throw BusinessValidationException.clientSafe(OWNER_FACING_MESSAGE);
        }

        @GetMapping("/business-validation/subtype")
        void subtype() {
            throw new FixtureConflictException(LEAKY_MESSAGE);
        }
    }
}
