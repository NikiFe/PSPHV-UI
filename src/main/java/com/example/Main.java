package com.example;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // Define server port
        int port = 8080;
        Server server = new Server(port);

        // Create a ServletContextHandler with sessions enabled
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/"); // Set root context

        server.setHandler(context); // Assign the handler to the server

        try {
            // Add SeatWebSocketServlet to handle WebSocket connections at /ws/seat/*
            ServletHolder wsHolder = new ServletHolder("ws-handler", new SeatWebSocketServlet());
            context.addServlet(wsHolder, "/ws/seat/*"); // WebSocket endpoint at /ws/seat/*

            // Add ParliamentServlet to handle HTTP API requests at /api/*
            ServletHolder parliamentServletHolder = new ServletHolder(new ParliamentServlet());
            context.addServlet(parliamentServletHolder, "/api/*");

            // Add default servlet for serving static content (e.g., index.html)
            ServletHolder defaultServlet = new ServletHolder("default",
                    org.eclipse.jetty.servlet.DefaultServlet.class);
            defaultServlet.setInitParameter("resourceBase", "src/main/resources/webapp");
            defaultServlet.setInitParameter("dirAllowed", "true");
            context.addServlet(defaultServlet, "/");

            // Start the Jetty server
            server.start();
            logger.info("Server started at http://localhost:{}/", port);
            server.join(); // Keep the server running
        } catch (Throwable t) {
            logger.error("Error starting Jetty server: ", t);
            System.exit(1);
        } finally {
            server.destroy();
        }
    }
}
