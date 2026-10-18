# Log Monitor SDK

A Logback-based SDK that forwards application `ERROR` logs to Apache Kafka, where the **AI Log Analyzer** (`ErrorLogConsumer`) picks them up for analysis.

The SDK provides a custom Logback appender (`KafkaAiLogAppender`). Once added to an application's `logback.xml`, every `ERROR` (or higher) log is mapped to a structured JSON payload and published to Kafka, asynchronously and without affecting the host application if Kafka is unavailable.

## Features

- **Custom Logback appender**: intercepts `ERROR` logs directly from Logback.
- **Consumer-compatible payload**: JSON matching the analyzer's `LogAnalysisRequest` (`idempotencyKey`, `rawLog`, `source`, `severity`, `logger`, `environment`, `context`).
- **Profile-based Kafka configuration**: separate Kafka servers for `dev` and `prod`, selected at build time.
- **Safe by design**:
  - Non-blocking sends with delivery callbacks; failures never reach the application.
  - Ignores logs from `org.apache.kafka.*` and the SDK itself, so there are no feedback loops.
  - Diagnostics go through Logback's status system (visible with `<statusListener>`).
- **Efficient**: only `ERROR`+ logs, Snappy compression, `linger.ms` batching.

## Prerequisites

- Java 21+
- Logback (`logback-classic`) as the logging backend (the default in Spring Boot)
- Apache Maven
- A reachable Apache Kafka cluster

## Message contract

| Item  | Value |
|-------|-------|
| Topic | `logging.analytics.raw-errors.v1` |
| Key   | Logger name |
| Value | JSON `LogAnalysisRequest` |

```json
{
  "idempotencyKey": "9f2c...sha256",
  "rawLog": "Payment failed\njava.lang.IllegalStateException: ...\n\tat com.acme...",
  "source": "payment-service",
  "severity": "ERROR",
  "logger": "com.acme.payment.PaymentService",
  "environment": "prod",
  "context": { "requestId": "abc-123", "userId": "42" }
}
```

`rawLog` and `source` are **mandatory**: the consumer rejects messages without them. `source` comes from the `serviceName` appender property, so always set it. `context` is filled from the logging MDC.

## Building the SDK (profiles)

Kafka settings are packaged per Maven profile from `src/main/resources/profile/<profile>/log-monitor-sdk.properties`.

| Profile | Command | Brokers (default) | acks |
|---------|---------|-------------------|------|
| `dev` (default) | `./mvnw clean install` | `localhost:9092` | `1` |
| `prod` | `./mvnw clean install -Pprod` | prod brokers configured in the prod file | `all` |

Edit the file of each profile to point to the right Kafka cluster:

```properties
log.monitor.kafka.bootstrap-servers=host1:9092,host2:9092
log.monitor.kafka.topic=logging.analytics.raw-errors.v1
log.monitor.kafka.acks=all
log.monitor.kafka.compression-type=snappy
log.monitor.kafka.linger-ms=20
```

Resolution order for each setting (highest wins):

1. Value set in `logback.xml` (`bootstrapServers`, `topic`)
2. Environment variable, e.g. `LOG_MONITOR_KAFKA_BOOTSTRAP_SERVERS`, `LOG_MONITOR_KAFKA_TOPIC`
3. Value from the packaged profile file

> Tip: publish the `dev` and `prod` builds under the same version only if you deploy them to separate repositories; otherwise leave `bootstrapServers` unset in `logback.xml` and override with the environment variable per environment.

## Integrating the SDK into another application

### 1. Add the dependency

Install the SDK to your local/company Maven repository (`./mvnw clean install`, with `-Pprod` for the production build), then add it to the application's `pom.xml`:

```xml
<dependency>
    <groupId>in.guardianservices</groupId>
    <artifactId>log-monitor-sdk</artifactId>
    <version>2026.0.1</version>
</dependency>
```

The application must use Logback (Spring Boot does by default). If it uses `spring-boot-starter-log4j2`, switch back to Logback first.

### 2. Configure the appender

Create `src/main/resources/logback-spring.xml` (or `logback.xml` for non-Spring apps).

