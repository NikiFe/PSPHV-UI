package com.example;

import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

public class SeatWebSocketServlet extends WebSocketServlet {
    private static final long serialVersionUID = 1L;

    @Override
    public void configure(WebSocketServletFactory factory) {
        // Register the SeatWebSocket class as the WebSocket endpoint
        factory.register(SeatWebSocket.class);
    }
}
