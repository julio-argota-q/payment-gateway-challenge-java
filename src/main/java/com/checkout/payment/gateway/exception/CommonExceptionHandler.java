package com.checkout.payment.gateway.exception;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorPaymentResponse> handleValidationException(
      MethodArgumentNotValidException validException) {
    Map<String, String> errors = validException.getBindingResult().getFieldErrors().stream()
        .collect(Collectors.toMap(FieldError::getField,
            error -> (Objects.isNull(error.getDefaultMessage())) ? "" : error.getDefaultMessage(),
            (first, second) -> first));

    var errorPaymentResponse = new ErrorPaymentResponse("Rejected", "Invalid payment request",
        errors);
    return ResponseEntity.badRequest().body(errorPaymentResponse);
  }

  @ExceptionHandler(BankUnavailableException.class)
  public ResponseEntity<BankUnavailableResponse> handleBankApiException(
      BankUnavailableException bankUnavailableException) {
    var errorBankApiResponse = new BankUnavailableResponse(bankUnavailableException.getMessage());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorBankApiResponse);
  }

  @ExceptionHandler(PaymentNotFoundException.class)
  public ResponseEntity<PaymentNotFoundResponse> handlePaymentNotFound(
      PaymentNotFoundException paymentNotFoundException) {
    LOG.debug("Payment not found with ID:{}", paymentNotFoundException.getMessage());
    var paymentNotFoundResponse = new PaymentNotFoundResponse(
        String.format("No payment found for ID:%s", paymentNotFoundException.getMessage()));
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(paymentNotFoundResponse);
  }

  @ExceptionHandler(BankInvalidResponseException.class)
  public ResponseEntity<BankInvalidApiResponse> handlePaymentNotFound(
      BankInvalidResponseException invalidResponseException) {
    LOG.warn("Invalid answer for the bank api:{}", invalidResponseException.getMessage());
    var bankInvalidApiResponse = new BankInvalidApiResponse(invalidResponseException.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(bankInvalidApiResponse);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorPaymentResponse> handleMalformedJson(
      HttpMessageNotReadableException exception) {
    LOG.warn("Invalid payment request: malformed JSON or invalid field type");
    var response = new ErrorPaymentResponse("Rejected", "Invalid payment request",
        Map.of("request", "Malformed JSON or invalid field type"));
    return ResponseEntity.badRequest().body(response);
  }
}
