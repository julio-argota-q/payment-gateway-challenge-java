package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PaymentEntity;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentsRepository {

  private final Map<UUID, PaymentEntity> payments = new ConcurrentHashMap<>();

  public void add(PaymentEntity payment) {
    Objects.requireNonNull(payment);
    payments.put(payment.getId(), payment);
  }

  public Optional<PaymentEntity> get(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }

}
