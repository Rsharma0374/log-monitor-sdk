package in.guardianservices.log_monitor_sdk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Log Monitor SDK.
 * This class is the entry point for running the SDK as a standalone Spring Boot application,
 * typically used for testing or running embedded services.
 */
@SpringBootApplication
public class LogMonitorSdkApplication {

	/**
	 * Main method that starts the Spring Boot application.
	 *
	 * @param args command line arguments passed to the application
	 */
	public static void main(String[] args) {
		SpringApplication.run(LogMonitorSdkApplication.class, args);
	}

}
