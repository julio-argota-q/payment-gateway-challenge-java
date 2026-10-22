package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import java.util.UUID;

public class PaymentEntity {

  private UUID id;
  private PaymentStatus status;
  private String currency;
  private Long amount;
  private Integer expiryMonth;
  private Integer expiryYear;
  private String cardNumberLastFour;
  private String bankTransactionId;

  public String getCardNumberLastFour() {
    return cardNumberLastFour;
  }

  public void setCardNumberLastFour(String cardNumberLastFour) {
    this.cardNumberLastFour = cardNumberLastFour;
  }

  public Integer getExpiryYear() {
    return expiryYear;
  }

  public void setExpiryYear(Integer expYear) {
    this.expiryYear = expYear;
  }

  public Integer getExpiryMonth() {
    return expiryMonth;
  }

  public void setExpiryMonth(Integer expMonth) {
    this.expiryMonth = expMonth;
  }

  public Long getAmount() {
    return amount;
  }

  public void setAmount(Long amount) {
    this.amount = amount;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public void setStatus(PaymentStatus status) {
    this.status = status;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getBankTransactionId() {
    return bankTransactionId;
  }

  public void setBankTransactionId(String bankTransactionId) {
    this.bankTransactionId = bankTransactionId;
  }
}
