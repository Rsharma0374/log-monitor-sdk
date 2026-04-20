package in.guardianservices.log_monitor_sdk.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogAnalysisRequest {

    private String rawLog;
    private String source;
    private String severity;
    private String logger;
    private String environment;
}
