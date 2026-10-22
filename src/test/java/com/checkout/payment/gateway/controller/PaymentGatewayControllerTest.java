package com.checkout.payment.gateway.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.external.BankClient;
import com.checkout.payment.gateway.external.dto.BankClientRequest;
import com.checkout.payment.gateway.external.dto.BankClientResponse;
import com.checkout.payment.gateway.model.PaymentEntity;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;
  @Autowired
  private PaymentsRepository paymentsRepository;
  @MockitoBean
  private BankClient bankClient;
  private static final Integer testYear = YearMonth.now().plusYears(2).getYear();

  static PaymentEntity createPaymentEntity(UUID paymentId) {
    var payment = new PaymentEntity();
    payment.setId(paymentId);
    payment.setAmount(10L);
    payment.setCurrency("USD");
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setExpiryMonth(12);
    payment.setExpiryYear(testYear);
    payment.setCardNumberLastFour("4321");
    return payment;
  }

  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {

    var payment = createPaymentEntity(UUID.randomUUID());
    paymentsRepository.add(payment);

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(payment.getStatus().getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value(payment.getCardNumberLastFour()))
        .andExpect(jsonPath("$.expiryMonth").value(payment.getExpiryMonth()))
        .andExpect(jsonPath("$.expiryYear").value(payment.getExpiryYear()))
        .andExpect(jsonPath("$.currency").value(payment.getCurrency()))
        .andExpect(jsonPath("$.amount").value(payment.getAmount()));
  }

  @Test
  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
    var paymentId = UUID.randomUUID();
    mvc.perform(MockMvcRequestBuilders.get("/payment/" + paymentId))
        .andExpect(status().isNotFound()).andExpect(
            jsonPath("$.message").value(String.format("No payment found for ID:%s", paymentId)));
  }

  @Test
  void whenPaymentIsAuthorizedReturn201() throws Exception {
    var bankTransactionId = UUID.randomUUID().toString();
    when(bankClient.authorizePayment(any(BankClientRequest.class))).thenReturn(
        new BankClientResponse(true, bankTransactionId));
    var requestContent = """
        {
          "card_number": "22243248876134",
          "expiry_month": 11,
          "expiry_year": %d,
          "currency": "GBP",
          "amount": 10,
          "cvv": "543"
        }
        """.formatted(testYear);

    mvc.perform(post("/payment").contentType(String.valueOf(MediaType.APPLICATION_JSON))
            .content(requestContent)).andExpect(status().isCreated())
        .andExpect(jsonPath("$.cardNumberLastFour").value("6134"))
        .andExpect(jsonPath("$.expiryMonth").value(11))
        .andExpect(jsonPath("$.expiryYear").value(testYear))
        .andExpect(jsonPath("$.amount").value(10)).andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.status").value("Authorized")).andExpect(jsonPath("id").exists())
        .andExpect(jsonPath("cvv").doesNotExist()).andExpect(jsonPath("cardNumber").doesNotExist());

    verify(bankClient, times(1)).authorizePayment(any(BankClientRequest.class));
  }

  @Test
  void whenPaymentIsNotAuthorizedReturn201() throws Exception {
    var bankTransactionId = UUID.randomUUID().toString();
    var testYear = YearMonth.now().plusYears(2).getYear();
    when(bankClient.authorizePayment(any(BankClientRequest.class))).thenReturn(
        new BankClientResponse(false, bankTransactionId));
    var requestContent = """
        {
          "card_number": "22243248876134",
          "expiry_month": 11,
          "expiry_year": %d,
          "currency": "GBP",
          "amount": 10,
          "cvv": "543"
        }
        """.formatted(testYear);
    mvc.perform(post("/payment").contentType(String.valueOf(MediaType.APPLICATION_JSON))
            .content(requestContent)).andExpect(status().isCreated())
        .andExpect(jsonPath("$.cardNumberLastFour").value("6134"))
        .andExpect(jsonPath("$.expiryMonth").value(11))
        .andExpect(jsonPath("$.expiryYear").value(testYear))
        .andExpect(jsonPath("$.amount").value(10)).andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.status").value("Declined")).andExpect(jsonPath("id").exists())
        .andExpect(jsonPath("cvv").doesNotExist()).andExpect(jsonPath("cardNumber").doesNotExist());

    verify(bankClient, times(1)).authorizePayment(any(BankClientRequest.class));
  }

  @Test
  void whenBankApiDoesNotRespondReturn503() throws Exception {
    when(bankClient.authorizePayment(any(BankClientRequest.class))).thenThrow(
        new BankUnavailableException("The bank service is down, try again later",
            new RuntimeException("HTTP 503")));
    var content = """
        {
          "card_number": "22243248876134",
          "expiry_month": 11,
          "expiry_year": %d,
          "currency": "GBP",
          "amount": 10,
          "cvv": "543"
        }
        """.formatted(testYear);

    mvc.perform(
            post("/payment").contentType(String.valueOf(MediaType.APPLICATION_JSON)).content(content))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message").value("The bank service is down, try again later"));

    verify(bankClient, times(1)).authorizePayment(any(BankClientRequest.class));
  }

  @Test
  void whenAmountIsMissingThenPaymentRejected() throws Exception {
    var content = """
        {
          "card_number": "22243248876134",
          "expiry_month": 11,
          "expiry_year": %d,
          "currency": "GBP",
          "cvv": "543"
        }
        """.formatted(testYear);
    mvc.perform(
            post("/payment").contentType(String.valueOf(MediaType.APPLICATION_JSON)).content(content))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.errors.amount").exists());

    verify(bankClient, times(0)).authorizePayment(any(BankClientRequest.class));
  }

  @Test
  void checkCorrectBankRequest() throws Exception {
    var bankTransactionId = UUID.randomUUID().toString();
    ArgumentCaptor<BankClientRequest> bakClientCaptor = ArgumentCaptor.forClass(
        BankClientRequest.class);
    when(bankClient.authorizePayment(any(BankClientRequest.class))).thenReturn(
        new BankClientResponse(false, bankTransactionId));

    mvc.perform(post("/payment").contentType(String.valueOf(MediaType.APPLICATION_JSON)).content("""
        {
          "card_number": "22243248876134",
          "expiry_month": 11,
          "expiry_year": %d,
          "currency": "GBP",
          "amount": 10,
          "cvv": "543"
        }
        """.formatted(testYear))).andExpect(status().isCreated());
    verify(bankClient).authorizePayment(bakClientCaptor.capture());
    var bankCLientRequest = bakClientCaptor.getValue();

    assertEquals("22243248876134", bankCLientRequest.cardNumber());
    assertEquals("11/%d".formatted(testYear), bankCLientRequest.expiryDate());
    assertEquals("GBP", bankCLientRequest.currency());
    assertEquals(10L, bankCLientRequest.amount());
    assertEquals("543", bankCLientRequest.cvv());
  }

  @Test
  void shouldRejectDecimalAmount() throws Exception {
    mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content("""
            {
              "card_number": "2222405343248877",
              "expiry_month": 12,
              "expiry_year": %d,
              "currency": "GBP",
              "amount": 10.5,
              "cvv": "123"
            }
            """.formatted(testYear))).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").value("Invalid payment request"))
        .andExpect(jsonPath("$.errors.request").exists());

    verifyNoInteractions(bankClient);

  }

  @Test
  void shouldRetrievePaymentAfterItIsCreated() throws Exception {
    when(bankClient.authorizePayment(any(BankClientRequest.class))).thenReturn(
        new BankClientResponse(true, "bank-reference-123"));

    String responseBody = mvc.perform(
            post("/payment").contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "card_number": "2222405343248877",
                  "expiry_month": 12,
                  "expiry_year": %d,
                  "currency": "GBP",
                  "amount": 1050,
                  "cvv": "123"
                }
                """.formatted(testYear))).andExpect(status().isCreated()).andReturn().getResponse()
        .getContentAsString();

    String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(responseBody).get("id")
        .asText();

    mvc.perform(get("/payment/{id}", id)).andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.amount").value(1050))
        .andExpect(jsonPath("$.cardNumberLastFour").value("8877"))
        .andExpect(jsonPath("$.cardNumber").doesNotExist())
        .andExpect(jsonPath("$.cvv").doesNotExist());
  }

  @Test
  void shouldRejectMalformedJsonWithoutCallingBank() throws Exception {
    mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content("{ invalid-json"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").value("Invalid payment request"))
        .andExpect(jsonPath("$.errors.request").exists());

    verifyNoInteractions(bankClient);
  }

  @Test
  void shouldRejectNonNumericAmountWithoutCallingBank() throws Exception {
    mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content("""
            {
              "card_number": "2222405343248877",
              "expiry_month": 12,
              "expiry_year": %d,
              "currency": "GBP",
              "amount": "not-a-number",
              "cvv": "123"
            }
            """.formatted(testYear))).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").value("Invalid payment request"))
        .andExpect(jsonPath("$.errors.request").exists());

    verifyNoInteractions(bankClient);
  }

}
