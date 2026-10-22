package com.checkout.payment.gateway.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;


import com.checkout.payment.gateway.exception.BankInvalidResponseException;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.external.dto.BankClientRequest;
import com.checkout.payment.gateway.external.dto.BankClientResponse;
import java.io.IOException;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BankClientTest {

  private BankClientImpl bankClient;
  private MockRestServiceServer mockServer;
  private static final Integer testYear = YearMonth.now().plusYears(2).getYear();

  @BeforeEach
  void setUp() {

    RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8080");
    mockServer = MockRestServiceServer.bindTo(builder).build();
    bankClient = new BankClientImpl(builder.build());
  }

  private BankClientRequest createClientBankRequest() {
    return new BankClientRequest("2222405343248877", "12/%d".formatted(testYear), "GBP", 1050L, "123");
  }

  @Test
  void shouldHandleAuthorizedResponse() {

    mockServer.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(method(HttpMethod.POST)).andRespond(withSuccess("""
            {
                "authorized": true,
                "authorization_code": "bank-123"
            }
            """, MediaType.APPLICATION_JSON));

    BankClientRequest request = createClientBankRequest();
    BankClientResponse response = bankClient.authorizePayment(request);

    assertTrue(response.authorized());
    assertEquals("bank-123", response.authorizationCode());

    mockServer.verify();
  }

  @Test
  void shouldSendCorrectJsonToBank() {

    mockServer.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(method(HttpMethod.POST)).andExpect(content().json("""
            {
                "card_number": "2222405343248877",
                "expiry_date": "12/%d",
                "currency": "GBP",
                "amount": 1050,
                "cvv": "123"
            }
            """.formatted(testYear))).andRespond(withSuccess("""
            {
                "authorized": true,
                "authorization_code": "bank-123"
            }
            """, MediaType.APPLICATION_JSON));

    BankClientRequest request = createClientBankRequest();
    bankClient.authorizePayment(request);

    mockServer.verify();
  }

  @Test
  void shouldAcceptDeclinedResponseWithoutAuthorizationCode() {
    mockServer.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(method(HttpMethod.POST)).andRespond(withSuccess("""
            {
                "authorized": false
            }
            """, MediaType.APPLICATION_JSON));

    BankClientRequest request = createClientBankRequest();
    BankClientResponse response = bankClient.authorizePayment(request);

    assertFalse(response.authorized());
    assertNull(response.authorizationCode());

    mockServer.verify();
  }

  @ParameterizedTest
  @EnumSource(value = HttpStatus.class, names = {"INTERNAL_SERVER_ERROR", "BAD_GATEWAY",
      "SERVICE_UNAVAILABLE", "GATEWAY_TIMEOUT"})
  void shouldHandleBankServerErrors(HttpStatus status) {

    mockServer.expect(requestTo("http://localhost:8080/payments")).andRespond(withStatus(status));

    assertThrows(BankUnavailableException.class,
        () -> bankClient.authorizePayment(createClientBankRequest()));

    mockServer.verify();
  }


  @ParameterizedTest
  @ValueSource(strings = {"", "{}", "{\"authorized\": null}", "{\"authorized\": true}"})
  void shouldRejectIncompleteBankResponse(String responseBody) {

    mockServer.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

    BankClientRequest request = new BankClientRequest("2222405343248877", "12/2028", "GBP", 1050L,
        "123");

    assertThrows(BankInvalidResponseException.class, () -> bankClient.authorizePayment(request));

    mockServer.verify();
  }

  @Test
  void shouldRejectMalformedBankResponse() {

    mockServer.expect(requestTo("http://localhost:8080/payments"))
        .andRespond(withSuccess("{ invalid json", MediaType.APPLICATION_JSON));

    assertThrows(BankInvalidResponseException.class,
        () -> bankClient.authorizePayment(createClientBankRequest()));

    mockServer.verify();
  }

  @Test
  void shouldRejectBlankAuthorizationCode() {
    mockServer.expect(requestTo("http://localhost:8080/payments")).andRespond(withSuccess("""
        { "authorized": true, "authorization_code": "   " }
        """, MediaType.APPLICATION_JSON));

    assertThrows(BankInvalidResponseException.class,
        () -> bankClient.authorizePayment(createClientBankRequest()));
    mockServer.verify();
  }

  @Test
  void shouldTreatConnectionFailureAsBankUnavailable() {
    mockServer.expect(requestTo("http://localhost:8080/payments")).andRespond(request -> {
      throw new IOException("Connection timed out");
    });

    assertThrows(BankUnavailableException.class,
        () -> bankClient.authorizePayment(createClientBankRequest()));
    mockServer.verify();
  }

  @Test
  void shouldTranslateBankBadRequestIntoInvalidBankResponse() {
    mockServer.expect(requestTo("http://localhost:8080/payments"))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST));

    assertThrows(BankInvalidResponseException.class,
        () -> bankClient.authorizePayment(createClientBankRequest()));
    mockServer.verify();
  }

  @Test
  void shouldRejectNonBooleanAuthorizationValue() {
    mockServer.expect(requestTo("http://localhost:8080/payments")).andRespond(withSuccess("""
        { "authorized": "not-a-boolean", "authorization_code": "bank-123" }
        """, MediaType.APPLICATION_JSON));

    assertThrows(BankInvalidResponseException.class,
        () -> bankClient.authorizePayment(createClientBankRequest()));
    mockServer.verify();
  }
}
