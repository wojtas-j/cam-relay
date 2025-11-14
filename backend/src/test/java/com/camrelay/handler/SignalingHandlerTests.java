package com.camrelay.handler;

import com.camrelay.dto.socket.SignalingMessage;
import com.camrelay.service.SignalingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.socket.*;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SignalingHandler}, verifying connection handling,
 * message validation, ping/pong logic and routing to SignalingService.
 * @since 1.0
 */
class SignalingHandlerTests {

    private SignalingServiceImpl signalingService;
    private SignalingHandler handler;
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        signalingService = mock(SignalingServiceImpl.class);
        handler = new SignalingHandler(signalingService);
        session = mock(WebSocketSession.class);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("username", "alice");
        when(session.getAttributes()).thenReturn(attributes);

        when(session.isOpen()).thenReturn(true);
    }

    @Nested
    class ConnectionTests {

        /**
         * Tests successful session registration.
         * @since 1.0
         */
        @Test
        void afterConnectionEstablished_registersUser() {
            // Act
            handler.afterConnectionEstablished(session);

            // Assert
            verify(signalingService, times(1)).register("alice", session);
        }

        /**
         * Tests closing connection when there is no username attribute.
         * @since 1.0
         */
        @Test
        void afterConnectionEstablished_noUsername_closesSession() throws Exception {
            // Arrange
            Map<String, Object> attributes = new HashMap<>();
            when(session.getAttributes()).thenReturn(attributes);

            // Act
            handler.afterConnectionEstablished(session);

            // Assert
            verify(session, times(1)).close(any());
            verifyNoInteractions(signalingService);
        }
    }

    @Nested
    class MessageTests {

        /**
         * Tests the ping → pong flow.
         * @since 1.0
         */
        @Test
        void handleTextMessage_pingsBack() throws Exception {
            // Arrange
            TextMessage msg = new TextMessage("{\"type\":\"ping\"}");

            // Act
            handler.handleTextMessage(session, msg);

            // Assert
            verify(session, times(1)).sendMessage(any(TextMessage.class));
            verify(signalingService, never()).route(any());
        }

        /**
         * Tests routing a valid signaling message.
         * @since 1.0
         */
        @Test
        void handleTextMessage_validMessage_routesIt() throws Exception {
            // Arrange
            TextMessage msg = new TextMessage(
                    "{\"type\":\"offer\",\"from\":\"alice\",\"to\":\"bob\",\"payload\":\"xyz\"}"
            );

            // Act
            handler.handleTextMessage(session, msg);

            // Assert
            verify(signalingService, times(1)).route(any(SignalingMessage.class));
        }

        /**
         * Tests blocking impersonation when sender != session user.
         * @since 1.0
         */
        @Test
        void handleTextMessage_blocksImpersonation() throws Exception {
            // Arrange
            TextMessage msg = new TextMessage(
                    "{\"type\":\"offer\",\"from\":\"NOT_ALICE\",\"to\":\"bob\",\"payload\":\"xyz\"}"
            );

            // Act
            handler.handleTextMessage(session, msg);

            // Assert
            verify(signalingService, never()).route(any());
            verify(session, never()).sendMessage(any());
        }

        /**
         * Tests ignoring malformed JSON without throwing exceptions.
         * @since 1.0
         */
        @Test
        void handleTextMessage_invalidJson_doesNotThrow() throws Exception {
            // Arrange
            TextMessage msg = new TextMessage("INVALID_JSON");

            // Act
            handler.handleTextMessage(session, msg);

            // Assert
            verifyNoInteractions(signalingService);
            verify(session, never()).sendMessage(any());
        }

        /**
         * Tests ignoring incomplete signaling messages (missing type/from/to).
         * @since 1.0
         */
        @Test
        void handleTextMessage_invalidFields_doesNotRoute() throws Exception {
            // Arrange
            TextMessage msg = new TextMessage("{\"type\":\"offer\",\"from\":null,\"to\":\"bob\"}");

            // Act
            handler.handleTextMessage(session, msg);

            // Assert
            verify(signalingService, never()).route(any());
        }
    }

    @Nested
    class DisconnectTests {

        /**
         * Tests proper unregistering on disconnect.
         * @since 1.0
         */
        @Test
        void afterConnectionClosed_unregistersUser() {
            // Act
            handler.afterConnectionClosed(session, CloseStatus.NORMAL);

            // Assert
            verify(signalingService, times(1)).unregister("alice");
        }
    }
}
