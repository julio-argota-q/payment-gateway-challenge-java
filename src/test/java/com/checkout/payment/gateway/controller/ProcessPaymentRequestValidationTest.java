package com.checkout.payment.gateway.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.checkout.payment.gateway.dto.ProcessPaymentRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.YearMonth;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProcessPaymentRequestValidationTest {

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

  private static ProcessPaymentRequest createValidRequest() {
    YearMonth expiry = YearMonth.now().plusYears(2);

    return new ProcessPaymentRequest("GBP", expiry.getMonthValue(), expiry.getYear(), 1050L, "123",
        "2222405343248877");
  }

  @ParameterizedTest(name = "{index} - {0}")
  @MethodSource("invalidPayments")
  void shouldRejectInvalidPayments(String scenario, ProcessPaymentRequest request,
      String expectedField) {

    Set<ConstraintViolation<ProcessPaymentRequest>> violations = validator.validate(request);

    assertTrue(
        violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(expectedField)),
        () -> "Expected violation for " + expectedField + " in scenario: " + scenario);
  }

  static Stream<Arguments> invalidPayments() {

    YearMonth expired = YearMonth.now().minusMonths(1);
    YearMonth future = YearMonth.now().plusYears(2);

    return Stream.of(Arguments.of("Missing amount",
        new ProcessPaymentRequest("GBP", future.getMonthValue(), future.getYear(), null, "123",
            "2222405343248877"), "amount"), Arguments.of("Negative amount",
        new ProcessPaymentRequest("GBP", future.getMonthValue(), future.getYear(), -100L, "123",
            "2222405343248877"), "amount"), Arguments.of("Invalid currency",
        new ProcessPaymentRequest("USD", future.getMonthValue(), future.getYear(), 1050L, "123",
            "2222405343248877"), "currency"), Arguments.of("Invalid CVV",
        new ProcessPaymentRequest("GBP", future.getMonthValue(), future.getYear(), 1050L, "12",
            "2222405343248877"), "cvv"), Arguments.of("Invalid card number",
        new ProcessPaymentRequest("GBP", future.getMonthValue(), future.getYear(), 1050L, "123",
            "123"), "cardNumber"), Arguments.of("Invalid expiry month",
        new ProcessPaymentRequest("GBP", 13, future.getYear(), 1050L, "123", "2222405343248877"),
        "expiryMonth"), Arguments.of("Expired card",
        new ProcessPaymentRequest("GBP", expired.getMonthValue(), expired.getYear(), 1050L, "123",
            "2222405343248877"), "expiryDateValid"), Arguments.of("Zero amount",
        new ProcessPaymentRequest("GBP", future.getMonthValue(), future.getYear(), 0L, "123",
            "2222405343248877"), "amount"), Arguments.of("Missing expiry month",
        new ProcessPaymentRequest("GBP", null, future.getYear(), 1050L, "123", "2222405343248877"),
        "expiryMonth"), Arguments.of("Missing expiry year",
        new ProcessPaymentRequest("GBP", future.getMonthValue(), null, 1050L, "123",
            "2222405343248877"), "expiryYear"), Arguments.of("Zero expiry month",
        new ProcessPaymentRequest("GBP", 0, future.getYear(), 1050L, "123", "2222405343248877"),
        "expiryMonth"), Arguments.of("Non-numeric CVV",
        new ProcessPaymentRequest("GBP", future.getMonthValue(), future.getYear(), 1050L, "12a",
            "2222405343248877"), "cvv"), Arguments.of("Non-numeric card number",
        new ProcessPaymentRequest("GBP", future.getMonthValue(), future.getYear(), 1050L, "123",
            "222240534324887a"), "cardNumber"), Arguments.of("Empty currency",
        new ProcessPaymentRequest("", future.getMonthValue(), future.getYear(), 1050L, "123",
            "2222405343248877"), "currency"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"GBP", "EUR", "MXN"})
  void shouldAcceptSupportedCurrencies(String currency) {

    YearMonth expiry = YearMonth.now().plusYears(2);

    ProcessPaymentRequest request = new ProcessPaymentRequest(currency, expiry.getMonthValue(),
        expiry.getYear(), 1050L, "123", "2222405343248877");

    assertTrue(validator.validate(request).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"123", "1234", "007", "0000"})
  void shouldAcceptValidCvvs(String cvv) {

    ProcessPaymentRequest base = createValidRequest();

    ProcessPaymentRequest request = new ProcessPaymentRequest(base.currency(), base.expiryMonth(),
        base.expiryYear(), base.amount(), cvv, base.cardNumber());

    assertTrue(validator.validate(request).isEmpty());
  }

  @Test
  void shouldAcceptCardExpiringCurrentMonth() {

    YearMonth current = YearMonth.now();

    ProcessPaymentRequest request = new ProcessPaymentRequest("GBP", current.getMonthValue(),
        current.getYear(), 1050L, "123", "2222405343248877");

    assertTrue(validator.validate(request).isEmpty());
  }

}
