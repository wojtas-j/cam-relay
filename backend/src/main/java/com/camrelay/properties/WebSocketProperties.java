package com.camrelay.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Loads WebSocket configuration values from application.yaml (prefix: websocket).
 * @since 1.0
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "websocket")
public class WebSocketProperties {
    private String path;
    private List<String> allowedOrigins;
    private Integer messageBufferLimit;
    private Long heartbeatIntervalMs;
}
