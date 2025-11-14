package com.camrelay.dto.socket;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic signaling message payload for WebRTC exchange.
 * type: "offer" | "answer" | "candidate" | "ping"
 * from: username of sender
 * to: target username
 * payload: string with SDP or ICE candidate (JSON string)
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SignalingMessage {
    private String type;
    private String from;
    private String to;
    private String payload;
}