package com.checkout.payment.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.YearMonth;
import java.util.Objects;

public record ProcessPaymentRequest(
    @NotBlank @Pattern(regexp = "EUR|MXN|GBP", message = "currency must be EUR, MXN or GBP.") String currency,

    @NotNull @Min(1) @Max(12) @JsonProperty("expiry_month") Integer expiryMonth,

    @NotNull @Positive @Max(9999) @JsonProperty("expiry_year") Integer expiryYear,

    @NotNull @Positive Long amount,

    @NotBlank @Pattern(regexp = "^[0-9]{3,4}$", message = "CVV must be just digits, with length between 3 and 4.") String cvv,

    @NotBlank @Pattern(regexp = "^[0-9]{14,19}$", message = "Card number must be just digits, with length between 14 and 19.") @JsonProperty("card_number") String cardNumber) {

  public String getExpiryDate() {
    return String.format("%02d/%04d", expiryMonth(), expiryYear());
  }

  public String getCardNumberLastFour() {
    return cardNumber().substring(cardNumber().length() - 4);
  }

  @AssertTrue(message = "Card expiry date must be in the future")
  @JsonIgnore
  public boolean isExpiryDateValid() {
    if (Objects.isNull(expiryMonth()) || Objects.isNull(expiryYear()) || expiryMonth < 1
        || expiryMonth > 12 || expiryYear < 1) {
      return true;
    }
    return !YearMonth.of(expiryYear, expiryMonth).isBefore(YearMonth.now());
  }
}
