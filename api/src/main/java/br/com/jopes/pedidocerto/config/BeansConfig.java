package br.com.jopes.pedidocerto.config;

import br.com.jopes.pedidocerto.adapter.out.chaos.InMemoryNetworkChaos;
import br.com.jopes.pedidocerto.application.port.out.NetworkChaos;
import br.com.jopes.pedidocerto.application.service.RelaySettings;
import br.com.jopes.pedidocerto.domain.integration.ChaosSettings;
import br.com.jopes.pedidocerto.domain.integration.RetryPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
class BeansConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RetryPolicy retryPolicy(AppProperties properties) {
        AppProperties.Relay relay = properties.relay();
        return new RetryPolicy(relay.maxAttempts(), relay.baseDelay(), relay.maxDelay());
    }

    @Bean
    RelaySettings relaySettings(AppProperties properties) {
        return new RelaySettings(properties.relay().batchSize(), properties.relay().lease());
    }

    @Bean
    NetworkChaos networkChaos(AppProperties properties) {
        AppProperties.Chaos chaos = properties.chaos();
        return new InMemoryNetworkChaos(new ChaosSettings(
                chaos.failureRate(), chaos.ackLossRate(), chaos.duplicateRate(), chaos.latencyMs()));
    }
}
