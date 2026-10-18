package in.guardianservices.log_monitor_sdk.kafka;

import ch.qos.logback.core.spi.ContextAwareBase;
import in.guardianservices.log_monitor_sdk.utils.SdkConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

/**
 * A Kafka producer utility designed to send application log messages to a configured Kafka topic.
 * The key is the logger name and the value is a JSON {@code LogAnalysisRequest}, as expected by
 * the log analyzer's {@code ErrorLogConsumer}.
 * <p>
 * Diagnostics go through Logback's status system (via {@link ContextAwareBase}) instead of SLF4J,
 * because logging through the appender being served would recurse.
 */
public class KafkaLogProducer {

    private final KafkaProducer<String, String> producer;
    private final String topic;
    private final ContextAwareBase status;

    /**
     * @param bootstrapServers comma-separated host:port list of the Kafka cluster
     * @param topic            target topic
     * @param status           Logback status reporter (the owning appender's context)
     */
    public KafkaLogProducer(String bootstrapServers, String topic, ContextAwareBase status) {
        this.topic = topic;
        this.status = status;

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "log-monitor-sdk");

        props.put(ProducerConfig.ACKS_CONFIG, orDefault(SdkConfig.ACKS, "1"));
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, orDefault(SdkConfig.COMPRESSION_TYPE, "snappy"));
        props.put(ProducerConfig.LINGER_MS_CONFIG, orDefault(SdkConfig.LINGER_MS, "20"));
        // Never block the application thread for long when the broker is down
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "2000");
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "30000");
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "10000");

        this.producer = new KafkaProducer<>(props);
        status.addInfo("Kafka log producer started. topic=" + topic + ", bootstrapServers=" + bootstrapServers);
    }

    private static String orDefault(String key, String fallback) {
        String value = SdkConfig.resolve(null, key);
        return value != null ? value : fallback;
    }

    /**
     * Asynchronously sends a log payload to the configured Kafka topic. Failures are reported to
     * the Logback status manager and never propagate to the application.
     */
    public void sendLog(String key, String logAnalysisRequest) {
        try {
            producer.send(new ProducerRecord<>(topic, key, logAnalysisRequest), (metadata, ex) -> {
                if (ex != null) {
                    status.addWarn("Failed to deliver log to Kafka topic " + topic + ": " + ex.getMessage());
                }
            });
        } catch (Exception e) {
            status.addWarn("Failed to enqueue log for Kafka topic " + topic, e);
        }
    }

    /** Flushes pending messages and closes the producer. Call on application shutdown. */
    public void close() {
        try {
            producer.flush();
            producer.close();
            status.addInfo("Kafka log producer closed");
        } catch (Exception e) {
            status.addWarn("Error while closing Kafka log producer", e);
        }
    }
}
