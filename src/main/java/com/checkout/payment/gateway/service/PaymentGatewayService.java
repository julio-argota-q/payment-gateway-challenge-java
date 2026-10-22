package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.dto.ProcessPaymentRequest;
import com.checkout.payment.gateway.dto.ProcessPaymentResponse;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.external.BankClient;
import com.checkout.payment.gateway.mappers.PaymentProcessMapper;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepository paymentsRepository;
  private final PaymentProcessMapper paymentProcessMapper;
  private final BankClient bankClient;

  public PaymentGatewayService(PaymentsRepository paymentsRepository,
      PaymentProcessMapper paymentProcessMapper, BankClient bankClient) {
    this.paymentsRepository = paymentsRepository;
    this.paymentProcessMapper = paymentProcessMapper;
    this.bankClient = bankClient;
  }

  public ProcessPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to to payment with ID {}", id);
    var paymentEntity = paymentsRepository.get(id)
        .orElseThrow(() -> new PaymentNotFoundException(id.toString()));
    return paymentProcessMapper.fromEntityToResponse(paymentEntity);
  }

  public ProcessPaymentResponse processPayment(ProcessPaymentRequest paymentRequest) {
    LOG.debug("Request to process payment with currency:{} and amount:{}",
        paymentRequest.currency(), paymentRequest.amount());
    var bankResponse = bankClient.authorizePayment(
        paymentProcessMapper.fromModelRequestToBankRequest(paymentRequest));

    var paymentEntity = paymentProcessMapper.fromRequestToEntity(paymentRequest);
    paymentEntity.setId(UUID.randomUUID());

    if (bankResponse.authorized()) {
      paymentEntity.setStatus(PaymentStatus.AUTHORIZED);
      paymentEntity.setBankTransactionId(bankResponse.authorizationCode());
    } else {
      paymentEntity.setStatus(PaymentStatus.DECLINED);
    }
    paymentsRepository.add(paymentEntity);
    LOG.info("Payment processed with paymentId:{}, status:{}", paymentEntity.getId(),
        paymentEntity.getStatus());
    return paymentProcessMapper.fromEntityToResponse(paymentEntity);
  }
}