#### Plain Java / any Logback app

```xml
<configuration>
    <appender name="KAFKA_APPENDER" class="in.guardianservices.log_monitor_sdk.analyzer.KafkaAiLogAppender">
        <serviceName>payment-service</serviceName>
        <environment>prod</environment>
        <!-- Optional: omit to use the profile / environment-variable defaults -->
        <bootstrapServers>kafka-prod-1:9092,kafka-prod-2:9092</bootstrapServers>
        <topic>logging.analytics.raw-errors.v1</topic>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="KAFKA_APPENDER"/>
    </root>
</configuration>
```

#### Spring Boot app (recommended: environment follows the Spring profile)

```xml
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>

    <springProperty scope="context" name="APP_NAME" source="spring.application.name" defaultValue="unknown-service"/>
    <springProfile name="dev">
        <property name="APP_ENV" value="dev"/>
    </springProfile>
    <springProfile name="prod">
        <property name="APP_ENV" value="prod"/>
    </springProfile>

    <appender name="KAFKA_APPENDER" class="in.guardianservices.log_monitor_sdk.analyzer.KafkaAiLogAppender">
        <serviceName>${APP_NAME}</serviceName>
        <environment>${APP_ENV}</environment>
        <!-- bootstrapServers/topic intentionally omitted: taken from the SDK profile build
             or LOG_MONITOR_KAFKA_BOOTSTRAP_SERVERS -->
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="KAFKA_APPENDER"/>
    </root>
</configuration>
```

Different Kafka servers per environment, without rebuilding the SDK, can be achieved by setting the environment variable on each deployment:

```bash
export LOG_MONITOR_KAFKA_BOOTSTRAP_SERVERS=kafka-prod-1:9092,kafka-prod-2:9092
```

### 3. Appender properties

| Property | Required | Description |
|----------|----------|-------------|
| `serviceName` | Yes | Sent as `source`. Identifies the application in the analyzer. |
| `environment` | Recommended | e.g. `dev`, `prod`. Sent as `environment`. |
| `bootstrapServers` | No* | Comma-separated Kafka brokers. Overrides env var and profile file. |
| `topic` | No* | Target topic. Overrides env var and profile file. |

\* One of the three configuration sources must provide a value, otherwise the appender logs a status error and stays disabled.

### 4. Add context (optional)

Anything put in the MDC is sent as `context` and helps the analyzer:

```java
MDC.put("requestId", requestId);
MDC.put("userId", userId);
try {
    ...
} finally {
    MDC.clear();
}
```

### 5. Verify

1. Start the application and trigger an error: `log.error("Something failed", exception);`
2. Check the topic:
   ```bash
   kafka-console-consumer.sh --bootstrap-server localhost:9092 \
     --topic logging.analytics.raw-errors.v1 --property print.key=true --from-beginning
   ```
3. To debug the SDK itself, enable Logback status output in your `logback.xml`:
   ```xml
   <statusListener class="ch.qos.logback.core.status.OnConsoleStatusListener"/>
   ```
   You should see `Kafka log producer started. topic=..., bootstrapServers=...`.

### Troubleshooting

| Symptom | Likely cause |
|---------|--------------|
| `bootstrapServers/topic are not configured` | No value in `logback.xml`, env var or profile file. |
| `serviceName is not set` warning | The consumer will reject messages without `source`. Set `serviceName`. |
| `Failed to deliver log to Kafka topic` | Broker unreachable or wrong address for the active environment. |
| Nothing arrives, no status output | The log level is below `ERROR`, or the logger belongs to `org.apache.kafka.*` / the SDK (intentionally ignored). |

## How it works

1. **Interception**: `KafkaAiLogAppender` receives log events from Logback.
2. **Filtering**: logs below `ERROR`, and logs from Kafka/the SDK, are ignored.
3. **Mapping**: `LogEventMapper` builds a `LogAnalysisRequest` (message + stack trace, MDC context, deterministic idempotency key).
4. **Publishing**: `KafkaLogProducer` sends the JSON to Kafka (key = logger name) with a delivery callback; failures are reported via Logback status and never thrown.
