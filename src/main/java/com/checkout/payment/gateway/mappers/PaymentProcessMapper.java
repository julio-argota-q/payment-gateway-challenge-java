package com.checkout.payment.gateway.mappers;

import com.checkout.payment.gateway.dto.ProcessPaymentRequest;
import com.checkout.payment.gateway.dto.ProcessPaymentResponse;
import com.checkout.payment.gateway.external.dto.BankClientRequest;
import com.checkout.payment.gateway.model.PaymentEntity;
import org.springframework.stereotype.Component;

@Component
public class PaymentProcessMapper {

  public ProcessPaymentResponse fromEntityToResponse(PaymentEntity paymentEntity) {
    return new ProcessPaymentResponse(paymentEntity.getId(), paymentEntity.getStatus(),
        paymentEntity.getCurrency(), paymentEntity.getAmount(), paymentEntity.getExpiryMonth(),
        paymentEntity.getExpiryYear(), paymentEntity.getCardNumberLastFour());
  }

  public PaymentEntity fromRequestToEntity(ProcessPaymentRequest paymentRequest) {
    var paymentEntity = new PaymentEntity();
    paymentEntity.setAmount(paymentRequest.amount());
    paymentEntity.setCardNumberLastFour(paymentRequest.getCardNumberLastFour());
    paymentEntity.setCurrency(paymentRequest.currency());
    paymentEntity.setExpiryMonth(paymentRequest.expiryMonth());
    paymentEntity.setExpiryYear(paymentRequest.expiryYear());
    return paymentEntity;
  }

  public BankClientRequest fromModelRequestToBankRequest(ProcessPaymentRequest paymentRequest) {
    return new BankClientRequest(paymentRequest.cardNumber(), paymentRequest.getExpiryDate(),
        paymentRequest.currency(), paymentRequest.amount(), paymentRequest.cvv());
  }
}
