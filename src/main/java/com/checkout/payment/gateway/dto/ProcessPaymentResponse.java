package com.checkout.payment.gateway.dto;

import com.checkout.payment.gateway.enums.PaymentStatus;
import java.util.UUID;

public record ProcessPaymentResponse(
    UUID id,
    PaymentStatus status,
    String currency,
    Long amount,
    Integer expiryMonth,
    Integer expiryYear,
    String cardNumberLastFour
) {}
