package com.camrelay.service;

import com.camrelay.dto.socket.SignalingMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Tests the functionality of {@link SignalingServiceImpl} responsible
 * for registering sessions, unregistering them, and routing signaling messages.
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class SignalingServiceImplTests {

    private static final String USER_A = "alice";
    private static final String USER_B = "bob";

    @Mock
    private WebSocketSession sessionA;

    @Mock
    private WebSocketSession sessionB;

    @InjectMocks
    private SignalingServiceImpl signalingService;

    @BeforeEach
    void setUp() {
        reset(sessionA, sessionB);
    }

    @Nested
    class RegisterTests {

        /**
         * Tests successful session registration.
         * @since 1.0
         */
        @Test
        void shouldRegisterSessionSuccessfully() {
            // Act
            signalingService.register(USER_A, sessionA);

            // Assert
            // brak bezpośredniej weryfikacji mapy — sprawdzamy brak wyjątków i zapisanie poprzez routing
            signalingService.route(new SignalingMessage("ping", USER_B, USER_A, null));
        }
    }

    @Nested
    class UnregisterTests {

        /**
         * Tests successful unregister of a user session.
         * @since 1.0
         */
        @Test
        void shouldUnregisterSessionSuccessfully() throws IOException {
            // Arrange
            signalingService.register(USER_A, sessionA);

            // Act
            signalingService.unregister(USER_A);

            // Assert
            signalingService.route(new SignalingMessage("offer", USER_B, USER_A, "sdp"));
            verify(sessionA, never()).sendMessage(any());
        }
    }

    @Nested
    class RouteTests {

        /**
         * Tests routing a message to a connected target user.
         * @since 1.0
         */
        @Test
        void shouldRouteMessageSuccessfully() throws Exception {
            // Arrange
            signalingService.register(USER_B, sessionB);
            when(sessionB.isOpen()).thenReturn(true);

            SignalingMessage msg = new SignalingMessage("offer", USER_A, USER_B, "SDP_DATA");

            // Act
            signalingService.route(msg);

            // Assert
            verify(sessionB, times(1)).sendMessage(any(TextMessage.class));
        }

        /**
         * Tests ignoring routing when target is not registered.
         * @since 1.0
         */
        @Test
        void shouldNotRouteWhenTargetNotRegistered() throws IOException {
            // Arrange
            SignalingMessage msg = new SignalingMessage("answer", USER_A, USER_B, "SDP_DATA");

            // Act
            signalingService.route(msg);

            // Assert
            verify(sessionB, never()).sendMessage(any());
        }

        /**
         * Tests ignoring routing when target session is closed.
         * @since 1.0
         */
        @Test
        void shouldNotRouteWhenTargetSessionIsClosed() throws IOException {
            // Arrange
            signalingService.register(USER_B, sessionB);
            when(sessionB.isOpen()).thenReturn(false);
            SignalingMessage msg = new SignalingMessage("candidate", USER_A, USER_B, "json");

            // Act
            signalingService.route(msg);

            // Assert
            verify(sessionB, never()).sendMessage(any());
        }

        /**
         * Tests dropping message when `to` field is null.
         * @since 1.0
         */
        @Test
        void shouldDropMessageWithoutTarget() {
            // Arrange
            SignalingMessage msg = new SignalingMessage("offer", USER_A, null, "SDP_DATA");

            // Act
            signalingService.route(msg);

            // Assert
            verifyNoInteractions(sessionA, sessionB);
        }

        /**
         * Tests that the service gracefully handles exceptions from WebSocket.
         * @since 1.0
         */
        @Test
        void shouldHandleExceptionFromSocketWhenRouting() throws Exception {
            // Arrange
            signalingService.register(USER_B, sessionB);
            when(sessionB.isOpen()).thenReturn(true);
            doThrow(new RuntimeException("Socket error")).when(sessionB).sendMessage(any());

            SignalingMessage msg = new SignalingMessage(
                    "answer",
                    USER_A,
                    USER_B,
                    UUID.randomUUID().toString()
            );

            // Act
            signalingService.route(msg);

            // Assert
            // nie rzuca wyjątku
            verify(sessionB, times(1)).sendMessage(any());
        }
    }
}
