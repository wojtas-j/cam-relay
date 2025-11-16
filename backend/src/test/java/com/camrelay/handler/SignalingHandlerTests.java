package com.camrelay.handler;

import com.camrelay.dto.socket.SignalingMessage;
import com.camrelay.service.SignalingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.util.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SignalingHandler}, verifying connection handling,
 * message validation, ping/pong logic, role-based limits and routing to SignalingService.
 * @since 1.0
 */
class SignalingHandlerTests {

    private SignalingServiceImpl signalingService;
    private SignalingHandler handler;
    private WebSocketSession session;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        signalingService = mock(SignalingServiceImpl.class);
        handler = new SignalingHandler(signalingService);

        session = mock(WebSocketSession.class);
        authentication = mock(Authentication.class);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("username", "alice");
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        when(session.getPrincipal()).thenReturn(authentication);

        when(signalingService.countReceivers()).thenReturn(0L);
        when(signalingService.countNonReceivers()).thenReturn(0L);
    }

    private void setRoles(String... roleNames) {
        List<GrantedAuthority> authorities = Arrays.stream(roleNames)
                .map(role -> (GrantedAuthority) () -> role)
                .toList();
        doReturn(authorities).when(authentication).getAuthorities();
    }

    @Nested
    class ConnectionTests {

        /**
         * Tests successful registration for a regular user (non-receiver).
         * @since 1.0
         */
        @Test
        void afterConnectionEstablished_user_registersSuccessfully() throws IOException {
            // Arrange
            setRoles("ROLE_USER");

            // Act
            handler.afterConnectionEstablished(session);

            // Assert
            verify(signalingService, times(1)).register("alice", session);
            verify(session, never()).close(any());
        }

        /**
         * Tests successful registration for a receiver when limit not reached.
         * @since 1.0
         */
        @Test
        void afterConnectionEstablished_receiver_underLimit_registers() throws IOException {
            // Arrange
            setRoles("ROLE_RECEIVER");

            // Act
            handler.afterConnectionEstablished(session);

            // Assert
            verify(signalingService, times(1)).register("alice", session);
            verify(session, never()).close(any());
        }

        /**
         * Tests closing connection when receiver limit is reached (1 already connected).
         * @since 1.0
         */
        @Test
        void afterConnectionEstablished_receiver_limitReached_closesSession() throws Exception {
            // Arrange
            setRoles("ROLE_RECEIVER");
            when(signalingService.countReceivers()).thenReturn(1L);

            // Act
            handler.afterConnectionEstablished(session);

            // Assert
            verify(session, times(1)).close(argThat(status ->
                    status.getCode() == CloseStatus.POLICY_VIOLATION.getCode() &&
                            "Receiver limit reached".equals(status.getReason())
            ));
            verify(signalingService).countReceivers(); // countReceivers() JEST wywoływane!
            verify(signalingService, never()).register(any(), any()); // ale register NIE
        }

        /**
         * Tests closing connection when non-receiver limit is reached (1 already connected).
         * @since 1.0
         */
        @Test
        void afterConnectionEstablished_nonReceiver_limitReached_closesSession() throws Exception {
            // Arrange
            setRoles("ROLE_USER");
            when(signalingService.countNonReceivers()).thenReturn(1L);

            // Act
            handler.afterConnectionEstablished(session);

            // Assert
            verify(session, times(1)).close(argThat(status ->
                    status.getCode() == CloseStatus.POLICY_VIOLATION.getCode() &&
                            "User/Admin limit reached".equals(status.getReason())
            ));
            verify(signalingService).countNonReceivers();
            verify(signalingService, never()).register(any(), any());
        }

        /**
         * Tests closing connection when there is no username attribute.
         * @since 1.0
         */
        @Test
        void afterConnectionEstablished_noUsername_closesSession() throws Exception {
            // Arrange
            when(session.getAttributes()).thenReturn(new HashMap<>());
            setRoles();

            // Act
            handler.afterConnectionEstablished(session);

            // Assert
            verify(session).close(eq(CloseStatus.POLICY_VIOLATION.withReason("Unauthorized")));
            verify(signalingService, never()).countReceivers();
            verify(signalingService).countNonReceivers();
            verify(signalingService, never()).register(any(), any());
        }

        /**
         * Tests successful registration for admin (treated as non-receiver).
         * @since 1.0
         */
        @Test
        void afterConnectionEstablished_admin_registersAsNonReceiver() throws IOException {
            // Arrange
            setRoles("ROLE_ADMIN");

            // Act
            handler.afterConnectionEstablished(session);

            // Assert
            verify(signalingService, times(1)).register("alice", session);
            verify(session, never()).close(any());
        }

        /**
         * Tests that user with both ROLE_RECEIVER and ROLE_USER is treated as receiver.
         * @since 1.0
         */
        @Test
        void afterConnectionEstablished_receiverAndUser_isTreatedAsReceiver() throws IOException {
            // Arrange
            setRoles("ROLE_RECEIVER", "ROLE_USER");

            // Act
            handler.afterConnectionEstablished(session);

            // Assert
            verify(signalingService, times(1)).register("alice", session);
            verify(session, never()).close(any());
        }
    }

    @Nested
    class MessageTests {

        /**
         * Tests the ping to pong flow.
         * @since 1.0
         */
        @Test
        void handleTextMessage_pingsBack() throws Exception {
            // Arrange
            setRoles("ROLE_USER");
            TextMessage msg = new TextMessage("{\"type\":\"ping\"}");

            // Act
            handler.handleTextMessage(session, msg);

            // Assert
            verify(session).sendMessage(eq(new TextMessage("{\"type\":\"pong\"}")));
            verify(signalingService, never()).route(any());
        }

        /**
         * Tests routing a valid signaling message.
         * @since 1.0
         */
        @Test
        void handleTextMessage_validMessage_routesIt() throws Exception {
            // Arrange
            setRoles("ROLE_USER");
            TextMessage msg = new TextMessage(
                    "{\"type\":\"offer\",\"from\":\"alice\",\"to\":\"bob\",\"payload\":\"xyz\"}"
            );

            // Act
            handler.handleTextMessage(session, msg);

            // Assert
            verify(signalingService, times(1)).route(argThat(sm ->
                    "offer".equals(sm.getType()) &&
                            "alice".equals(sm.getFrom()) &&
                            "bob".equals(sm.getTo())
            ));
        }

        /**
         * Tests blocking impersonation when sender != session user.
         * @since 1.0
         */
        @Test
        void handleTextMessage_blocksImpersonation() throws Exception {
            // Arrange
            setRoles("ROLE_USER");
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
            setRoles("ROLE_USER");
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
            setRoles("ROLE_USER");
            TextMessage msg = new TextMessage("{\"type\":\"offer\",\"from\":null,\"to\":\"bob\"}");

            // Act
            handler.handleTextMessage(session, msg);

            // Assert
            verify(signalingService, never()).route(any());
        }

        /**
         * Tests that messages with extra fields are still routed if required ones are valid.
         * @since 1.0
         */
        @Test
        void handleTextMessage_extraFields_stillRoutes() throws Exception {
            // Arrange
            setRoles("ROLE_USER");
            String json = """
            {
                "type": "candidate",
                "from": "alice",
                "to": "bob",
                "payload": "xyz"
            }
            """;
            TextMessage msg = new TextMessage(json);

            // Act
            handler.handleTextMessage(session, msg);

            // Assert
            verify(signalingService, times(1)).route(any(SignalingMessage.class));
        }
    }

    @Nested
    class DisconnectTests {

        /**
         * Tests proper unregistering on normal disconnect.
         * @since 1.0
         */
        @Test
        void afterConnectionClosed_normal_unregistersUser() {
            // Act
            handler.afterConnectionClosed(session, CloseStatus.NORMAL);

            // Assert
            verify(signalingService, times(1)).unregister("alice");
        }

        /**
         * Tests unregistering even when username is missing (defensive).
         * @since 1.0
         */
        @Test
        void afterConnectionClosed_noUsername_doesNotThrow() {
            // Arrange
            when(session.getAttributes()).thenReturn(new HashMap<>());

            // Act
            handler.afterConnectionClosed(session, CloseStatus.NORMAL);

            // Assert
            verify(signalingService, never()).unregister(any());
        }
    }

    @Nested
    class ErrorHandlingTests {

        /**
         * Tests that transport error triggers unregister and logs error.
         * @since 1.0
         */
        @Test
        void handleTransportError_unregistersAndLogs() {
            // Arrange
            Throwable exception = new RuntimeException("Network error");

            // Act
            handler.handleTransportError(session, exception);

            // Assert
            verify(signalingService, times(1)).unregister("alice");
        }

        /**
         * Tests that transport error with no username does not throw.
         * @since 1.0
         */
        @Test
        void handleTransportError_noUsername_doesNotThrow() {
            // Arrange
            when(session.getAttributes()).thenReturn(new HashMap<>());
            Throwable exception = new RuntimeException("Network error");

            // Act
            handler.handleTransportError(session, exception);

            // Assert
            verify(signalingService, never()).unregister(any());
        }
    }
}
