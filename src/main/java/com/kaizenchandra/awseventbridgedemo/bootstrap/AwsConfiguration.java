package com.kaizenchandra.awseventbridgedemo.bootstrap;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.time.Duration;

import software.amazon.awssdk.services.eventbridge.*;
import software.amazon.awssdk.services.sqs.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;

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
