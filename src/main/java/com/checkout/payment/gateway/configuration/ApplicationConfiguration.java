package com.checkout.payment.gateway.configuration;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ApplicationConfiguration {

  @Bean
  public RestClient restClient(RestClient.Builder builder,
      BankClientProperties bankClientProperties) {

    var httpClient = HttpClient.newBuilder()
        .connectTimeout(bankClientProperties.connectionTimeout()).build();
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(bankClientProperties.readTimeout());

    return builder.baseUrl(bankClientProperties.baseUrl()).requestFactory(requestFactory).build();
  }
}
