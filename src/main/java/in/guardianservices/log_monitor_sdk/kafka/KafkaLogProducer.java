package in.guardianservices.log_monitor_sdk.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

/**
 * A Kafka producer utility designed to send application log messages to a configured Kafka topic.
 * This producer is optimized for reliability and performance with batching and Snappy compression.
 */
public class KafkaLogProducer {

    private KafkaProducer<String, String> producer;
    private final String topic;

    /**
     * Initializes a new instance of {@link KafkaLogProducer}.
     *
     * @param bootstrapServers A comma-separated list of host:port pairs to establish the initial connection to the Kafka cluster.
     * @param topic            The name of the Kafka topic to which log events will be sent.
     */
    public KafkaLogProducer(String bootstrapServers, String topic) {
        this.topic = topic;
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // Reliability settings for an architected solution
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy"); // Reduce network bandwidth
        props.put(ProducerConfig.LINGER_MS_CONFIG, "20"); // Batching for performance

        this.producer = new KafkaProducer<>(props);
    }

    /**
     * Asynchronously sends a log payload to the configured Kafka topic.
     *
     * @param key     The key associated with the log event, typically the logger name or correlation ID.
     * @param payload The structured (JSON) payload containing the log event details.
     */
    public void sendLog(String key, String payload) {
        if (producer != null) {
            producer.send(new ProducerRecord<>(topic, key, payload));
        }
    }

    /**
     * Flushes any pending log messages and closes the Kafka producer instance.
     * This method should be called during application shutdown to ensure all logs are transmitted.
     */
    public void close() {
        if (producer != null) {
            producer.flush();
            producer.close();
        }
    }
}
