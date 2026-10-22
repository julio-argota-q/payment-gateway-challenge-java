package com.checkout.payment.gateway.external;

import com.checkout.payment.gateway.external.dto.BankClientRequest;
import com.checkout.payment.gateway.external.dto.BankClientResponse;

public interface BankClient {

  BankClientResponse authorizePayment(BankClientRequest bankClientRequest);
}
