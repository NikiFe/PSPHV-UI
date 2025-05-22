package com.example;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;

import java.util.concurrent.CopyOnWriteArraySet;

@WebSocket
public class SeatWebSocket {
    private static final Logger logger = LoggerFactory.getLogger(SeatWebSocket.class);

    // Thread-safe set to store all active WebSocket sessions
    private static final CopyOnWriteArraySet<Session> sessions = new CopyOnWriteArraySet<>();

    private final boolean authenticated;

    // Constructor to accept authentication status
    public SeatWebSocket(boolean isAuthenticated) {
        this.authenticated = isAuthenticated;
    }

    @OnWebSocketConnect
    public void onConnect(Session session) throws Exception {
        // No longer need to get attributes here, use the member variable
        // ServletUpgradeRequest servletUpgradeRequest = (ServletUpgradeRequest) session.getUpgradeRequest();
        // Boolean isAuthenticated = (Boolean) servletUpgradeRequest.getHttpServletRequest().getAttribute("isAuthenticated");

        if (this.authenticated) {
            sessions.add(session);
            logger.info("WebSocket Connected (Authenticated): {}", session.getRemoteAddress().getAddress());
        } else {
            logger.warn("WebSocket Connection Attempt Rejected (Unauthenticated): {}", session.getRemoteAddress().getAddress());
            session.close(StatusCode.POLICY_VIOLATION, "User not authenticated");
        }
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        sessions.remove(session);
        logger.info("WebSocket Closed: {} Reason: {}", session.getRemoteAddress().getAddress(), reason);
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) {
        logger.info("Received message from {}: {}", session.getRemoteAddress().getAddress(), message);
        // Echo logic removed
        // Handle incoming messages if needed (original comment)
        // For example, if clients could send specific commands:
        // try {
        //     JSONObject clientMessage = new JSONObject(message);
        //     // process clientMessage
        // } catch (org.json.JSONException e) {
        //     logger.warn("Received non-JSON message or malformed JSON from client: {}", message);
        // }
    }

    @OnWebSocketError
    public void onError(Session session, Throwable error) {
        logger.error("WebSocket Error on session {}: {}", session.getRemoteAddress().getAddress(), error.getMessage());
    }

    // Method to broadcast a message to all connected clients
    public static void broadcast(String message) {
        for (Session session : sessions) {
            if (session.isOpen()) {
                session.getRemote().sendStringByFuture(message);
                logger.debug("Sent message to {}: {}", session.getRemoteAddress().getAddress(), message);
            }
        }
    }

    // Overloaded method to broadcast JSON objects
    public static void broadcast(JSONObject json) {
        broadcast(json.toString());
    }

    public static void broadcastSeatStatusChange(String userId, String newStatus) {
        JSONObject statusUpdate = new JSONObject();
        statusUpdate.put("type", "seatStatusChange");
        statusUpdate.put("userId", userId);
        statusUpdate.put("seatStatus", newStatus);
        broadcast(statusUpdate); // This sends the update to all connected clients
    }
}
