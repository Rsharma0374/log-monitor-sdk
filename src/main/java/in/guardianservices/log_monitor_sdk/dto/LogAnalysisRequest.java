package in.guardianservices.log_monitor_sdk.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Payload published to Kafka. Must stay in sync with the consumer's
 * {@code in.guardianservices.ai_log_analyzer.dto.LogAnalysisRequest}.
 * {@code rawLog} and {@code source} are mandatory on the consumer side.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LogAnalysisRequest {

    private String idempotencyKey;
    private String rawLog;
    private String source;
    private String severity;
    private String logger;
    private String environment;
    private Map<String, String> context;
}
