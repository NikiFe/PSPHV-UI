package com.example;

import org.eclipse.jetty.websocket.server.WebSocketServerFactory;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import javax.servlet.http.HttpSession;

public class SeatWebSocketServlet extends WebSocketServlet {
    private static final long serialVersionUID = 1L;

    @Override
    public void configure(WebSocketServletFactory factory) {
        factory.setCreator(new WebSocketCreator() {
            @Override
            public Object createWebSocket(org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest req, org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse resp) {
                HttpSession httpSession = (HttpSession) req.getHttpServletRequest().getSession(false);
                boolean isAuthenticated = false;
                if (httpSession != null && httpSession.getAttribute("username") != null) {
                    isAuthenticated = true;
                }
                return new SeatWebSocket(isAuthenticated);
            }
        });
    }
}
