package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.dto.ProcessPaymentRequest;
import com.checkout.payment.gateway.dto.ProcessPaymentResponse;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.exception.BankInvalidResponseException;
import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.external.BankClient;
import com.checkout.payment.gateway.external.dto.BankClientRequest;
import com.checkout.payment.gateway.external.dto.BankClientResponse;
import com.checkout.payment.gateway.mappers.PaymentProcessMapper;
import com.checkout.payment.gateway.model.PaymentEntity;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.YearMonth;
import java.util.UUID;
import java.util.Optional;

public class PaymentGatewayServiceTest {

  private PaymentsRepository paymentsRepository;
  private BankClient bankClient;
  private PaymentGatewayService paymentGatewayService;
  private static final Integer testYear = YearMonth.now().plusYears(2).getYear();

  @BeforeEach
  void setUp() {
    paymentsRepository = mock(PaymentsRepository.class);
    bankClient = mock(BankClient.class);

    paymentGatewayService = new PaymentGatewayService(paymentsRepository,
        new PaymentProcessMapper(), bankClient);
  }

  private ProcessPaymentRequest createValidPaymentRequest() {
    return new ProcessPaymentRequest("GBP", 12, testYear, 200L, "435", "76896548976534");
  }

  @Test
  void shouldAuthorizeAndStorePayment() {

    // Arrange
    when(bankClient.authorizePayment(any(BankClientRequest.class))).thenReturn(
        new BankClientResponse(true, "bank-authorization-123"));

    // Act
    ProcessPaymentResponse response = paymentGatewayService.processPayment(
        createValidPaymentRequest());

    // Assert response
    assertNotNull(response.id());
    assertEquals(PaymentStatus.AUTHORIZED, response.status());
    assertEquals(200, response.amount());
    assertEquals("GBP", response.currency());
    assertEquals("6534", response.cardNumberLastFour());

    // Verify stored entity
    ArgumentCaptor<PaymentEntity> captor = ArgumentCaptor.forClass(PaymentEntity.class);

    verify(paymentsRepository).add(captor.capture());

    PaymentEntity saved = captor.getValue();

    assertEquals(response.id(), saved.getId());
    assertEquals(PaymentStatus.AUTHORIZED, saved.getStatus());
    assertEquals("bank-authorization-123", saved.getBankTransactionId());
    assertEquals("6534", saved.getCardNumberLastFour());

    verify(bankClient, times(1)).authorizePayment(any(BankClientRequest.class));
  }

  @Test
  void shouldDeclineAndStorePayment() {

    when(bankClient.authorizePayment(any(BankClientRequest.class))).thenReturn(
        new BankClientResponse(false, null));

    ProcessPaymentResponse response = paymentGatewayService.processPayment(
        createValidPaymentRequest());

    assertEquals(PaymentStatus.DECLINED, response.status());

    ArgumentCaptor<PaymentEntity> captor = ArgumentCaptor.forClass(PaymentEntity.class);

    verify(paymentsRepository).add(captor.capture());
    PaymentEntity saved = captor.getValue();
    assertEquals(PaymentStatus.DECLINED, saved.getStatus());
    assertEquals(response.id(), saved.getId());
    assertEquals("6534", saved.getCardNumberLastFour());
    assertNull(saved.getBankTransactionId());
  }

  @Test
  void shouldNotStorePaymentWhenBankIsUnavailable() {

    when(bankClient.authorizePayment(any(BankClientRequest.class))).thenThrow(
        new BankUnavailableException("Bank unavailable", new RuntimeException("HTTP 503")));

    assertThrows(BankUnavailableException.class,
        () -> paymentGatewayService.processPayment(createValidPaymentRequest()));

    verify(paymentsRepository, never()).add(any(PaymentEntity.class));
  }

  @Test
  void shouldConvertExpiryDateToBankFormat() {
    ProcessPaymentRequest request = createValidPaymentRequest();
    BankClientRequest bankRequest = new PaymentProcessMapper().fromModelRequestToBankRequest(
        request);

    assertEquals("12/%d".formatted(testYear), bankRequest.expiryDate());
    assertEquals("76896548976534", bankRequest.cardNumber());
    assertEquals(200L, bankRequest.amount());
  }

  @Test
  void shouldNotStorePaymentWhenBankReturnsInvalidResponse() {
    when(bankClient.authorizePayment(any(BankClientRequest.class))).thenThrow(
        new BankInvalidResponseException("Missing authorization status"));

    assertThrows(BankInvalidResponseException.class,
        () -> paymentGatewayService.processPayment(createValidPaymentRequest()));

    verify(paymentsRepository, never()).add(any(PaymentEntity.class));
  }

  @Test
  void shouldReturnStoredPaymentByInternalId() {
    UUID id = UUID.randomUUID();
    PaymentEntity stored = new PaymentEntity();
    stored.setId(id);
    stored.setStatus(PaymentStatus.DECLINED);
    stored.setCurrency("GBP");
    stored.setAmount(200L);
    stored.setExpiryMonth(12);
    stored.setExpiryYear(testYear);
    stored.setCardNumberLastFour("6534");
    when(paymentsRepository.get(id)).thenReturn(Optional.of(stored));

    ProcessPaymentResponse response = paymentGatewayService.getPaymentById(id);

    assertEquals(id, response.id());
    assertEquals(PaymentStatus.DECLINED, response.status());
    assertEquals("6534", response.cardNumberLastFour());
    verify(paymentsRepository).get(id);
  }

  @Test
  void shouldThrowPaymentNotFoundWhenIdIsUnknown() {
    UUID id = UUID.randomUUID();
    when(paymentsRepository.get(id)).thenReturn(Optional.empty());

    assertThrows(PaymentNotFoundException.class, () -> paymentGatewayService.getPaymentById(id));
  }

}
