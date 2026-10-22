package com.checkout.payment.gateway.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BankClientRequest(@JsonProperty("card_number") String cardNumber,
                                @JsonProperty("expiry_date") String expiryDate, String currency,
                                Long amount, String cvv) {

}