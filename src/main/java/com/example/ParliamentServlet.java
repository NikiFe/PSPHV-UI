package com.example;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ParliamentServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(ParliamentServlet.class);

    private final MongoCollection<Document> usersCollection;
    private final MongoCollection<Document> proposalsCollection;

    // Constructor to inject MongoDB collections
    public ParliamentServlet() {
        // Initialize MongoDB connection and get collections
        MongoDatabase database = MongoDBConnection.getDatabase();
        this.usersCollection = database.getCollection("users");
        this.proposalsCollection = database.getCollection("proposals");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getPathInfo();

        switch (path) {
            case "/login":
                handleLogin(request, response);
                break;
            case "/logout":
                handleLogout(request, response);
                break;
            case "/join-seat":
                handleJoinSeat(request, response);
                break;
            case "/users/update-status":
                handleUpdateStatus(request, response);
                break;
            case "/proposals":
                handleNewProposal(request, response);
                break;
            case "/impose-fine":
                handleImposeFine(request, response);
                break;
            case "/break":
                handleCallBreak(request, response);
                break;
            case "/end-session":
                handleEndSession(request, response);
                break;
            case "/register": // Handle registration
                handleRegister(request, response);
                break;
            default:
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Endpoint not found.");
                logger.warn("Unknown POST endpoint: {}", path);
                break;
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        String path = request.getPathInfo();

        switch (path) {
            case "/users":
                handleGetUsers(request, response);
                break;
            case "/user-info":
                handleUserInfo(request, response);
                break;
            case "/proposals":
                handleGetProposals(request, response);
                break;
            case "/queue":
                handleGetQueue(request, response);
                break;
            default:
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Endpoint not found.");
                logger.warn("Unknown GET endpoint: {}", path);
                break;
        }
    }

    // Handle user registration
    private void handleRegister(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            // Parse JSON from request body
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                sb.append(line);
            }
            JSONObject registerJson = new JSONObject(sb.toString());
            String username = registerJson.getString("username").trim();
            String password = registerJson.getString("password").trim();
            String role = registerJson.optString("role", "MEMBER").trim(); // Default role is MEMBER

            // Validate input
            if (username.isEmpty() || password.isEmpty() || role.isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Username, password, and role cannot be empty.");
                logger.warn("Registration attempt with empty fields.");
                return;
            }

            // Check if username already exists
            Document existingUser = usersCollection.find(new Document("username", username)).first();
            if (existingUser != null) {
                response.sendError(HttpServletResponse.SC_CONFLICT, "Username already exists.");
                logger.warn("Registration attempt with existing username '{}'.", username);
                return;
            }

            // Hash the password using BCrypt
            String hashedPassword = MongoDBConnection.hashPassword(password);

            // Create new user document
            Document newUser = new Document("username", username)
                    .append("password", hashedPassword)
                    .append("role", role.toUpperCase())
                    .append("present", false)
                    .append("seatStatus", "NEUTRAL")
                    .append("fines", 0)
                    .append("partyAffiliation", "") // Initialize as empty; can be updated later
                    .append("electoralStrength", 0); // Initialize as 0; can be updated later

            // Insert the new user into the database
            usersCollection.insertOne(newUser);
            logger.info("New user '{}' registered successfully with role '{}'.", username, role);

            // Respond with success
            response.setStatus(HttpServletResponse.SC_CREATED);
            JSONObject resp = new JSONObject();
            resp.put("message", "Registration successful. Please log in.");
            response.setContentType("application/json");
            response.getWriter().write(resp.toString());

        } catch (Exception e) {
            logger.error("Error during registration: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred during registration.");
        }
    }

    // Handle user login
    private void handleLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            // Parse JSON from request body
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                sb.append(line);
            }
            JSONObject loginJson = new JSONObject(sb.toString());
            String username = loginJson.getString("username").trim();
            String password = loginJson.getString("password").trim();

            // Validate input
            if (username.isEmpty() || password.isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Username and password cannot be empty.");
                logger.warn("Login attempt with empty username or password.");
                return;
            }

            // Fetch user from database
            Document query = new Document("username", username);
            Document userDoc = usersCollection.find(query).first();

            if (userDoc != null) {
                // Verify password using BCrypt
                String hashedPassword = userDoc.getString("password");
                if (MongoDBConnection.verifyPassword(password, hashedPassword)) {
                    // Create session and set attributes
                    HttpSession session = request.getSession(true);
                    session.setAttribute("username", username);
                    session.setAttribute("role", userDoc.getString("role"));

                    response.setStatus(HttpServletResponse.SC_OK);
                    JSONObject resp = new JSONObject();
                    resp.put("message", "Login successful.");
                    response.setContentType("application/json");
                    response.getWriter().write(resp.toString());
                    logger.info("User '{}' logged in successfully.", username);
                    return;
                }
            }

            // Invalid credentials
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid username or password.");
            logger.warn("Failed login attempt for username '{}'.", username);
        } catch (Exception e) {
            logger.error("Error during login: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred during login.");
        }
    }

    // Handle user logout
    private void handleLogout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                String username = (String) session.getAttribute("username");
                session.invalidate();
                logger.info("User '{}' logged out.", username);
            }
            response.setStatus(HttpServletResponse.SC_OK);
            JSONObject resp = new JSONObject();
            resp.put("message", "Logout successful.");
            response.setContentType("application/json");
            response.getWriter().write(resp.toString());
        } catch (Exception e) {
            logger.error("Error during logout: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred during logout.");
        }
    }

    // Handle user joining a seat
    private void handleJoinSeat(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("username") != null) {
                String username = (String) session.getAttribute("username");

                Document query = new Document("username", username);
                Document userDoc = usersCollection.find(query).first();

                if (userDoc != null) {
                    boolean isPresent = userDoc.getBoolean("present", false);
                    if (isPresent) {
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "User is already in a seat.");
                        logger.warn("User '{}' attempted to join a seat but is already present.", username);
                        return;
                    }

                    // Update seatStatus to 'NEUTRAL' and set 'present' to true
                    Document update = new Document("$set", new Document("seatStatus", "NEUTRAL").append("present", true));
                    usersCollection.updateOne(query, update);

                    // Fetch updated user info
                    Document updatedUserDoc = usersCollection.find(query).first();
                    JSONObject userJson = new JSONObject(updatedUserDoc.toJson());
                    userJson.put("id", updatedUserDoc.getObjectId("_id").toString());

                    // Broadcast seat update via WebSocket
                    JSONObject seatUpdate = new JSONObject();
                    seatUpdate.put("type", "seatUpdate");
                    seatUpdate.put("user", userJson);
                    SeatWebSocket.broadcast(seatUpdate);

                    response.setStatus(HttpServletResponse.SC_OK);
                    JSONObject resp = new JSONObject();
                    resp.put("message", "Successfully joined a seat.");
                    response.setContentType("application/json");
                    response.getWriter().write(resp.toString());
                    logger.info("User '{}' joined a seat.", username);
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "User not found.");
                    logger.warn("User '{}' not found in the database.", username);
                }
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not authenticated.");
                logger.warn("Unauthenticated attempt to join a seat.");
            }
        } catch (Exception e) {
            logger.error("Error during join seat: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while joining the seat.");
        }
    }

    // Handle updating seat status (e.g., raising hand, objecting, canceling)
    private void handleUpdateStatus(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("username") != null) {
                String requesterUsername = (String) session.getAttribute("username");
                String requesterRole = (String) session.getAttribute("role");

                // Parse JSON from request body
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = request.getReader().readLine()) != null) {
                    sb.append(line);
                }
                JSONObject statusUpdate = new JSONObject(sb.toString());
                String userId = statusUpdate.getString("id");
                String newStatus = statusUpdate.getString("seatStatus");

                // Fetch user by ID
                Document query = new Document("_id", new org.bson.types.ObjectId(userId));
                Document userDoc = usersCollection.find(query).first();

                if (userDoc != null) {
                    String targetUsername = userDoc.getString("username");

                    // If the requester is not the president and trying to update someone else's status, deny
                    if (!"PRESIDENT".equals(requesterRole) && !targetUsername.equals(requesterUsername)) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Cannot update other users' status.");
                        logger.warn("User '{}' attempted to update status of '{}'. Forbidden.", requesterUsername, targetUsername);
                        return;
                    }

                    // Validate newStatus
                    if (!isValidSeatStatus(newStatus)) {
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid seat status.");
                        logger.warn("Invalid seat status '{}' provided by user '{}'.", newStatus, requesterUsername);
                        return;
                    }

                    // Prepare update document
                    Document update;
                    if ("NEUTRAL".equals(newStatus)) {
                        // When setting to NEUTRAL, do NOT modify 'present'
                        update = new Document("$set", new Document("seatStatus", "NEUTRAL"));
                    } else {
                        // When setting to any other status, ensure 'present' is true
                        update = new Document("$set", new Document("seatStatus", newStatus).append("present", true));
                    }

                    // Update the user's seat status
                    usersCollection.updateOne(query, update);

                    // Fetch updated user info
                    Document updatedUserDoc = usersCollection.find(query).first();
                    JSONObject userJson = new JSONObject(updatedUserDoc.toJson());
                    userJson.put("id", updatedUserDoc.getObjectId("_id").toString());

                    // Broadcast seat update via WebSocket
                    JSONObject seatUpdate = new JSONObject();
                    seatUpdate.put("type", "seatUpdate");
                    seatUpdate.put("user", userJson);
                    SeatWebSocket.broadcast(seatUpdate);

                    response.setStatus(HttpServletResponse.SC_OK);
                    JSONObject resp = new JSONObject();
                    resp.put("message", "Seat status updated successfully.");
                    response.setContentType("application/json");
                    response.getWriter().write(resp.toString());
                    logger.info("User '{}' updated seat status of '{}' to '{}'.", requesterUsername, targetUsername, newStatus);
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "User not found.");
                    logger.warn("User with ID '{}' not found.", userId);
                }
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not authenticated.");
                logger.warn("Unauthenticated attempt to update seat status.");
            }
        } catch (Exception e) {
            logger.error("Error during updating seat status: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while updating seat status.");
        }
    }

    // Validate seat status
    private boolean isValidSeatStatus(String status) {
        return "REQUESTING_TO_SPEAK".equals(status) ||
                "SPEAKING".equals(status) ||
                "OBJECTING".equals(status) ||
                "NEUTRAL".equals(status);
    }

    // Handle adding a new proposal (President only)
    private void handleNewProposal(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                // Parse JSON from request body
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = request.getReader().readLine()) != null) {
                    sb.append(line);
                }
                JSONObject proposalJson = new JSONObject(sb.toString());
                String title = proposalJson.getString("title").trim();
                String party = proposalJson.optString("party", "President").trim(); // Default party is President

                if (title.isEmpty()) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Proposal title cannot be empty.");
                    logger.warn("President attempted to add a proposal with empty title.");
                    return;
                }

                // Assign proposal number
                int nextProposalNumber = getNextProposalNumber();

                // Save the proposal to the database
                Document proposalDoc = new Document("title", title)
                        .append("proposalNumber", nextProposalNumber)
                        .append("party", party);
                proposalsCollection.insertOne(proposalDoc);

                // Broadcast proposal update via WebSocket
                JSONObject proposalUpdate = new JSONObject();
                proposalUpdate.put("type", "proposalUpdate");
                proposalUpdate.put("proposal", new JSONObject(proposalDoc.toJson()));
                SeatWebSocket.broadcast(proposalUpdate);

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "New proposal added successfully.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President added new proposal: '{}'", title);
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can add proposals.");
                logger.warn("Non-president attempted to add a new proposal.");
            }
        } catch (Exception e) {
            logger.error("Error during adding new proposal: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while adding the proposal.");
        }
    }

    // Handle imposing a fine on a user (President only)
    private void handleImposeFine(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                // Parse JSON from request body
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = request.getReader().readLine()) != null) {
                    sb.append(line);
                }
                JSONObject fineJson = new JSONObject(sb.toString());
                String usernameToFine = fineJson.getString("username").trim();
                int amount = fineJson.getInt("amount");

                if (usernameToFine.isEmpty() || amount <= 0) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid username or amount.");
                    logger.warn("President provided invalid username '{}' or amount '{}'.", usernameToFine, amount);
                    return;
                }

                // Fetch user by username
                Document query = new Document("username", usernameToFine);
                Document userDoc = usersCollection.find(query).first();

                if (userDoc != null) {
                    // Update user's fines
                    Document update = new Document("$inc", new Document("fines", amount));
                    usersCollection.updateOne(query, update);

                    // Broadcast fine imposed via WebSocket
                    JSONObject fineImposed = new JSONObject();
                    fineImposed.put("type", "fineImposed");
                    fineImposed.put("username", usernameToFine);
                    fineImposed.put("amount", amount);
                    SeatWebSocket.broadcast(fineImposed);

                    response.setStatus(HttpServletResponse.SC_OK);
                    JSONObject resp = new JSONObject();
                    resp.put("message", "Fine imposed successfully.");
                    response.setContentType("application/json");
                    response.getWriter().write(resp.toString());
                    logger.info("President imposed a fine of {} on '{}'.", amount, usernameToFine);
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "User to fine not found.");
                    logger.warn("President attempted to impose a fine on non-existent user '{}'.", usernameToFine);
                }
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can impose fines.");
                logger.warn("Non-president attempted to impose a fine.");
            }
        } catch (Exception e) {
            logger.error("Error during imposing fine: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while imposing the fine.");
        }
    }

    // Handle calling a break (President only)
    private void handleCallBreak(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                // Broadcast break notification via WebSocket
                JSONObject breakNotification = new JSONObject();
                breakNotification.put("type", "break");
                SeatWebSocket.broadcast(breakNotification);

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Break has been called.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President called a break.");
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can call a break.");
                logger.warn("Non-president attempted to call a break.");
            }
        } catch (Exception e) {
            logger.error("Error during calling a break: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while calling a break.");
        }
    }

    // Handle ending the session (President only)
    private void handleEndSession(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                // Reset all users' seat status and presence
                Document update = new Document("$set", new Document("seatStatus", "NEUTRAL").append("present", false));
                usersCollection.updateMany(new Document(), update);

                // Broadcast end session notification via WebSocket
                JSONObject endSessionNotification = new JSONObject();
                endSessionNotification.put("type", "endSession");
                SeatWebSocket.broadcast(endSessionNotification);

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Session has been ended.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President ended the session.");
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can end the session.");
                logger.warn("Non-president attempted to end the session.");
            }
        } catch (Exception e) {
            logger.error("Error during ending session: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while ending the session.");
        }
    }

    // Handle fetching all users
    private void handleGetUsers(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<Document> users = usersCollection.find().into(new ArrayList<>());

            JSONArray usersArray = new JSONArray();
            for (Document doc : users) {
                JSONObject userJson = new JSONObject(doc.toJson());
                userJson.remove("password"); // Remove sensitive information
                userJson.put("id", doc.getObjectId("_id").toString());
                usersArray.put(userJson);
            }

            response.setContentType("application/json");
            response.getWriter().write(usersArray.toString());
            logger.info("Fetched all users.");
        } catch (Exception e) {
            logger.error("Error during fetching users: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while fetching users.");
        }
    }

    // Handle fetching logged-in user's info
    private void handleUserInfo(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("username") != null) {
                String username = (String) session.getAttribute("username");
                Document query = new Document("username", username);
                Document userDoc = usersCollection.find(query).first();

                if (userDoc != null) {
                    JSONObject userInfo = new JSONObject();
                    userInfo.put("username", userDoc.getString("username"));
                    userInfo.put("role", userDoc.getString("role"));

                    response.setContentType("application/json");
                    response.getWriter().write(userInfo.toString());
                    logger.info("Fetched user info for '{}'.", username);
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "User not found.");
                    logger.warn("User '{}' not found in the database.", username);
                }
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not authenticated.");
                logger.warn("Unauthenticated attempt to fetch user info.");
            }
        } catch (Exception e) {
            logger.error("Error during fetching user info: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while fetching user info.");
        }
    }

    // Handle fetching all proposals
    private void handleGetProposals(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<Document> proposals = proposalsCollection.find().into(new ArrayList<>());

            JSONArray proposalsArray = new JSONArray();
            for (Document doc : proposals) {
                proposalsArray.put(new JSONObject(doc.toJson()));
            }

            response.setContentType("application/json");
            response.getWriter().write(proposalsArray.toString());
            logger.info("Fetched all proposals.");
        } catch (Exception e) {
            logger.error("Error during fetching proposals: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while fetching proposals.");
        }
    }

    // Handle fetching the speaking queue
    private void handleGetQueue(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            // Fetch users who are requesting to speak or objecting, ordered by some criteria if needed
            List<Document> queueUsers = usersCollection.find(new Document("seatStatus", new Document("$in", List.of("REQUESTING_TO_SPEAK", "OBJECTING"))))
                    .into(new ArrayList<>());

            JSONArray queueArray = new JSONArray();
            for (Document doc : queueUsers) {
                JSONObject queueItem = new JSONObject();
                queueItem.put("username", doc.getString("username"));
                queueItem.put("status", doc.getString("seatStatus"));
                queueArray.put(queueItem);
            }

            response.setContentType("application/json");
            response.getWriter().write(queueArray.toString());
            logger.info("Fetched speaking queue.");
        } catch (Exception e) {
            logger.error("Error during fetching queue: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while fetching the queue.");
        }
    }

    // Helper method to get the next proposal number
    private int getNextProposalNumber() {
        long count = proposalsCollection.countDocuments();
        return (int) (count + 1);
    }
}
