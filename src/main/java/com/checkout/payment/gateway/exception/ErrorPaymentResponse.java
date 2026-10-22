package com.checkout.payment.gateway.exception;

import java.util.Map;

public record ErrorPaymentResponse(String status, String message, Map<String, String> errors) {}
