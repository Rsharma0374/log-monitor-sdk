package in.guardianservices.log_monitor_sdk.utils;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;

import java.util.Map;

/**
 * Utility class to map Logback's {@link ILoggingEvent} objects into structured JSON strings.
 * This class captures critical logging metadata including MDC and stack traces.
 */
public class LogEventMapper {

    /**
     * Converts an {@link ILoggingEvent} to a JSON-formatted string.
     * The resulting JSON object contains the timestamp, log level, logger name,
     * message, optional stack trace, and optional MDC context map.
     *
     * @param event The Logback logging event to be converted.
     * @return A structured JSON string representation of the log event.
     */
    public static String toJson(ILoggingEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        appendField(sb, "timestamp", String.valueOf(event.getTimeStamp()), false);
        appendField(sb, "level", event.getLevel().toString(), true);
        appendField(sb, "logger", event.getLoggerName(), true);
        appendField(sb, "message", event.getFormattedMessage(), true);

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
        for (StackTraceElementProxy step : tp.getStackTraceElementProxyArray()) {
            st.append(step.getSTEAsString()).append("\\n");
        }
        return st.toString();
    }
}
