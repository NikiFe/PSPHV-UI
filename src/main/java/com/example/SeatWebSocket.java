package com.example;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;

import java.util.concurrent.CopyOnWriteArraySet;

@WebSocket
public class SeatWebSocket {
    private static final Logger logger = LoggerFactory.getLogger(SeatWebSocket.class);

    // Thread-safe set to store all active WebSocket sessions
    private static final CopyOnWriteArraySet<Session> sessions = new CopyOnWriteArraySet<>();

    @OnWebSocketConnect
    public void onConnect(Session session) throws Exception {
        sessions.add(session);
        logger.info("WebSocket Connected: {}", session.getRemoteAddress().getAddress());
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        sessions.remove(session);
        logger.info("WebSocket Closed: {} Reason: {}", session.getRemoteAddress().getAddress(), reason);
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) {
        logger.info("Received message from {}: {}", session.getRemoteAddress().getAddress(), message);
        // Handle incoming messages if needed
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
}
