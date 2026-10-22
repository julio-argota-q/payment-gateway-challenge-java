package com.checkout.payment.gateway.external;

import com.checkout.payment.gateway.exception.BankInvalidResponseException;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.external.dto.BankClientRequest;
import com.checkout.payment.gateway.external.dto.BankClientResponse;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class BankClientImpl implements BankClient {

  private static final Logger LOG = LoggerFactory.getLogger(BankClientImpl.class);
  private final RestClient bankClient;

  public BankClientImpl(RestClient bankClient) {
    this.bankClient = bankClient;
  }

  @Override
  public BankClientResponse authorizePayment(BankClientRequest bankClientRequest) {
    try {

      var bankApiResponse = bankClient.post().uri("/payments")
          .contentType(MediaType.APPLICATION_JSON).body(bankClientRequest).retrieve()
          .body(BankClientResponse.class);

      if (Objects.isNull(bankApiResponse) || Objects.isNull(bankApiResponse.authorized())) {
        throw new BankInvalidResponseException(
            "Bank api response with a null or incompleted response.");
      }

      if (Boolean.TRUE.equals(bankApiResponse.authorized()) && (
          Objects.isNull(bankApiResponse.authorizationCode()) || bankApiResponse.authorizationCode()
              .isBlank())) {
        throw new BankInvalidResponseException(
            "Authorized bank response, does not provide authorization code.");
      }

      return bankApiResponse;
    } catch (HttpServerErrorException exception) {
      LOG.warn("Bank API returned HTTP {}", exception.getStatusCode().value());
      throw new BankUnavailableException("Bank service is temporarily unavailable", exception);
    } catch (ResourceAccessException exception) {
      LOG.warn("Communication failure with acquiring bank: {}",
          exception.getClass().getSimpleName());

      throw new BankUnavailableException("Unable to communicate with acquiring bank", exception);
    } catch (RestClientException ex) {
      LOG.warn("Unexpected error while communicating with acquiring bank: {}",
          ex.getClass().getSimpleName());
      throw new BankInvalidResponseException("Unexpected response from acquiring bank", ex);
    }
  }

}
