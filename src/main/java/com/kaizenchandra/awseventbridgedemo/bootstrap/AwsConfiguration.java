package com.kaizenchandra.awseventbridgedemo.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;
import java.time.Duration;

@Configuration
public class AwsConfiguration {
    private ClientOverrideConfiguration config() {
        return ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(15)).apiCallAttemptTimeout(Duration.ofSeconds(8)).retryPolicy(RetryPolicy.builder().numRetries(2).build()).build();
    }

    @Bean(destroyMethod = "close")
    EventBridgeClient eventBridge(@Value("${aws.endpoint:}") String endpoint, @Value("${aws.region:us-east-1}") String region) {
        var b = EventBridgeClient.builder().region(Region.of(region)).overrideConfiguration(config());
        if (!endpoint.isBlank()) b.endpointOverride(URI.create(endpoint));
        return b.build();
    }

    @Bean(destroyMethod = "close")
    SqsClient sqs(@Value("${aws.endpoint:}") String endpoint, @Value("${aws.region:us-east-1}") String region) {
        var b = SqsClient.builder().region(Region.of(region)).overrideConfiguration(config());
        if (!endpoint.isBlank()) b.endpointOverride(URI.create(endpoint));
        return b.build();
    }
}
