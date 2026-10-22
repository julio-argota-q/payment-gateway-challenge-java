package com.checkout.payment.gateway.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BankClientResponse(Boolean authorized,
                                 @JsonProperty("authorization_code") String authorizationCode) {

}