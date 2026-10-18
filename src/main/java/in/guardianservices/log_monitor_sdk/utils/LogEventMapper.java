package in.guardianservices.log_monitor_sdk.utils;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.guardianservices.log_monitor_sdk.dto.LogAnalysisRequest;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Utility class to map Logback's {@link ILoggingEvent} objects into structured JSON strings.
 * This class captures critical logging metadata including MDC and stack traces.
 */
public class LogEventMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    /**
     * Converts an {@link ILoggingEvent} to a JSON-formatted string.
     * The resulting JSON object contains the timestamp, log level, logger name,
     * message, optional stack trace, and optional MDC context map.
     *
     * @param event The Logback logging event to be converted.
     * @return A structured JSON string representation of the log event.
     */
    public static String toJson(ILoggingEvent event, String environment) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        appendField(sb, "timestamp", String.valueOf(event.getTimeStamp()), false);
        appendField(sb, "level", event.getLevel().toString(), true);
        appendField(sb, "logger", event.getLoggerName(), true);
        appendField(sb, "message", event.getFormattedMessage(), true);
        if (StringUtils.isNoneBlank(environment)) {
            appendField(sb, "environment", environment, true);
        }

        if (event.getThrowableProxy() != null) {
            appendField(sb, "stackTrace", formatStackTrace(event.getThrowableProxy()), true);
        }

        // Capture MDC (User context like user_id, request_id)
        Map<String, String> mdc = event.getMDCPropertyMap();
        if (!mdc.isEmpty()) {
            appendField(sb, "context", mdc.toString(), true);
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * Appends a key-value pair to a StringBuilder containing JSON output.
     *
     * @param sb    The StringBuilder being used to construct the JSON.
     * @param key   The key of the JSON field.
     * @param value The value of the JSON field.
     * @param quote A flag indicating whether the value should be enclosed in quotes.
     */
    private static void appendField(StringBuilder sb, String key, String value, boolean quote) {
        if (sb.length() > 1) sb.append(",");
        sb.append("\"").append(key).append("\":");
        if (quote) sb.append("\"").append(value.replace("\"", "\\\"")).append("\"");
        else sb.append(value);
    }

    /**
     * Formats a throwable proxy into a string containing the stack trace.
     *
     * @param tp The throwable proxy to format.
     * @return A string containing the formatted stack trace.
     */
    private static String formatStackTrace(IThrowableProxy tp) {
        StringBuilder st = new StringBuilder();
        StackTraceElementProxy[] steps = tp.getStackTraceElementProxyArray();
        if (steps != null) {
            for (StackTraceElementProxy step : steps) {
                st.append(step.getSTEAsString()).append("\n");
            }
        }
        return st.toString();
    }

    /**
     * Maps a Logback event to the JSON payload expected by the analyzer's {@code ErrorLogConsumer}.
     *
     * @return JSON string, or {@code null} if serialization failed (caller must not send it)
     */
    public static String mapLogEvent(ILoggingEvent event, String environment, String serviceName) {
        try {
            LogAnalysisRequest request = new LogAnalysisRequest();
            String rawLog = event.getFormattedMessage();
            if (event.getThrowableProxy() != null) {
                rawLog = rawLog + "\n" + event.getThrowableProxy().getClassName() + ": "
                        + event.getThrowableProxy().getMessage() + "\n"
                        + formatStackTrace(event.getThrowableProxy());
            }
            request.setRawLog(rawLog);
            request.setSource(serviceName);
            request.setSeverity(event.getLevel().toString());
            request.setLogger(event.getLoggerName());
            request.setEnvironment(environment);
            request.setIdempotencyKey(idempotencyKey(serviceName, event, rawLog));

            Map<String, String> mdc = event.getMDCPropertyMap();
            if (mdc != null && !mdc.isEmpty()) {
                request.setContext(new HashMap<>(mdc));
            }
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            return null;
        }
    }

    /** Deterministic key so a retried send of the same event can be de-duplicated downstream. */
    private static String idempotencyKey(String serviceName, ILoggingEvent event, String rawLog) throws Exception {
        String seed = serviceName + "|" + event.getLoggerName() + "|" + event.getTimeStamp() + "|" + rawLog;
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
