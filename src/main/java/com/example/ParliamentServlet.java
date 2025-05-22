package com.example;

import java.util.stream.Collectors;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.Sorts;
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
import java.util.UUID; // For CSRF token generation

import static com.mongodb.client.model.Filters.eq;

public class ParliamentServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(ParliamentServlet.class);

    private final MongoCollection<Document> usersCollection;
    private final MongoCollection<Document> proposalsCollection;
    private final MongoCollection<Document> votesCollection;
    private final MongoCollection<Document> votingLogsCollection;
    private final MongoCollection<Document> fineReasonsCollection;
    private final MongoCollection<Document> systemParametersCollection;

    private String discordWebhookUrl;

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
            case "/proposals/end-voting-priority":
                handleEndPriorityVoting(request, response);
                break;
            case "/proposals/end-voting-constitutional":
                handleEndVotingConstitutional(request, response);
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
            case "/users/update":
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
            logger.warn("Unknown GET endpoint: path is null");
        }
    }

    private void handleGetBreakStatus(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        JSONObject breakStatus = new JSONObject();
        breakStatus.put("breakActive", isBreakActive());
        response.getWriter().write(breakStatus.toString());
    }

    // Handle proposal by ID
    private void handleGetProposalById(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String proposalId = request.getPathInfo().split("/")[2];

            Document proposal = proposalsCollection.find(eq("_id", new ObjectId(proposalId))).first();
            if (proposal != null) {
                JSONObject proposalJson = new JSONObject(proposal.toJson());

                // Convert '_id' to 'id'
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
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                sb.append(line);
            }
            JSONObject registerJson = new JSONObject(sb.toString());
            String username = registerJson.getString("username").trim();
            String password = registerJson.getString("password").trim();
            String role = registerJson.optString("role", "MEMBER").trim();

            if (username.isEmpty() || password.isEmpty() || role.isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Username, password, and role cannot be empty.");
                logger.warn("Registration attempt with empty fields.");
                return;
            }

            Document existingUser = usersCollection.find(new Document("username", username)).first();
            if (existingUser != null) {
                response.sendError(HttpServletResponse.SC_CONFLICT, "Username already exists.");
                logger.warn("Registration attempt with existing username '{}'.", username);
                return;
            }

            String hashedPassword = MongoDBConnection.hashPassword(password);

            Document newUser = new Document("username", username)
                    .append("password", hashedPassword)
                    .append("role", role.toUpperCase())
                    .append("present", false)
                    .append("seatStatus", "NEUTRAL")
                    .append("fines", 0)
                    .append("partyAffiliation", "")
                    .append("electoralStrength", 1);

            usersCollection.insertOne(newUser);
            logger.info("New user '{}' registered successfully with role '{}'.", username, role);

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

    // Delete proposal by ID
    private void handleDeleteProposal(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
            String proposalIdString = request.getPathInfo().split("/")[2];

            try {
                ObjectId proposalId = new ObjectId(proposalIdString);
                Document query = new Document("_id", proposalId);
                proposalsCollection.deleteOne(query);

                // Broadcast the deletion event
                JSONObject deleteMsg = new JSONObject();
                deleteMsg.put("type", "proposalDelete");
                deleteMsg.put("proposalId", proposalIdString);
                SeatWebSocket.broadcast(deleteMsg);
                logger.info("Broadcasted proposalDelete for id '{}'.", proposalIdString);

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Proposal deleted successfully.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President deleted proposal with id '{}'.", proposalIdString);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid proposal ID format: {}", proposalIdString);
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

    /**
     * Updates an existing proposal. The President can update:
     * - Title
     * - Party
     * - "stupid" flag
     * (Any combination of the above fields if present in JSON).
     */
    private void handleUpdateProposal(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
            String proposalIdString = request.getPathInfo().split("/")[2]; // Renamed for clarity
            ObjectId proposalId = new ObjectId(proposalIdString);

            try {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = request.getReader().readLine()) != null) {
                    sb.append(line);
                }
                JSONObject updateData = new JSONObject(sb.toString());
                Document setFields = new Document();

                if (updateData.has("title")) {
                    String title = updateData.getString("title");
                    if (title.trim().isEmpty()) {
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Proposal title cannot be empty.");
                        return;
                    }
                    setFields.put("title", title);
                }

                if (updateData.has("party")) {
                    String party = updateData.getString("party").trim();
                    if (party.isEmpty()) {
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Party cannot be empty.");
                        return;
                    }
                    setFields.put("party", party);
                }

                if (updateData.has("stupid")) {
                    boolean newStupidValue = updateData.getBoolean("stupid");
                    Document existingProposal = proposalsCollection.find(eq("_id", proposalId)).first();
                    if (existingProposal == null) {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND, "Proposal not found.");
                        return;
                    }
                    boolean oldStupidValue = existingProposal.getBoolean("stupid", false);
                    boolean alreadyEnded = existingProposal.getBoolean("votingEnded", false);
                    if (!oldStupidValue && newStupidValue) {
                        setFields.put("votingEnded", true);
                    } else if (oldStupidValue && !newStupidValue) {
                        if (alreadyEnded) {
                            setFields.put("votingEnded", false);
                        }
                    }
                    setFields.put("stupid", newStupidValue);
                }

                if (setFields.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    JSONObject resp = new JSONObject();
                    resp.put("message", "No fields to update.");
                    response.setContentType("application/json");
                    response.getWriter().write(resp.toString());
                    return;
                }

                Document query = new Document("_id", proposalId);
                Document updateDoc = new Document("$set", setFields);
                proposalsCollection.updateOne(query, updateDoc);

                // Fetch the updated proposal to broadcast its latest state
                Document updatedProposalDoc = proposalsCollection.find(eq("_id", proposalId)).first();
                if (updatedProposalDoc != null) {
                    JSONObject updatedProposalJson = new JSONObject(updatedProposalDoc.toJson());
                    updatedProposalJson.put("id", updatedProposalDoc.getObjectId("_id").toHexString());
                    updatedProposalJson.remove("_id");

                    JSONObject proposalUpdateMsg = new JSONObject();
                    proposalUpdateMsg.put("type", "proposalUpdate");
                    proposalUpdateMsg.put("proposal", updatedProposalJson);
                    SeatWebSocket.broadcast(proposalUpdateMsg);
                    logger.info("Broadcasted proposalUpdate for id '{}' after president update.", proposalIdString);
                }

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Proposal updated successfully.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President updated proposal (id '{}') with data: {}", proposalIdString, setFields.toJson());

            } catch (IllegalArgumentException e) {
                logger.error("Invalid proposal ID format: {}", proposalIdString);
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
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                sb.append(line);
            }
            JSONObject loginJson = new JSONObject(sb.toString());
            String username = loginJson.getString("username").trim();
            String password = loginJson.getString("password").trim();

            if (username.isEmpty() || password.isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Username and password cannot be empty.");
                logger.warn("Login attempt with empty username or password.");
                return;
            }

            Document query = new Document("username", username);
            Document userDoc = usersCollection.find(query).first();

            if (userDoc != null) {
                String hashedPassword = userDoc.getString("password");
                if (MongoDBConnection.verifyPassword(password, hashedPassword)) {
                    HttpSession session = request.getSession(true);
                    session.setAttribute("username", username);
                    session.setAttribute("role", userDoc.getString("role"));
                    session.setAttribute("userId", userDoc.getObjectId("_id").toHexString());
                    session.setAttribute("electoralStrength", userDoc.getInteger("electoralStrength", 1));

                    // Generate and store CSRF token
                    String csrfToken = UUID.randomUUID().toString();
                    session.setAttribute(CsrfFilter.CSRF_TOKEN_SESSION_ATTR_NAME, csrfToken);

                    response.setStatus(HttpServletResponse.SC_OK);
                    JSONObject resp = new JSONObject();
                    resp.put("message", "Login successful.");
                    resp.put(CsrfFilter.CSRF_TOKEN_SESSION_ATTR_NAME, csrfToken); // Send token to client
                    response.setContentType("application/json");
                    response.getWriter().write(resp.toString());
                    logger.info("User '{}' logged in successfully. CSRF token generated.", username);
                    return;
                }
            }

            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid username or password.");
            logger.warn("Failed login attempt for username '{}'.", username);
        } catch (Exception e) {
            logger.error("Error during login: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred during login.");
        }
    }

    private void handleUserInfo(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("username") != null) {
            String username = (String) session.getAttribute("username");
            Document userDoc = usersCollection.find(eq("username", username)).first();
            if (userDoc != null) {
                JSONObject userJson = new JSONObject(userDoc.toJson());
                userJson.put("id", userDoc.getObjectId("_id").toHexString());
                userJson.remove("_id");
                userJson.remove("password"); // remove sensitive data

                // Retrieve CSRF token from session and add to response
                String sessionToken = (String) session.getAttribute(CsrfFilter.CSRF_TOKEN_SESSION_ATTR_NAME);
                if (sessionToken != null) {
                    userJson.put(CsrfFilter.CSRF_TOKEN_HEADER_NAME, sessionToken); // Use consistent key name
                } else {
                    // This case is unlikely if the user is properly logged in via handleLogin,
                    // but log a warning if it happens.
                    logger.warn("CSRF token not found in session for authenticated user '{}' during user-info request.", username);
                }

                response.setContentType("application/json");
                response.getWriter().write(userJson.toString());
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "User not found.");
            }
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not authenticated.");
        }
    }

    // Handle user logout
    private void handleLogout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                String username = (String) session.getAttribute("username");
                if (username != null) {
                    usersCollection.updateOne(Filters.eq("username", username), Updates.set("present", false));
                }
                // Remove CSRF token from session
                session.removeAttribute(CsrfFilter.CSRF_TOKEN_SESSION_ATTR_NAME);
                logger.info("CSRF token removed for user '{}' during logout.", username);

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

    // Update seat status
    private void handleUpdateStatus(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("username") != null) {
                String requesterUsername = (String) session.getAttribute("username");
                String requesterRole = (String) session.getAttribute("role");

                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = request.getReader().readLine()) != null) {
                    sb.append(line);
                }
                JSONObject statusUpdateJson = new JSONObject(sb.toString()); // Renamed for clarity
                String userId = statusUpdateJson.getString("id");
                String newStatus = statusUpdateJson.getString("seatStatus");

                Document query = new Document("_id", new ObjectId(userId));
                Document userDoc = usersCollection.find(query).first();

                if (userDoc != null) {
                    String targetUsername = userDoc.getString("username");
                    String currentSeatStatus = userDoc.getString("seatStatus");

                    if (requesterUsername.equals(targetUsername)
                            && "OBJECTING".equals(currentSeatStatus)
                            && !"PRESIDENT".equals(requesterRole)) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "You cannot cancel your own objection.");
                        logger.warn("User '{}' attempted to cancel their own objection.", requesterUsername);
                        return;
                    }

                    if (!requesterUsername.equals(targetUsername) && !"PRESIDENT".equals(requesterRole)) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "You can only update your own status.");
                        logger.warn("User '{}' attempted to update status of '{}' without permission.", requesterUsername, targetUsername);
                        return;
                    }

                    Document update = new Document("$set", new Document("seatStatus", newStatus).append("present", true));
                    usersCollection.updateOne(query, update);

                    Document updatedUserDoc = usersCollection.find(query).first();
                    JSONObject userJsonForResponse = new JSONObject(updatedUserDoc.toJson());
                    userJsonForResponse.put("id", updatedUserDoc.getObjectId("_id").toHexString());
                    userJsonForResponse.remove("_id");
                    userJsonForResponse.remove("password"); // Ensure password is not sent in HTTP response

                    // Prepare user JSON for WebSocket broadcast (can be the same or slightly different if needed)
                    JSONObject userJsonForBroadcast = new JSONObject(userJsonForResponse.toString()); // Create a copy for broadcast

                    // Broadcast the change via WebSocket
                    JSONObject seatUpdateMsg = new JSONObject();
                    seatUpdateMsg.put("type", "seatUpdate");
                    seatUpdateMsg.put("user", userJsonForBroadcast);
                    SeatWebSocket.broadcast(seatUpdateMsg);
                    logger.info("Broadcasted seatUpdate for user '{}' due to status change.", targetUsername);

                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentType("application/json");
                    response.getWriter().write(userJsonForResponse.toString()); // Return the updated user document
                    logger.info("User '{}' updated seat status of '{}' to '{}'. Returned updated user data.", requesterUsername, targetUsername, newStatus);
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

    // Add a new proposal (President only)
    private void handleNewProposal(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = request.getReader().readLine()) != null) {
                    sb.append(line);
                }
                JSONObject proposalJson = new JSONObject(sb.toString());
                String title = proposalJson.getString("title");
                String party = proposalJson.optString("party", "President").trim();
                boolean priority = proposalJson.optBoolean("priority", false);
                // NEW: Read the constitutional flag and vote requirement
                boolean constitutional = proposalJson.optBoolean("constitutional", false);
                String voteRequirement = proposalJson.optString("voteRequirement", "Rel").trim();
                String type = proposalJson.optString("type", "normal").trim();
                String associatedProposal = proposalJson.optString("assProposal", "").trim();

                int proposalNumber;
                String proposalVisual;
                if (constitutional) {
                    proposalNumber = getNextConstitutionalProposalNumber();
                    proposalVisual = "C" + proposalNumber;
                } else if (priority) {
                    proposalNumber = getNextProposalNumber(true);
                    proposalVisual = "P" + proposalNumber;
                } else {
                    proposalNumber = getNextProposalNumber(false);
                    proposalVisual = String.valueOf(proposalNumber);
                }
                if (!type.equals("normal")) {
                    proposalVisual += (type.equals("additive") ? " → " : type.equals("countering") ? " x " : "") + associatedProposal;
                }

                if (title.trim().isEmpty()) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Proposal title cannot be empty.");
                    logger.warn("President attempted to add a proposal with empty title.");
                    return;
                }

                int meetingNumber = getCurrentMeetingNumber();

                Document proposalDoc = new Document("title", title)
                        .append("proposalNumber", proposalNumber)
                        .append("party", party)
                        .append("isPriority", priority)
                        // NEW: Add the constitutional flag and vote requirement field
                        .append("isConstitutional", constitutional)
                        .append("voteRequirement", voteRequirement)
                        .append("stupid", false)
                        .append("associationType", type)
                        .append("referencedProposal", associatedProposal)
                        .append("proposalVisual", proposalVisual)
                        .append("meetingNumber", meetingNumber)
                        .append("passed", false)
                        .append("totalFor", 0)
                        .append("totalAgainst", 0)
                        .append("votingEnded", false);
                proposalsCollection.insertOne(proposalDoc);

                Document insertedProposal = proposalsCollection.find(Filters.eq("proposalNumber", proposalNumber)).first();
                if (insertedProposal != null) {
                    JSONObject insertedProposalJson = new JSONObject(insertedProposal.toJson());
                    String id = insertedProposal.getObjectId("_id").toHexString();
                    insertedProposalJson.put("id", id);
                    insertedProposalJson.remove("_id");

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

    private int getNextConstitutionalProposalNumber() {
        Document lastProposal = proposalsCollection.find(Filters.eq("isConstitutional", true))
                .sort(Sorts.descending("proposalNumber"))
                .limit(1)
                .first();
        if (lastProposal == null) {
            return 1;
        } else {
            int lastNum = lastProposal.getInteger("proposalNumber", 0);
            return lastNum + 1;
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

                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = request.getReader().readLine()) != null) {
                    sb.append(line);
                }
                JSONObject voteJson = new JSONObject(sb.toString());
                String proposalId = voteJson.getString("proposalId");
                String voteChoice = voteJson.getString("voteChoice");

                if (!Arrays.asList("For", "Against", "Abstain").contains(voteChoice)) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid vote choice.");
                    logger.warn("User '{}' submitted an invalid vote choice '{}'.", username, voteChoice);
                    return;
                }

                Document proposal = proposalsCollection.find(eq("_id", new ObjectId(proposalId))).first();
                if (proposal == null || proposal.getBoolean("votingEnded", false)) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid proposal or voting has ended.");
                    return;
                }
                // If it's stupid => no voting
                if (proposal.getBoolean("stupid", false)) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "This proposal is marked as stupid; no voting allowed.");
                    return;
                }

                Document voteRecord = new Document("proposalId", new ObjectId(proposalId))
                        .append("userId", new ObjectId(userId))
                        .append("username", username)
                        .append("voteChoice", voteChoice)
                        .append("electoralStrength", electoralStrength)
                        .append("timestamp", new Date());

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
                logger.info("User '{}' voted on proposal '{}': '{}'", username, proposalId, voteChoice);
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not authenticated.");
                logger.warn("Unauthenticated attempt to submit a vote.");
            }
        } catch (Exception e) {
            logger.error("Error during submitting vote: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while submitting the vote.");
        }
    }

    /**
     * Ends voting for normal proposals (isPriority=false, stupid=false).
     * Then sends all ended proposals to Discord (priority first, then normal).
     */
    private void handleEndVoting(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                List<Document> proposals = proposalsCollection.find(
                        Filters.and(
                                Filters.eq("votingEnded", false),
                                Filters.eq("isPriority", false),
                                Filters.eq("isConstitutional", false),
                                Filters.eq("stupid", false)
                        )
                ).into(new ArrayList<>());

                if (proposals.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    JSONObject resp = new JSONObject();
                    resp.put("message", "No normal proposals to end. Possibly only priority, constitutional, or stupid proposals remain.");
                    response.setContentType("application/json");
                    response.getWriter().write(resp.toString());
                    return;
                }

                Map<String, Integer> adjustedMap = computeAdjustedElectoralStrengths();
                for (Document proposal : proposals) {
                    endProposalVoting(proposal, adjustedMap);
                }
                
                // Broadcast that proposal states have been updated
                JSONObject updateMsg = new JSONObject().put("type", "proposalsUpdated");
                SeatWebSocket.broadcast(updateMsg);
                logger.info("Broadcasted proposalsUpdated after ending normal voting.");

                sendVotingResultsToDiscord();
                incrementMeetingNumber();

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Voting ended for normal proposals and votes counted successfully.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President ended normal proposals' voting.");
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can end voting.");
            }
        } catch (Exception e) {
            logger.error("Error during ending voting: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while ending voting.");
        }
    }

    /**
     * Ends voting for priority proposals (isPriority=true, stupid=false).
     * No Discord message is sent here—only in handleEndVoting.
     */
    private void handleEndPriorityVoting(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                List<Document> proposals = proposalsCollection.find(
                        Filters.and(
                                Filters.eq("votingEnded", false),
                                Filters.eq("isPriority", true),
                                Filters.eq("stupid", false)
                        )
                ).into(new ArrayList<>());

                if (proposals.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    JSONObject resp = new JSONObject();
                    resp.put("message", "No priority proposals to end, or they might be marked stupid.");
                    response.setContentType("application/json");
                    response.getWriter().write(resp.toString());
                    return;
                }

                Map<String, Integer> adjustedMap = computeAdjustedElectoralStrengths();
                for (Document proposal : proposals) {
                    endProposalVoting(proposal, adjustedMap);
                }

                // Broadcast that proposal states have been updated
                JSONObject updateMsg = new JSONObject().put("type", "proposalsUpdated");
                SeatWebSocket.broadcast(updateMsg);
                logger.info("Broadcasted proposalsUpdated after ending priority voting.");

                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Priority voting ended. Votes counted, no Discord message yet.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President ended priority proposals' voting.");
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can end priority voting.");
            }
        } catch (Exception e) {
            logger.error("Error during ending priority voting: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while ending priority voting.");
        }
    }

    private void handleEndVotingConstitutional(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                List<Document> proposals = proposalsCollection.find(
                        Filters.and(
                                Filters.eq("votingEnded", false),
                                Filters.eq("isConstitutional", true),
                                Filters.eq("stupid", false)
                        )
                ).into(new ArrayList<>());
                if (proposals.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    JSONObject resp = new JSONObject();
                    resp.put("message", "No constitutional proposals to end, or they might be marked stupid.");
                    response.setContentType("application/json");
                    response.getWriter().write(resp.toString());
                    return;
                }

                Map<String, Integer> adjustedMap = computeAdjustedElectoralStrengths();
                for (Document proposal : proposals) {
                    endProposalVoting(proposal, adjustedMap);
                }

                // Broadcast that proposal states have been updated
                JSONObject updateMsg = new JSONObject().put("type", "proposalsUpdated");
                SeatWebSocket.broadcast(updateMsg);
                logger.info("Broadcasted proposalsUpdated after ending constitutional voting.");
                
                response.setStatus(HttpServletResponse.SC_OK);
                JSONObject resp = new JSONObject();
                resp.put("message", "Constitutional voting ended. Votes counted.");
                response.setContentType("application/json");
                response.getWriter().write(resp.toString());
                logger.info("President ended constitutional proposals' voting.");
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Only the president can end constitutional voting.");
            }
        } catch (Exception e) {
            logger.error("Error during ending constitutional voting: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while ending constitutional voting.");
        }
    }

    private void endProposalVoting(Document proposal, Map<String, Integer> adjustedMap) {
        ObjectId proposalId = proposal.getObjectId("_id");
        List<Document> votes = votesCollection.find(Filters.eq("proposalId", proposalId)).into(new ArrayList<>());
        int totalFor = 0;
        int totalAgainst = 0;
        int supportersCount = 0;
        JSONArray detailedVotes = new JSONArray();

        for (Document vote : votes) {
            String voteChoice = vote.getString("voteChoice");
            String voterId = vote.getObjectId("userId").toHexString();
            String username = vote.getString("username");
            int voterAdjustedStrength = adjustedMap.getOrDefault(voterId, 0);
            Date timestamp = vote.getDate("timestamp");
            JSONObject detailedVote = new JSONObject();
            detailedVote.put("userId", voterId);
            detailedVote.put("username", username);
            detailedVote.put("voteChoice", voteChoice);
            detailedVote.put("electoralStrength", voterAdjustedStrength);
            if (timestamp != null) {
                detailedVote.put("timestamp", timestamp.toInstant().toString());
            }
            detailedVotes.put(detailedVote);

            if ("For".equalsIgnoreCase(voteChoice)) {
                totalFor += voterAdjustedStrength;
                supportersCount++;
            } else if ("Against".equalsIgnoreCase(voteChoice)) {
                totalAgainst += voterAdjustedStrength;
            }
        }

        boolean passed = false;
        String voteRequirement = proposal.getString("voteRequirement");
        if (voteRequirement == null) {
            voteRequirement = "Rel";
        }
        if ("Rel".equals(voteRequirement)) {
            // Rel: More For than Against and at least 2 For votes
            passed = (supportersCount >= 2 && totalFor > totalAgainst);
        } else {
            double ratio = 0.0;
            boolean useTotal = voteRequirement.endsWith("+");
            if (voteRequirement.startsWith("2/3")) {
                ratio = 2.0 / 3.0;
            } else if (voteRequirement.startsWith("3/5")) {
                ratio = 3.0 / 5.0;
            } else if (voteRequirement.startsWith("1/2")) {
                ratio = 1.0 / 2.0;
            }
            double denominator = useTotal ? getTotalElectoralStrength() : getTotalPresentElectoralStrength(adjustedMap);
            passed = (totalFor > ratio * denominator);
        }

        proposalsCollection.updateOne(
                Filters.eq("_id", proposalId),
                new Document("$set", new Document("passed", passed)
                        .append("totalFor", totalFor)
                        .append("totalAgainst", totalAgainst)
                        .append("votingEnded", true))
        );

        Document votingLog = new Document("proposalId", proposalId)
                .append("proposalTitle", proposal.getString("title"))
                .append("meetingNumber", proposal.getInteger("meetingNumber", 1))
                .append("votes", detailedVotes.toList())
                .append("timestamp", new Date());
        votingLogsCollection.insertOne(votingLog);
        logger.info("Proposal '{}': For = {}, Against = {}, Passed = {}",
                proposal.getString("title"), totalFor, totalAgainst, passed);
    }

    private int getTotalElectoralStrength() {
        List<Document> allUsers = usersCollection.find().into(new ArrayList<>());
        int sum = 0;
        for (Document u : allUsers) {
            sum += u.getInteger("electoralStrength", 0);
        }
        return sum;
    }

    private int getTotalPresentElectoralStrength(Map<String, Integer> adjustedMap) {
        int sum = 0;
        for (Integer strength : adjustedMap.values()) {
            sum += strength;
        }
        return sum;
    }

    private Map<String, Integer> computeAdjustedElectoralStrengths() {
        List<Document> allUsers = usersCollection.find().into(new ArrayList<>());

        List<Document> nezUsers = allUsers.stream()
                .filter(u -> "NEZ".equalsIgnoreCase(u.getString("partyAffiliation")))
                .collect(Collectors.toList());
        List<Document> otherUsers = allUsers.stream()
                .filter(u -> !"NEZ".equalsIgnoreCase(u.getString("partyAffiliation")))
                .collect(Collectors.toList());

        Map<String, Integer> adjustedMap = new HashMap<>();

        // NEZ users just keep their strength if present
        for (Document nez : nezUsers) {
            if (nez.getBoolean("present", false)) {
                String userId = nez.getObjectId("_id").toHexString();
                int es = nez.getInteger("electoralStrength", 0);
                adjustedMap.put(userId, es);
            }
        }

        // group others by party
        Map<String, List<Document>> usersByParty = otherUsers.stream()
                .collect(Collectors.groupingBy(u -> {
                    String partyAff = u.getString("partyAffiliation");
                    return (partyAff != null && !partyAff.trim().isEmpty()) ? partyAff : "Independent";
                }));

        for (Map.Entry<String, List<Document>> entry : usersByParty.entrySet()) {
            String party = entry.getKey();
            List<Document> partyUsers = entry.getValue();

            List<Document> presentUsers = new ArrayList<>();
            int sumPresent = 0;
            int sumAbsent = 0;

            for (Document user : partyUsers) {
                int es = user.getInteger("electoralStrength", 0);
                if (user.getBoolean("present", false)) {
                    presentUsers.add(user);
                    sumPresent += es;
                } else {
                    sumAbsent += es;
                }
            }

            if (sumPresent == 0) continue;

            for (Document pUser : presentUsers) {
                String userId = pUser.getObjectId("_id").toHexString();
                int original = pUser.getInteger("electoralStrength", 0);
                double adjustedDouble = original + ((double) original * sumAbsent / sumPresent);
                int adjusted = (int) Math.round(adjustedDouble);
                adjustedMap.put(userId, adjusted);
            }
        }

        return adjustedMap;
    }

    // Send Discord results with strikethrough for stupid proposals
    private void sendVotingResultsToDiscord() {
        try {
            if (discordWebhookUrl == null || discordWebhookUrl.isEmpty()) {
                logger.warn("Discord webhook URL is not configured. Skipping sending results to Discord.");
                return;
            }

            int meetingNumber = getCurrentMeetingNumber();

            // Fetch proposals from the DB which have ended voting in this meeting
            List<Document> endedProposals = proposalsCollection.find(
                    Filters.and(
                            Filters.eq("meetingNumber", meetingNumber),
                            Filters.eq("votingEnded", true)
                    )
            ).into(new ArrayList<>());

            // Separate them into priority vs. normal
            List<Document> priorityProposals = new ArrayList<>();
            List<Document> normalProposals = new ArrayList<>();
            for (Document p : endedProposals) {
                if (p.getBoolean("isPriority", false)) {
                    priorityProposals.add(p);
                } else {
                    normalProposals.add(p);
                }
            }

            // Fetch present users
            List<Document> presentUsers = usersCollection.find(eq("present", true)).into(new ArrayList<>());
            // Fetch the chairman/president
            Document chairman = usersCollection.find(eq("role", "PRESIDENT")).first();
            String chairmanName = (chairman != null) ? chairman.getString("username") : "N/A";

            // -----------------------
            // Build the main message
            // -----------------------
            StringBuilder msg = new StringBuilder();

            // "Jednání: **75**"
            msg.append("Jednání: **").append(meetingNumber).append("**\n");
            // "Účast: **2**"
            msg.append("Účast: **").append(presentUsers.size()).append("**\n");
            // "Předseda: **GeorgeH7**"
            msg.append("Předseda: **").append(chairmanName).append("**\n\n");

            // "Docházka:"
            msg.append("Docházka:\n");
            for (Document user : presentUsers) {
                String username = user.getString("username");
                String party = user.getString("partyAffiliation");
                int es = user.getInteger("electoralStrength", 0);

                // e.g. "**32** - MNSB - **PanNuggetek**"
                msg.append("**").append(es).append("** - ")
                        .append((party != null && !party.isEmpty()) ? party : "/")
                        .append(" - **").append(username).append("**\n");
            }
            msg.append("\n");

            // ---------------------------------------
            // Priority proposals first (no "PRIORITY" label in text)
            // ---------------------------------------
            for (Document p : priorityProposals) {
                boolean passed = p.getBoolean("passed", false);
                int totalFor = p.getInteger("totalFor", 0);
                int totalAgainst = p.getInteger("totalAgainst", 0);

                String resultEmoji = passed ? "✅" : "❌";
                String proposalVisual = p.getString("proposalVisual"); // e.g. "P1", "P2", etc.
                String party = p.getString("party");
                String title = p.getString("title");
                boolean isStupid = p.getBoolean("stupid", false);

                // If "stupid", apply strikethrough
                if (isStupid) {
                    title = "~~" + title + "~~";
                }

                // e.g.: "✅ **P1 VSP:** Title..."
                msg.append(resultEmoji).append(" **")
                        .append(proposalVisual).append(" ")
                        .append((party != null && !party.isEmpty()) ? party : "/")
                        .append(":** ")
                        .append(title)
                        .append("\n");
            }

            // ---------------------------------------
            // Normal proposals next
            // ---------------------------------------
            for (Document p : normalProposals) {
                boolean passed = p.getBoolean("passed", false);
                int totalFor = p.getInteger("totalFor", 0);
                int totalAgainst = p.getInteger("totalAgainst", 0);

                String resultEmoji = passed ? "✅" : "❌";
                String proposalVisual = p.getString("proposalVisual"); // e.g. "01", "02", etc.
                String party = p.getString("party");
                String title = p.getString("title");
                boolean isStupid = p.getBoolean("stupid", false);

                if (isStupid) {
                    title = "~~" + title + "~~";
                }

                // e.g.: "✅ **01 VSP:** Title..."
                msg.append(resultEmoji).append(" **")
                        .append(proposalVisual).append(" ")
                        .append((party != null && !party.isEmpty()) ? party : "/")
                        .append(":** ")
                        .append(title)
                        .append("\n");
            }

            // ---------------------------------------------------
            // Fines / Kázeňská opatření (optional, example logic)
            // ---------------------------------------------------
            msg.append("\nKázeňská opatření:\n");

            // If you have logic to fetch the fines for this meeting, do it here:
            List<Document> meetingFines = fineReasonsCollection.find(eq("meetingNumber", meetingNumber)).into(new ArrayList<>());

            if (meetingFines.isEmpty()) {
                // If no fines, print "--"
                msg.append("--\n");
            } else {
                // Example: "50 b.ch. - PanNuggetek (Reason: disruption)"
                for (Document fineDoc : meetingFines) {
                    String finedUser = fineDoc.getString("username");
                    int amount = fineDoc.getInteger("amount", 0);
                    String reason = fineDoc.getString("reason");
                    msg.append(amount).append(" b.ch. - ").append(finedUser)
                            .append(" (").append(reason).append(")\n");
                }
            }

            // -----------------------------------
            // Send final message to Discord
            // -----------------------------------
            sendDiscordWebhook(msg.toString());
            logger.info("Voting results sent to Discord using updated formatting.");

        } catch (Exception e) {
            logger.error("Error during sending voting results to Discord: ", e);
        }
    }

    private void sendDiscordWebhook(String content) {
        try {
            if (discordWebhookUrl == null || discordWebhookUrl.isEmpty()) {
                logger.warn("Discord webhook URL is not configured. Skipping sending results to Discord.");
                return;
            }

            List<String> chunks = splitContentIntoChunks(content);
            for (String chunk : chunks) {
                sendDiscordWebhookChunk(chunk);
            }
        } catch (Exception e) {
            logger.error("Error sending Discord webhook: ", e);
        }
    }

    private void sendDiscordWebhookChunk(String content) {
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
                logger.error("Failed to send Discord webhook. Response code: " + responseCode);
            }

        } catch (Exception e) {
            logger.error("Error sending Discord webhook: ", e);
        }
    }

    private List<String> splitContentIntoChunks(String content) {
        List<String> chunks = new ArrayList<>();
        String[] lines = content.split("\n");
        StringBuilder currentChunk = new StringBuilder();

        for (String line : lines) {
            if (currentChunk.length() + line.length() + 1 <= 2000) {
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n");
                }
                currentChunk.append(line);
            } else {
                if (line.length() > 2000) {
                    if (currentChunk.length() > 0) {
                        chunks.add(currentChunk.toString());
                        currentChunk = new StringBuilder();
                    }
                    List<String> splitLines = splitLineIntoChunks(line, 2000);
                    chunks.addAll(splitLines);
                } else {
                    if (currentChunk.length() > 0) {
                        chunks.add(currentChunk.toString());
                        currentChunk = new StringBuilder();
                    }
                    currentChunk.append(line);
                }
            }
        }
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }
        return chunks;
    }

    private List<String> splitLineIntoChunks(String line, int maxChunkSize) {
        List<String> chunks = new ArrayList<>();
        if (line.length() <= maxChunkSize) {
            chunks.add(line);
        } else {
            String[] words = line.split(" ");
            StringBuilder currentChunk = new StringBuilder();
            for (String word : words) {
                if (currentChunk.length() + word.length() + 1 <= maxChunkSize) {
                    if (currentChunk.length() > 0) {
                        currentChunk.append(" ");
                    }
                    currentChunk.append(word);
                } else {
                    if (currentChunk.length() > 0) {
                        chunks.add(currentChunk.toString());
                        currentChunk = new StringBuilder();
                    }
                    if (word.length() > maxChunkSize) {
                        int index = 0;
                        while (index < word.length()) {
                            int endIndex = Math.min(index + maxChunkSize, word.length());
                            chunks.add(word.substring(index, endIndex));
                            index = endIndex;
                        }
                    } else {
                        currentChunk.append(word);
                    }
                }
            }
            if (currentChunk.length() > 0) {
                chunks.add(currentChunk.toString());
            }
        }
        return chunks;
    }

    // Handle entering election results (President only)
    private void handleElectionResults(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = request.getReader().readLine()) != null) {
                    sb.append(line);
                }
                JSONArray electionResults = new JSONArray(sb.toString());

                for (int i = 0; i < electionResults.length(); i++) {
                    JSONObject result = electionResults.getJSONObject(i);
                    String username = result.getString("username");
                    int electoralStrength = result.getInt("electoralStrength");

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

                    if (!Arrays.asList("MEMBER", "PRESIDENT", "OTHER_ROLE").contains(role)) {
                        logger.warn("Invalid role '{}' for user ID '{}'. Skipping update.", role, userId);
                        continue;
                    }

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

    // Handle imposing a fine on a user (President only)
    private void handleImposeFine(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = request.getReader().readLine()) != null) {
                    sb.append(line);
                }
                JSONObject fineJson = new JSONObject(sb.toString());
                String usernameToFine = fineJson.getString("username").trim();
                int amount = fineJson.getInt("amount");

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

                Document query = new Document("username", usernameToFine);
                Document userDoc = usersCollection.find(query).first();

                if (userDoc != null) {
                    usersCollection.updateOne(query, Updates.inc("fines", amount));

                    String fineId = "FINE-" + System.currentTimeMillis();
                    int meetingNumber = getCurrentMeetingNumber();

                    Document fineReasonDoc = new Document("fineId", fineId)
                            .append("username", usernameToFine)
                            .append("amount", amount)
                            .append("reason", reason)
                            .append("timestamp", new Date())
                            .append("issuedBy", (String) session.getAttribute("username"))
                            .append("status", "active")
                            .append("meetingNumber", meetingNumber);
                    fineReasonsCollection.insertOne(fineReasonDoc);

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

    // Handle calling a break (President only)
    private void handleCallBreak(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                setBreakStatus(true);

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

    // Handle ending a break
    private void handleEndBreak(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                setBreakStatus(false);

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

    // Handle ending the session
    private void handleEndSession(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && "PRESIDENT".equals(session.getAttribute("role"))) {
                Document update = new Document("$set", new Document("seatStatus", "NEUTRAL").append("present", false));
                usersCollection.updateMany(new Document(), update);

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

    // Fetch all users (updated to handle present parameter)
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
                userJson.remove("password");
                userJson.put("id", doc.getObjectId("_id").toHexString());
                userJson.remove("_id");

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
                userJson.remove("password");
                userJson.put("id", userDoc.getObjectId("_id").toHexString());
                userJson.remove("_id");

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

    // Fetch all proposals
    private void handleGetProposals(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<Document> proposals = proposalsCollection.find().into(new ArrayList<>());

            HttpSession session = request.getSession(false);
            String userId = null;
            if (session != null && session.getAttribute("userId") != null) {
                userId = (String) session.getAttribute("userId");
            }

            JSONArray proposalsArray = new JSONArray();
            for (Document doc : proposals) {
                JSONObject proposalJson = new JSONObject(doc.toJson());

                String id = doc.getObjectId("_id").toHexString();
                proposalJson.put("id", id);
                proposalJson.remove("_id");

                if (doc.getBoolean("votingEnded", false)) {
                    proposalJson.put("passed", doc.getBoolean("passed", false));
                    proposalJson.put("totalFor", doc.getInteger("totalFor", 0));
                    proposalJson.put("totalAgainst", doc.getInteger("totalAgainst", 0));
                }

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

    // Get the speaking queue
    private void handleGetQueue(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<Document> queueUsers = usersCollection.find(new Document("seatStatus",
                            new Document("$in", List.of("REQUESTING_TO_SPEAK", "OBJECTING"))))
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

    // Helper to get next proposal number
    private int getNextProposalNumber(Boolean priority) {
        // We'll only look at proposals with isPriority == `priority`.
        // Sort them descending by "proposalNumber" to find the largest used number.
        Document lastProposal = proposalsCollection
                .find(eq("isPriority", priority))
                .sort(Sorts.descending("proposalNumber"))
                .limit(1)
                .first();

        if (lastProposal == null) {
            // No proposals exist in this category (priority/normal) yet, start at 1
            return 1;
        } else {
            int lastNum = lastProposal.getInteger("proposalNumber", 0);
            // Next free number
            return lastNum + 1;
        }
    }
}
