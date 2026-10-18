package in.guardianservices.log_monitor_sdk.utils;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads profile-specific defaults from {@code log-monitor-sdk.properties}, which is packaged
 * from {@code src/main/resources/profile/<profile>} depending on the active Maven profile
 * ({@code -Pdev} by default, {@code -Pprod} for production).
 * Resolution order: explicit value (logback.xml) &gt; environment variable &gt; profile file.
 */
public final class SdkConfig {

    public static final String BOOTSTRAP_SERVERS = "log.monitor.kafka.bootstrap-servers";
    public static final String TOPIC = "log.monitor.kafka.topic";
    public static final String ACKS = "log.monitor.kafka.acks";
    public static final String COMPRESSION_TYPE = "log.monitor.kafka.compression-type";
    public static final String LINGER_MS = "log.monitor.kafka.linger-ms";

    private static final String FILE = "log-monitor-sdk.properties";
    private static final Properties DEFAULTS = load();

    private SdkConfig() {
    }

    private static Properties load() {
        Properties props = new Properties();
        try (InputStream in = SdkConfig.class.getClassLoader().getResourceAsStream(FILE)) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
            // falls back to explicit configuration only; the appender reports missing values
        }
        return props;
    }

    /** @return explicit value if set, else env var (e.g. LOG_MONITOR_KAFKA_TOPIC), else profile default. */
    public static String resolve(String explicit, String key) {
        if (StringUtils.isNotBlank(explicit)) {
            return explicit.trim();
        }
        String env = System.getenv(key.toUpperCase().replace('.', '_').replace('-', '_'));
        if (StringUtils.isNotBlank(env)) {
            return env.trim();
        }
        return DEFAULTS.getProperty(key);
    }
}
