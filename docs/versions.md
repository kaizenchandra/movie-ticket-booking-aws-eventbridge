# Verified compatibility and sources

Checked 2026-09-14 against official sources and the resolved Maven build.

| Component | Pin / source |
|---|---|
| Java | 21; Docker uses Eclipse Temurin 21; executed with installed Oracle Java 21.0.12.1 |
| Spring Boot | 4.1.1 retained from starter, [official stable release](https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now/) |
| Java compatibility | [Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html); Java 21 is in the supported range |
| JPA, Hibernate, Spring Security, Reactor, Flyway, PostgreSQL JDBC, Testcontainers | Pinned transitively by Boot 4.1.1 BOM; [official managed coordinates](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html); inspect `./mvnw dependency:tree` |
| AWS SDK v2 | 2.31.54 BOM; [official release](https://github.com/aws/aws-sdk-java-v2/releases/tag/2.31.54); EventBridge and SQS use the same BOM |
| ArchUnit | 1.4.1; [official release](https://github.com/TNG/ArchUnit/releases/tag/v1.4.1) |
| LocalStack | 2026.04.0; [EventBridge service documentation](https://docs.localstack.cloud/aws/services/events/) describes this provider version and SQS targets |
| PostgreSQL | 17.6 Docker/test image; verified by the running migration and persistence suite |

LocalStack [plans](https://docs.localstack.cloud/aws/licensing/) list EventBridge and SQS. [Authentication documentation](https://docs.localstack.cloud/aws/getting-started/auth-token/) requires a token and an assigned license; [delivery change announcement](https://blog.localstack.cloud/the-road-ahead-for-localstack/) explains mandatory authentication in current unified images. Obtain a commercial, trial, or eligible noncommercial license under the terms applicable to your use; no free entitlement is assumed. CI requires the appropriate token too. Persistence/advanced features may depend on plan. No license was provisioned by this task.

AWS semantics: [PutEvents individual results](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-putevents.html), [EventBridge delivery DLQ](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-rule-dlq.html), [SQS consumer DLQ](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html), [ECS CloudFormation service](https://docs.aws.amazon.com/AWSCloudFormation/latest/TemplateReference/aws-resource-ecs-service.html). LocalStack feature listing is not evidence that every AWS failure mode is emulated identically. Run the supplied local messaging suite and a staging AWS suite before promotion.
