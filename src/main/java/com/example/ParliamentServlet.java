package com.example;

import java.util.stream.Collectors;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId; // Import ObjectId
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import static com.mongodb.client.model.Filters.eq;

public class ParliamentServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(ParliamentServlet.class);

    private final MongoCollection<Document> usersCollection;
    private final MongoCollection<Document> proposalsCollection;
    private final MongoCollection<Document> votesCollection;
    private final MongoCollection<Document> votingLogsCollection;
    private final MongoCollection<Document> fineReasonsCollection;
    private final MongoCollection<Document> systemParametersCollection;

    private String discordWebhookUrl; // Add this variable

    public ParliamentServlet() {
        MongoDatabase database = MongoDBConnection.getDatabase();
        this.usersCollection = database.getCollection("users");
        this.proposalsCollection = database.getCollection("proposals");
        this.votesCollection = database.getCollection("votes");
        this.votingLogsCollection = database.getCollection("votingLogs");
        this.fineReasonsCollection = database.getCollection("fineReasons");
        this.systemParametersCollection = database.getCollection("systemParameters");

        // Ensure break status is initialized if missing
        initializeBreakStatus();

        // Initialize meeting number if missing
        initializeMeetingNumber();

        // Initialize Discord webhook URL
        initializeDiscordWebhookUrl();
    }

    private void initializeDiscordWebhookUrl() {
        // Load Discord webhook URL from environment variable or configuration file
        discordWebhookUrl = System.getenv("DISCORD_WEBHOOK_URL");
        if (discordWebhookUrl == null || discordWebhookUrl.isEmpty()) {
            logger.warn("Discord webhook URL not configured. Messages will not be sent to Discord.");
        }
    }

    private void initializeBreakStatus() {
        Document breakStatus = systemParametersCollection.find(Filters.eq("parameter", "breakStatus")).first();
        if (breakStatus == null) {
            systemParametersCollection.insertOne(new Document("parameter", "breakStatus").append("value", false));
        }
    }

    private void initializeMeetingNumber() {
        Document meetingDoc = systemParametersCollection.find(Filters.eq("parameter", "meetingNumber")).first();
        if (meetingDoc == null) {
            systemParametersCollection.insertOne(new Document("parameter", "meetingNumber").append("value", 1));
        }
    }

    private void setBreakStatus(boolean status) {
        systemParametersCollection.updateOne(
                Filters.eq("parameter", "breakStatus"),
                new Document("$set", new Document("value", status))
        );
    }

    private boolean isBreakActive() {
        Document breakStatus = systemParametersCollection.find(Filters.eq("parameter", "breakStatus")).first();
        return breakStatus != null && breakStatus.getBoolean("value", false);
    }

    private int getCurrentMeetingNumber() {
        Document meetingDoc = systemParametersCollection.find(Filters.eq("parameter", "meetingNumber")).first();
        return meetingDoc != null ? meetingDoc.getInteger("value", 1) : 1;
    }

    private void incrementMeetingNumber() {
        systemParametersCollection.updateOne(
                Filters.eq("parameter", "meetingNumber"),
                new Document("$inc", new Document("value", 1))
        );
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
            case "/proposals/vote":
                handleSubmitVote(request, response);
                break;
            case "/proposals/end-voting":
                handleEndVoting(request, response);
                break;
            case "/impose-fine":
                handleImposeFine(request, response);
                break;
            case "/break":
                handleCallBreak(request, response);
                break;
            case "/end-break":
                handleEndBreak(request, response);
                break;
            case "/end-session":
                handleEndSession(request, response);
                break;
            case "/register":
                handleRegister(request, response);
                break;
            case "/elections/results":
                handleElectionResults(request, response);
                break;
            case "/users/update": // New case for updating users
                handleUpdateUsers(request, response);
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

        if (path != null) {
            if (path.equals("/proposals")) {
                handleGetProposals(request, response);
            } else if (path.startsWith("/proposals/")) {
                handleGetProposalById(request, response);
            } else if (path.equals("/users")) {
                handleGetUsers(request, response);
            } else if (path.startsWith("/users/")) {
                handleGetUserById(request, response);
            } else if (path.equals("/user-info")) {
                handleUserInfo(request, response);
            } else if (path.equals("/queue")) {
                handleGetQueue(request, response);
            } else if (path.equals("/system/break-status")) {
                handleGetBreakStatus(request, response);
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Endpoint not found.");
                logger.warn("Unknown GET endpoint: {}", path);
            }
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Endpoint not found.");
            logger.warn("Unknown GET endpoint: {}", path);
        }
    }

    private void handleGetBreakStatus(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        JSONObject breakStatus = new JSONObject();
        breakStatus.put("breakActive", isBreakActive());
        response.getWriter().write(breakStatus.toString());
    }

    // Updated method to fetch proposal by ID
    private void handleGetProposalById(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String proposalId = request.getPathInfo().split("/")[2]; // Extract proposal ID from path

            // Find the proposal with the given ID
            Document proposal = proposalsCollection.find(eq("_id", new ObjectId(proposalId))).first();

            if (proposal != null) {
                JSONObject proposalJson = new JSONObject(proposal.toJson());

                // Convert '_id' to 'id' and remove '_id'
                String id = proposal.getObjectId("_id").toHexString();
                proposalJson.put("id", id);
                proposalJson.remove("_id");

                response.setContentType("application/json");
                response.getWriter().write(proposalJson.toString());
                logger.info("Fetched proposal with id '{}'.", proposalId);
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Proposal not found.");
                logger.warn("Proposal with id '{}' not found.", proposalId);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid proposal ID format: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid proposal ID format.");
        } catch (Exception e) {
            logger.error("Error during fetching proposal by id: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while fetching the proposal.");
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
                    .append("electoralStrength", 1); // Default electoral strength is 1

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

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();

        if (path.startsWith("/proposals/")) {
            handleDeleteProposal(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Endpoint not found.");
        }
    }

    // Updated method to delete proposal by ID
    private void handleDeleteProposal(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
            String proposalId = request.getPathInfo().split("/")[2]; // Extract proposalId from path

            try {
                // Create a query using the ObjectId
                Document query = new Document("_id", new ObjectId(proposalId));
                proposalsCollection.deleteOne(query);

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Proposal deleted successfully.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President deleted proposal with id '{}'.", proposalId);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid proposal ID format: {}", proposalId);
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid proposal ID format.");
            } catch (Exception e) {
                logger.error("Error deleting proposal: ", e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while deleting the proposal.");
            }
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can delete proposals.");
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();

        if (path.startsWith("/proposals/")) {
            handleUpdateProposal(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Endpoint not found.");
        }
    }

    // Updated method to update proposal by ID
    private void handleUpdateProposal(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
            String proposalId = request.getPathInfo().split("/")[2]; // Extract proposalId from path

            try {
                // Parse JSON payload
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = request.getReader().readLine()) != null) {
                    sb.append(line);
                }
                JSONObject updateData = new JSONObject(sb.toString());

                String title = updateData.optString("title", "").trim();
                String party = updateData.optString("party", "").trim();

                if (title.isEmpty() || party.isEmpty()) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Title and party cannot be empty.");
                    return;
                }

                // Create a query using the ObjectId
                Document query = new Document("_id", new ObjectId(proposalId));
                Document update = new Document("$set", new Document("title", title).append("party", party));
                proposalsCollection.updateOne(query, update);

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Proposal updated successfully.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President updated proposal with id '{}'.", proposalId);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid proposal ID format: {}", proposalId);
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid proposal ID format.");
            } catch (Exception e) {
                logger.error("Error updating proposal: ", e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while updating the proposal.");
            }
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can update proposals.");
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
                    session.setAttribute("userId", userDoc.getObjectId("_id").toHexString());
                    session.setAttribute("electoralStrength", userDoc.getInteger("electoralStrength", 1));

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
                if (username != null) {
                    // Update 'present' to false in the database
                    usersCollection.updateOne(Filters.eq("username", username), Updates.set("present", false));
                }
                session.invalidate();
                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Logged out successfully.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
            } else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No active session.");
            }
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
                Document update = new Document("$set", new Document("present", true).append("seatStatus", "NEUTRAL"));
                usersCollection.updateOne(query, update);

                // Broadcast seat update
                Document userDoc = usersCollection.find(query).first();
                JSONObject userJson = new JSONObject(userDoc.toJson());
                userJson.put("id", userDoc.getObjectId("_id").toHexString());
                userJson.remove("_id");

                JSONObject seatUpdate = new JSONObject();
                seatUpdate.put("type", "seatUpdate");
                seatUpdate.put("user", userJson);
                SeatWebSocket.broadcast(seatUpdate);

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Joined seat successfully.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("User '{}' joined a seat.", username);
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not authenticated.");
                logger.warn("Unauthenticated attempt to join a seat.");
            }
        } catch (Exception e) {
            logger.error("Error during joining seat: ", e);
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

                // Fetch the target user by ID
                Document query = new Document("_id", new ObjectId(userId));
                Document userDoc = usersCollection.find(query).first();

                if (userDoc != null) {
                    String targetUsername = userDoc.getString("username");
                    String currentSeatStatus = userDoc.getString("seatStatus");

                    // Users cannot cancel their own objections unless they are the president
                    if (requesterUsername.equals(targetUsername) && "OBJECTING".equals(currentSeatStatus) && !"PRESIDENT".equals(requesterRole)) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "You cannot cancel your own objection.");
                        logger.warn("User '{}' attempted to cancel their own objection.", requesterUsername);
                        return;
                    }

                    // Only the president can change other users' statuses
                    if (!requesterUsername.equals(targetUsername) && !"PRESIDENT".equals(requesterRole)) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "You can only update your own status.");
                        logger.warn("User '{}' attempted to update status of '{}' without permission.", requesterUsername, targetUsername);
                        return;
                    }

                    // Perform the update
                    Document update = new Document("$set", new Document("seatStatus", newStatus).append("present", true));
                    usersCollection.updateOne(query, update);

                    // Broadcast the update
                    Document updatedUserDoc = usersCollection.find(query).first();
                    JSONObject userJson = new JSONObject(updatedUserDoc.toJson());
                    userJson.put("id", updatedUserDoc.getObjectId("_id").toHexString());
                    userJson.remove("_id");

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
                Boolean priority = proposalJson.optBoolean("priority", false);
                String type = proposalJson.optString("type", "normal").trim();
                String associatedProposal = proposalJson.optString("assProposal").trim();

                int nextProposalNumber = getNextProposalNumber(priority);

                String proposalVisual = (priority ? "P" : "") +
                        nextProposalNumber +
                        (type.equals("additive") ? " → " : type.equals("countering") ? " x " : "") +
                        associatedProposal;

                if (title.isEmpty()) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Proposal title cannot be empty.");
                    logger.warn("President attempted to add a proposal with empty title.");
                    return;
                }

                int meetingNumber = getCurrentMeetingNumber();

                // Save the proposal to the database
                Document proposalDoc = new Document("title", title)
                        .append("proposalNumber", nextProposalNumber)
                        .append("party", party)
                        .append("isPriority", priority)
                        .append("associationType", type)
                        .append("referencedProposal", associatedProposal)
                        .append("proposalVisual", proposalVisual)
                        .append("meetingNumber", meetingNumber)
                        .append("passed", false)
                        .append("totalFor", 0)
                        .append("totalAgainst", 0)
                        .append("votingEnded", false);
                proposalsCollection.insertOne(proposalDoc);

                // Fetch the inserted proposal to get the '_id'
                Document insertedProposal = proposalsCollection.find(eq("proposalNumber", nextProposalNumber)).first();

                if (insertedProposal != null) {
                    JSONObject insertedProposalJson = new JSONObject(insertedProposal.toJson());

                    // Convert '_id' to 'id' and remove '_id'
                    String id = insertedProposal.getObjectId("_id").toHexString();
                    insertedProposalJson.put("id", id);
                    insertedProposalJson.remove("_id");

                    // Broadcast proposal update via WebSocket
                    JSONObject proposalUpdate = new JSONObject();
                    proposalUpdate.put("type", "proposalUpdate");
                    proposalUpdate.put("proposal", insertedProposalJson);
                    SeatWebSocket.broadcast(proposalUpdate);

                    response.setStatus(HttpServletResponse.SC_OK);
                    JSONObject resp = new JSONObject();
                    resp.put("message", "New proposal added successfully.");
                    response.setContentType("application/json");
                    response.getWriter().write(resp.toString());
                    logger.info("President added new proposal: '{}'", title);
                } else {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to retrieve inserted proposal.");
                }
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can add proposals.");
                logger.warn("Non-president attempted to add a new proposal.");
            }
        } catch (Exception e) {
            logger.error("Error during adding new proposal: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while adding the proposal.");
        }
    }

    // Handle submitting a vote
    private void handleSubmitVote(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("username") != null) {
                String username = (String) session.getAttribute("username");
                String userId = (String) session.getAttribute("userId");
                int electoralStrength = (int) session.getAttribute("electoralStrength");

                // Parse JSON from request body
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = request.getReader().readLine()) != null) {
                    sb.append(line);
                }
                JSONObject voteJson = new JSONObject(sb.toString());
                String proposalId = voteJson.getString("proposalId");
                String voteChoice = voteJson.getString("voteChoice"); // "For", "Against", or "Abstain"

                // Validate voteChoice
                if (!Arrays.asList("For", "Against", "Abstain").contains(voteChoice)) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid vote choice.");
                    logger.warn("User '{}' submitted an invalid vote choice '{}'.", username, voteChoice);
                    return;
                }

                // Fetch the proposal
                Document proposal = proposalsCollection.find(eq("_id", new ObjectId(proposalId))).first();
                if (proposal == null || proposal.getBoolean("votingEnded", false)) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid proposal or voting has ended.");
                    return;
                }

                // Prepare the vote record with timestamp
                Document voteRecord = new Document("proposalId", new ObjectId(proposalId))
                        .append("userId", new ObjectId(userId))
                        .append("username", username)
                        .append("voteChoice", voteChoice)
                        .append("electoralStrength", electoralStrength)
                        .append("timestamp", new Date()); // Add timestamp

                // Update or insert the vote
                votesCollection.updateOne(
                        Filters.and(eq("proposalId", new ObjectId(proposalId)), eq("userId", new ObjectId(userId))),
                        new Document("$set", voteRecord),
                        new UpdateOptions().upsert(true)
                );

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Vote submitted successfully.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("User '{}' submitted a vote for proposal '{}'. Vote Choice: '{}'", username, proposalId, voteChoice);
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not authenticated.");
                logger.warn("Unauthenticated attempt to submit a vote.");
            }
        } catch (Exception e) {
            logger.error("Error during submitting vote: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while submitting the vote.");
        }
    }

    private void handleEndVoting(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                // Fetch all proposals where voting hasn't ended
                List<Document> proposals = proposalsCollection.find(eq("votingEnded", false)).into(new ArrayList<>());

                // Fetch all users
                List<Document> allUsers = usersCollection.find().into(new ArrayList<>());

                // Separate NEZ users from others
                List<Document> nezUsers = allUsers.stream()
                        .filter(user -> "NEZ".equalsIgnoreCase(user.getString("partyAffiliation")))
                        .collect(Collectors.toList());

                List<Document> otherUsers = allUsers.stream()
                        .filter(user -> !"NEZ".equalsIgnoreCase(user.getString("partyAffiliation")))
                        .collect(Collectors.toList());

                // Map to hold adjusted electoral strengths per user
                Map<String, Integer> adjustedElectoralStrengths = new HashMap<>();

                // Handle NEZ users: include their electoralStrength if present
                for (Document nezUser : nezUsers) {
                    boolean isPresent = nezUser.getBoolean("present", false);
                    if (isPresent) {
                        String userId = nezUser.getObjectId("_id").toHexString();
                        int electoralStrength = nezUser.getInteger("electoralStrength", 0);
                        adjustedElectoralStrengths.put(userId, electoralStrength);
                        logger.debug("NEZ User: '{}', Electoral Strength: {}", nezUser.getString("username"), electoralStrength);
                    } else {
                        logger.debug("NEZ User: '{}', is absent. Electoral Strength not counted.", nezUser.getString("username"));
                    }
                }

                // Group other users by their partyAffiliation
                Map<String, List<Document>> usersByParty = otherUsers.stream()
                        .collect(Collectors.groupingBy(user -> {
                            String partyAffiliation = user.getString("partyAffiliation");
                            return (partyAffiliation != null && !partyAffiliation.trim().isEmpty()) ? partyAffiliation : "Independent";
                        }));

                // Iterate over each party to perform redistribution within the party
                for (Map.Entry<String, List<Document>> entry : usersByParty.entrySet()) {
                    String party = entry.getKey();
                    List<Document> partyUsers = entry.getValue();

                    // Separate present and absent users within the party
                    List<Document> presentUsers = new ArrayList<>();
                    List<Document> absentUsers = new ArrayList<>();

                    int sumPresent = 0;
                    int sumAbsent = 0;

                    for (Document user : partyUsers) {
                        int electoralStrength = user.getInteger("electoralStrength", 0);
                        if (user.getBoolean("present", false)) {
                            presentUsers.add(user);
                            sumPresent += electoralStrength;
                        } else {
                            absentUsers.add(user);
                            sumAbsent += electoralStrength;
                        }
                    }

                    // Log the current party's present and absent sums
                    logger.debug("Processing Party: '{}', Sum Present: {}, Sum Absent: {}", party, sumPresent, sumAbsent);

                    // Skip redistribution if there are no present users in the party
                    if (sumPresent == 0) {
                        if (sumAbsent > 0) {
                            // Log warning: No present users to redistribute absent weights in this party
                            logger.warn("No present users in party '{}' to redistribute {} absent electoral strength.", party, sumAbsent);
                        }
                        continue;
                    }

                    // Redistribute absent weights to present users within the party
                    for (Document user : presentUsers) {
                        String userId = user.getObjectId("_id").toHexString();
                        int originalWeight = user.getInteger("electoralStrength", 0);
                        double adjustedWeightDouble = originalWeight + ((double) originalWeight * sumAbsent / sumPresent);

                        // Round to the nearest integer
                        int adjustedWeight = (int) Math.round(adjustedWeightDouble);
                        adjustedElectoralStrengths.put(userId, adjustedWeight);

                        // Log the adjusted weight for debugging
                        logger.debug("Party: '{}', User ID: '{}', Original Weight: {}, Adjusted Weight: {}",
                                party, userId, originalWeight, adjustedWeight);
                    }
                }

                // Log the adjusted electoral strengths map for verification
                logger.debug("Adjusted Electoral Strengths: {}", adjustedElectoralStrengths);

                // Process each proposal
                for (Document proposal : proposals) {
                    ObjectId proposalId = proposal.getObjectId("_id");

                    // Fetch all votes for this proposal
                    List<Document> votes = votesCollection.find(eq("proposalId", proposalId)).into(new ArrayList<>());

                    int totalFor = 0;
                    int totalAgainst = 0;
                    int supportersCount = 0;

                    // Prepare a detailed list of votes for logging
                    JSONArray detailedVotes = new JSONArray();

                    for (Document vote : votes) {
                        String voteChoice = vote.getString("voteChoice");
                        String voterId = vote.getObjectId("userId").toHexString();
                        String username = vote.getString("username");
                        int voterAdjustedStrength = adjustedElectoralStrengths.getOrDefault(voterId, 0);
                        Date timestamp = vote.getDate("timestamp");

                        // Log each vote's details
                        logger.debug("Processing Vote - Voter ID: '{}', Username: '{}', Choice: '{}', Adjusted Strength: {}, Timestamp: {}",
                                voterId, username, voteChoice, voterAdjustedStrength, timestamp);

                        // Build detailed vote JSON object
                        JSONObject detailedVote = new JSONObject();
                        detailedVote.put("userId", voterId);
                        detailedVote.put("username", username);
                        detailedVote.put("voteChoice", voteChoice);
                        detailedVote.put("electoralStrength", voterAdjustedStrength);
                        detailedVote.put("timestamp", timestamp.toInstant().toString()); // ISO 8601 format

                        detailedVotes.put(detailedVote);

                        if ("For".equalsIgnoreCase(voteChoice)) {
                            totalFor += voterAdjustedStrength;
                            supportersCount++;
                        } else if ("Against".equalsIgnoreCase(voteChoice)) {
                            totalAgainst += voterAdjustedStrength;
                        }
                        // Abstain votes do not affect totals
                    }

                    // Log the vote totals before determining the outcome
                    logger.debug("Proposal ID: '{}', Total For: {}, Total Against: {}, Supporters Count: {}",
                            proposalId, totalFor, totalAgainst, supportersCount);

                    // Determine if the proposal passed
                    boolean passed = false;
                    if (supportersCount >= 2 && totalFor > totalAgainst) {
                        passed = true;
                    }

                    // Update the proposal with voting results
                    proposalsCollection.updateOne(
                            eq("_id", proposalId),
                            new Document("$set", new Document("passed", passed)
                                    .append("totalFor", totalFor)
                                    .append("totalAgainst", totalAgainst)
                                    .append("votingEnded", true))
                    );

                    // Log the voting results in votingLogsCollection with detailed votes
                    Document votingLog = new Document("proposalId", proposalId)
                            .append("proposalTitle", proposal.getString("title"))
                            .append("meetingNumber", proposal.getInteger("meetingNumber", 1))
                            .append("votes", detailedVotes.toList()) // Insert detailed votes
                            .append("timestamp", new Date());

                    votingLogsCollection.insertOne(votingLog);

                    // Log the outcome of the proposal
                    logger.info("Proposal '{}': For = {}, Against = {}, Passed = {}",
                            proposal.getString("title"), totalFor, totalAgainst, passed);
                }

                // Send the voting results to Discord
                sendVotingResultsToDiscord();

                // Increment meeting number
                incrementMeetingNumber();

                // Prepare and send the response
                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Voting ended and votes counted successfully.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President ended voting and counted votes.");
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can end voting.");
                logger.warn("Unauthorized attempt to end voting.");
            }
        } catch (Exception e) {
            logger.error("Error during ending voting: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while ending voting.");
        }
    }


    // Send voting results to Discord
    private void sendVotingResultsToDiscord() {
        try {
            if (discordWebhookUrl == null || discordWebhookUrl.isEmpty()) {
                logger.warn("Discord webhook URL is not configured. Skipping sending results to Discord.");
                return;
            }

            // Fetch current meeting number
            int meetingNumber = getCurrentMeetingNumber();

            // Fetch all proposals for the current meeting
            List<Document> proposals = proposalsCollection.find(eq("meetingNumber", meetingNumber)).into(new ArrayList<>());

            // Fetch present users
            List<Document> presentUsers = usersCollection.find(eq("present", true)).into(new ArrayList<>());

            // Fetch the chairman (předseda) using role "PRESIDENT"
            Document chairman = usersCollection.find(eq("role", "PRESIDENT")).first();
            String chairmanName = (chairman != null) ? chairman.getString("username") : "N/A";

            // Build the message
            StringBuilder messageBuilder = new StringBuilder();

            // "Jednání: **71**"
            messageBuilder.append("Jednání: **").append(meetingNumber).append("**\n");

            // "Účast: **4**"
            messageBuilder.append("Účast: **").append(presentUsers.size()).append("**\n");

            // "Předseda: **Vitjaaa**"
            messageBuilder.append("Předseda: **").append(chairmanName).append("**\n\n");

            // Next meeting date placeholder
            String nextMeetingDate = "TBD"; // Replace with actual date if available
            messageBuilder.append("Další jednání: ").append(nextMeetingDate).append("\n\n");

            // "Docházka:"
            messageBuilder.append("Docházka:\n");

            // Build attendance list with bolded electoral strength and usernames
            for (Document user : presentUsers) {
                String username = user.getString("username");
                String party = user.getString("partyAffiliation");
                int electoralStrength = user.getInteger("electoralStrength", 0);

                messageBuilder.append("**").append(electoralStrength).append("** - ")
                        .append((party != null && !party.isEmpty()) ? party : "/").append(" - ")
                        .append("**").append(username).append("**\n");
            }
            messageBuilder.append("\n");

            // Build proposals results
            for (Document proposal : proposals) {
                String resultEmoji = proposal.getBoolean("passed", false) ? "✅" : "❌";
                String proposalVisual = proposal.getString("proposalVisual");
                String party = proposal.getString("party");
                String title = proposal.getString("title");

                // Bold proposal number and party, including colon
                String formattedProposalVisual = String.format("**%s %s:**", proposalVisual, (party != null && !party.isEmpty()) ? party : "/");

                messageBuilder.append(String.format("%s %s %s\n", resultEmoji, formattedProposalVisual, title));
            }

            // Fetch fines for the current meeting
            List<Document> fines = fineReasonsCollection.find(eq("meetingNumber", meetingNumber)).into(new ArrayList<>());

            if (!fines.isEmpty()) {
                messageBuilder.append("\nKázeňská opatření:\n");

                class FineBreakdown {
                    int amount;
                    int count;
                    String reason;

                    FineBreakdown(int amount, String reason) {
                        this.amount = amount;
                        this.reason = reason;
                        this.count = 1;
                    }
                }

                // Aggregate fines per user
                Map<String, Integer> userTotalAmounts = new HashMap<>();
                Map<String, List<FineBreakdown>> userFineBreakdowns = new HashMap<>();

                for (Document fine : fines) {
                    String username = fine.getString("username");
                    int amount = fine.getInteger("amount", 0);
                    String reason = fine.getString("reason");

                    // Update total amount
                    userTotalAmounts.put(username, userTotalAmounts.getOrDefault(username, 0) + amount);

                    // Update breakdowns
                    List<FineBreakdown> breakdownList = userFineBreakdowns.get(username);
                    if (breakdownList == null) {
                        breakdownList = new ArrayList<>();
                        userFineBreakdowns.put(username, breakdownList);
                    }

                    // Check if a breakdown with same amount and reason exists
                    boolean found = false;
                    for (FineBreakdown fb : breakdownList) {
                        if (fb.amount == amount && fb.reason.equals(reason)) {
                            fb.count++;
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        breakdownList.add(new FineBreakdown(amount, reason));
                    }
                }

                // Build fines message per user
                for (String username : userTotalAmounts.keySet()) {
                    int totalAmount = userTotalAmounts.get(username);
                    List<FineBreakdown> breakdownList = userFineBreakdowns.get(username);

                    // Build the breakdown string
                    StringBuilder breakdownBuilder = new StringBuilder();
                    breakdownBuilder.append("||(");
                    for (int i = 0; i < breakdownList.size(); i++) {
                        FineBreakdown fb = breakdownList.get(i);
                        breakdownBuilder.append(fb.count).append("x").append(fb.amount).append(" b.ch. - ").append(fb.reason);
                        if (i < breakdownList.size() - 1) {
                            breakdownBuilder.append(", ");
                        }
                    }
                    breakdownBuilder.append(")||");

                    // Append to message
                    messageBuilder.append(String.format("%d b.ch. - %s %s\n", totalAmount, username, breakdownBuilder.toString()));
                }
            }

            // Send the message to Discord
            sendDiscordWebhook(messageBuilder.toString());

            logger.info("Voting results sent to Discord.");
        } catch (Exception e) {
            logger.error("Error during sending voting results to Discord: ", e);
        }
    }

    // Handle entering election results (President only) [Method already exists]
    private void handleElectionResults(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                // Parse JSON from request body
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = request.getReader().readLine()) != null) {
                    sb.append(line);
                }
                JSONArray electionResults = new JSONArray(sb.toString());

                // Update electoral strengths of users
                for (int i = 0; i < electionResults.length(); i++) {
                    JSONObject result = electionResults.getJSONObject(i);
                    String username = result.getString("username");
                    int electoralStrength = result.getInt("electoralStrength");

                    // Update user's electoral strength
                    usersCollection.updateOne(
                            eq("username", username),
                            new Document("$set", new Document("electoralStrength", electoralStrength))
                    );
                }

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Election results processed successfully.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President updated electoral strengths from election results.");
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can submit election results.");
                logger.warn("Unauthorized attempt to submit election results.");
            }
        } catch (Exception e) {
            logger.error("Error during processing election results: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while processing election results.");
        }
    }
    // Handle updating user information (President only)
    private void handleUpdateUsers(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                // Parse JSON array from request body
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = request.getReader().readLine()) != null) {
                    sb.append(line);
                }
                JSONArray userUpdates = new JSONArray(sb.toString());

                for (int i = 0; i < userUpdates.length(); i++) {
                    JSONObject userUpdate = userUpdates.getJSONObject(i);
                    String userId = userUpdate.getString("id");
                    int electoralStrength = userUpdate.optInt("electoralStrength", 1);
                    String partyAffiliation = userUpdate.optString("partyAffiliation", "");
                    String role = userUpdate.optString("role", "MEMBER");

                    // Validate role
                    if (!Arrays.asList("MEMBER", "PRESIDENT", "OTHER_ROLE").contains(role)) {
                        logger.warn("Invalid role '{}' provided for user ID '{}'. Skipping update.", role, userId);
                        continue;
                    }

                    // Update the user document
                    Document updateFields = new Document()
                            .append("electoralStrength", electoralStrength)
                            .append("partyAffiliation", partyAffiliation)
                            .append("role", role);

                    usersCollection.updateOne(
                            eq("_id", new ObjectId(userId)),
                            new Document("$set", updateFields)
                    );
                }

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "User updates processed successfully.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President updated user information.");
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can update user information.");
                logger.warn("Unauthorized attempt to update user information.");
            }
        } catch (Exception e) {
            logger.error("Error during updating user information: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while updating user information.");
        }
    }


    private void sendDiscordWebhook(String content) {
        try {
            URL url = new URL(discordWebhookUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            JSONObject payload = new JSONObject();
            payload.put("content", content);

            String jsonPayload = payload.toString();

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 204) {
                // Discord webhooks return 204 No Content on success
                logger.error("Failed to send Discord webhook. Response code: " + responseCode);
            }

        } catch (Exception e) {
            logger.error("Error sending Discord webhook: ", e);
        }
    }

    // Handle imposing a fine on a user (President only)
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

                // Check for the reason field in JSON payload
                if (!fineJson.has("reason")) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Fine reason is required.");
                    logger.warn("Fine reason missing for username '{}'.", usernameToFine);
                    return;
                }
                String reason = fineJson.getString("reason").trim();

                if (usernameToFine.isEmpty() || amount <= 0 || reason.isEmpty()) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid username, amount, or reason.");
                    logger.warn("President provided invalid data for imposing fine.");
                    return;
                }

                // Fetch user by username
                Document query = new Document("username", usernameToFine);
                Document userDoc = usersCollection.find(query).first();

                if (userDoc != null) {
                    // Update user's fines
                    usersCollection.updateOne(query, Updates.inc("fines", amount));

                    // Generate a unique fineId
                    String fineId = "FINE-" + System.currentTimeMillis();

                    // Fetch current meeting number
                    int meetingNumber = getCurrentMeetingNumber(); // Added this line

                    // Store fine reason in 'fineReasons' collection with meetingNumber
                    Document fineReasonDoc = new Document("fineId", fineId)
                            .append("username", usernameToFine)
                            .append("amount", amount)
                            .append("reason", reason)
                            .append("timestamp", new Date())
                            .append("issuedBy", (String) session.getAttribute("username"))
                            .append("status", "active")
                            .append("meetingNumber", meetingNumber); // Added this line
                    fineReasonsCollection.insertOne(fineReasonDoc);

                    // Broadcast fine imposed via WebSocket
                    JSONObject fineImposed = new JSONObject();
                    fineImposed.put("type", "fineImposed");
                    fineImposed.put("username", usernameToFine);
                    fineImposed.put("amount", amount);
                    fineImposed.put("reason", reason);
                    SeatWebSocket.broadcast(fineImposed);

                    response.setStatus(HttpServletResponse.SC_OK);
                    JSONObject resp = new JSONObject();
                    resp.put("message", "Fine imposed successfully.");
                    response.setContentType("application/json");
                    response.getWriter().write(resp.toString());
                    logger.info("President imposed a fine of {} on '{}'. Reason: {}", amount, usernameToFine, reason);
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "User to fine not found.");
                    logger.warn("President attempted to impose a fine on non-existent user '{}'.", usernameToFine);
                }
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can impose fines.");
                logger.warn("Unauthorized attempt to impose a fine.");
            }
        } catch (Exception e) {
            logger.error("Error during imposing fine: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while imposing the fine.");
        }
    }

    // Handle starting a break (President only)
    private void handleCallBreak(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                setBreakStatus(true);  // Update MongoDB break status

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

    // Handle ending a break (President only)
    private void handleEndBreak(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                setBreakStatus(false);  // Update MongoDB break status

                JSONObject endBreakNotification = new JSONObject();
                endBreakNotification.put("type", "endBreak");
                SeatWebSocket.broadcast(endBreakNotification);

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Break has ended.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President ended the break.");
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can end the break.");
                logger.warn("Non-president attempted to end the break.");
            }
        } catch (Exception e) {
            logger.error("Error during ending the break: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while ending the break.");
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

    // Handle fetching all users (Updated to handle 'present' query parameter)
    private void handleGetUsers(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String presentParam = request.getParameter("present");
            List<Document> users;
            if ("true".equalsIgnoreCase(presentParam)) {
                users = usersCollection.find(eq("present", true)).into(new ArrayList<>());
            } else {
                users = usersCollection.find().into(new ArrayList<>());
            }

            JSONArray usersArray = new JSONArray();
            for (Document doc : users) {
                JSONObject userJson = new JSONObject(doc.toJson());
                userJson.remove("password"); // Remove sensitive information
                userJson.put("id", doc.getObjectId("_id").toHexString());
                userJson.remove("_id");

                // Ensure seatStatus is included
                String seatStatus = doc.getString("seatStatus");
                userJson.put("seatStatus", seatStatus != null ? seatStatus : "NEUTRAL");

                usersArray.put(userJson);
            }

            response.setContentType("application/json");
            response.getWriter().write(usersArray.toString());
            logger.info("Fetched users. Present only: {}", "true".equalsIgnoreCase(presentParam));
        } catch (Exception e) {
            logger.error("Error during fetching users: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while fetching users.");
        }
    }

    private void handleGetUserById(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String userId = request.getPathInfo().split("/")[2];

            Document userDoc = usersCollection.find(eq("_id", new ObjectId(userId))).first();

            if (userDoc != null) {
                JSONObject userJson = new JSONObject(userDoc.toJson());
                userJson.remove("password"); // Remove sensitive information
                userJson.put("id", userDoc.getObjectId("_id").toHexString());
                userJson.remove("_id");

                // Ensure seatStatus is included
                String seatStatus = userDoc.getString("seatStatus");
                userJson.put("seatStatus", seatStatus != null ? seatStatus : "NEUTRAL");

                response.setContentType("application/json");
                response.getWriter().write(userJson.toString());
                logger.info("Fetched user with id '{}'.", userId);
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "User not found.");
                logger.warn("User with id '{}' not found.", userId);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid user ID format: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid user ID format.");
        } catch (Exception e) {
            logger.error("Error during fetching user by id: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while fetching the user.");
        }
    }

    // Handle fetching logged-in user's info
    private void handleUserInfo(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("username") != null) {
            String username = (String) session.getAttribute("username");
            Document userDoc = usersCollection.find(eq("username", username)).first();
            if (userDoc != null) {
                JSONObject userJson = new JSONObject(userDoc.toJson());
                userJson.put("id", userDoc.getObjectId("_id").toHexString());
                userJson.remove("_id");
                userJson.remove("password"); // Remove sensitive data
                response.setContentType("application/json");
                response.getWriter().write(userJson.toString());
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "User not found.");
            }
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not authenticated.");
        }
    }

    // Updated method to fetch all proposals
    private void handleGetProposals(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<Document> proposals = proposalsCollection.find().into(new ArrayList<>());

            // Fetch user's votes to include their vote choice
            HttpSession session = request.getSession(false);
            String userId = null;
            if (session != null && session.getAttribute("userId") != null) {
                userId = (String) session.getAttribute("userId");
            }

            JSONArray proposalsArray = new JSONArray();
            for (Document doc : proposals) {
                JSONObject proposalJson = new JSONObject(doc.toJson());

                // Convert '_id' to 'id' and remove '_id'
                String id = doc.getObjectId("_id").toHexString();
                proposalJson.put("id", id);
                proposalJson.remove("_id");

                // Include voting results if voting has ended
                if (doc.getBoolean("votingEnded", false)) {
                    proposalJson.put("passed", doc.getBoolean("passed", false));
                    proposalJson.put("totalFor", doc.getInteger("totalFor", 0));
                    proposalJson.put("totalAgainst", doc.getInteger("totalAgainst", 0));
                }

                // Include user's vote choice if available
                if (userId != null) {
                    Document vote = votesCollection.find(Filters.and(
                            eq("proposalId", doc.getObjectId("_id")),
                            eq("userId", new ObjectId(userId))
                    )).first();
                    if (vote != null) {
                        proposalJson.put("userVote", vote.getString("voteChoice"));
                    } else {
                        proposalJson.put("userVote", "Abstain");
                    }
                } else {
                    proposalJson.put("userVote", "Abstain");
                }

                proposalsArray.put(proposalJson);
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
    private int getNextProposalNumber(Boolean priority) {
        Bson filter = eq("isPriority", priority);
        int counter = (int) proposalsCollection.countDocuments(filter);
        return counter + 1;
    }
}
