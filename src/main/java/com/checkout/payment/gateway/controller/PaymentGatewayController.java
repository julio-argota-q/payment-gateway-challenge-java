package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.dto.ProcessPaymentRequest;
import com.checkout.payment.gateway.dto.ProcessPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentGatewayController {

  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  @GetMapping("/payment/{id}")
  public ResponseEntity<ProcessPaymentResponse> getPostPaymentEventById(@PathVariable UUID id) {
    return new ResponseEntity<>(paymentGatewayService.getPaymentById(id), HttpStatus.OK);
  }

  @PostMapping("/payment")
  public ResponseEntity<ProcessPaymentResponse> createPaymentEvent(
      @Valid @RequestBody ProcessPaymentRequest paymentRequest) {
    return new ResponseEntity<>(paymentGatewayService.processPayment(paymentRequest),
        HttpStatus.CREATED);
  }
}
