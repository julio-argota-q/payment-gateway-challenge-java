package com.checkout.payment.gateway.configuration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.net.URI;
import java.time.Duration;
import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "bank.api")
public record BankClientProperties(@NotBlank String baseUrl,
                                   @NotNull @DurationMin(millis = 1) Duration connectionTimeout,
                                   @NotNull @DurationMin(millis = 1) Duration readTimeout) {

  @AssertTrue(message = "Bank API base URL must be a valid HTTP or HTTPS URL")
  public boolean isBaseUrlValid() {
    if (baseUrl == null || baseUrl.isBlank()) {
      return true; // @NotBlank handles this
    }
    try {
      URI uri = URI.create(baseUrl);

      return uri.isAbsolute() && Set.of("http", "https").contains(uri.getScheme().toLowerCase())
          && uri.getHost() != null && uri.getUserInfo() == null && uri.getQuery() == null
          && uri.getFragment() == null;

    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

}
