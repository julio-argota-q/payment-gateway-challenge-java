package com.checkout.payment.gateway.exception;

public class BankInvalidResponseException extends RuntimeException {

  public BankInvalidResponseException(String message) {
    super(message);
  }

  public BankInvalidResponseException(String message, Throwable cause) {
    super(message, cause);
  }
}
