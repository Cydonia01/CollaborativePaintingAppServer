package project;

import java.net.*;
import java.io.*;
import java.util.*;

/**
 * Distributed collaborative painting server with mutual exclusion.
 * 
 * <p>Key Features:
 * <ul>
 *   <li>Handles multiple concurrent clients via TCP sockets</li>
 *   <li>Implements Lamport clock-based mutual exclusion for paint operations</li>
 *   <li>Maintains persistent canvas state with auto-save capability</li>
 *   <li>Provides admin commands for server monitoring and control</li>
 *   <li>Resolves conflicts using timestamp ordering and lexicographic tie-breaking</li>
 * </ul>
 */
public class Server {
    // Constants
    private static final String STATE_FILE = "canvas_state.txt";
    private static final long AUTO_SAVE_INTERVAL_MS = 10_000; // Auto-save every 10 seconds
    private static final int PAINT_COUNT_THRESHOLD = 5; // Save after every 5 paint operations
    
    // Message type constants
    private static final String MSG_PAINT = "|PAINT|";
    private static final String MSG_JOIN = "|JOIN|";
    private static final String MSG_LEAVE = "|LEAVE|";
    private static final String MSG_ADMIN = "|ADMIN|";
    private static final String MSG_SYNC_DATA = "SYNC_DATA";
    private static final String MSG_SYNC_COMPLETE = "SYNC_COMPLETE";
    private static final String MSG_INFO = "INFO";
    private static final String MSG_CLEAR = "CLEAR";
    
    // Network configuration
    private ServerSocket serverSocket;
    private final int port;
    private volatile boolean running;
    
    // Client management
    private final List<ClientHandler> clients;
    private final Mutex clientsLock;
    private int nextClientId;
    private final Mutex nextClientIdLock;
    
    // Lamport clock for logical timestamps
    private final LamportClock lamportClock;
    
    // Priority queue for paint requests ordered by timestamp
    private final PriorityQueue<PaintRequest> requestQueue;
    private final Mutex queueLock;
    
    // Pixel states with timestamp-based conflict resolution
    private final Map<String, PixelState> pixelStates;
    private final Mutex pixelStatesLock;
    
    // State persistence
    private long lastSaveTime;
    private final Mutex lastSaveTimeLock;
    private int paintCountSinceLastSave;
    private final Mutex paintCountLock;
    
    // Statistics
    private int totalPaints;
    private final Mutex totalPaintsLock;
    private int totalConflicts;
    private final Mutex totalConflictsLock;
    private int totalRejected;
    private final Mutex totalRejectedLock;
    private final long serverStartTime;
    
    /**
     * Creates a new collaborative painting server on the specified port.
     * Initializes all data structures and statistics counters.
     * 
     * @param port the TCP port number to listen on for client connections
     */
    public Server(int port) {
        this.port = port;
        
        // Initialize regular collections
        this.clients = new ArrayList<>();
        this.requestQueue = new PriorityQueue<>();
        this.pixelStates = new HashMap<>();
        
        // Initialize Lamport clock
        this.lamportClock = new LamportClock();
        
        // Initialize custom mutex locks for manual synchronization
        this.clientsLock = new Mutex();
        this.nextClientIdLock = new Mutex();
        this.queueLock = new Mutex();
        this.pixelStatesLock = new Mutex();
        this.lastSaveTimeLock = new Mutex();
        this.paintCountLock = new Mutex();
        this.totalPaintsLock = new Mutex();
        this.totalConflictsLock = new Mutex();
        this.totalRejectedLock = new Mutex();
        
        // Initialize primitive values
        this.running = false;
        this.nextClientId = 0;
        this.lastSaveTime = 0;
        this.paintCountSinceLastSave = 0;
        this.totalPaints = 0;
        this.totalConflicts = 0;
        this.totalRejected = 0;
        this.serverStartTime = System.currentTimeMillis();
    }

    /**
     * Starts the server and begins accepting client connections.
     * 
     * @throws IOException if the server socket cannot be created
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        
        // Load saved canvas state
        loadState();
        
        // Start mutual exclusion worker thread
        Thread workerThread = new Thread(this::processPaintRequests, "MutualExclusion-Worker");
        workerThread.setDaemon(false);
        workerThread.start();
        
        Logger.log("TCP Server started on port " + port);
        Logger.log("Waiting for clients...");

        acceptClientConnections();
    }
    
    /**
     * Accepts incoming client connections in a loop.
     */
    private void acceptClientConnections() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                
                // Generate client ID with custom mutex
                int clientId = nextClientIdLock.withLock(() -> ++nextClientId);
                
                Logger.info("Assigned client ID: " + clientId);
                
                ClientHandler handler = new ClientHandler(clientSocket, this, clientId);
                
                // Add client with custom mutex
                clientsLock.withLock(() -> clients.add(handler));
                
                Thread clientThread = new Thread(handler);
                clientThread.setDaemon(false);
                clientThread.start();
                
                Logger.log("Client connected from " + clientSocket.getRemoteSocketAddress());
            } catch (IOException e) {
                if (running) {
                    Logger.error("Error accepting client: " + e.getMessage(), e);
                }
            }
        }
    }
    
    /**
     * Stops the server and closes all connections.
     */
    public void stop() {
        Logger.log("Stop method called - beginning shutdown sequence...");
        running = false;
        
        // Close all client connections using custom mutex
        List<ClientHandler> clientsCopy = clientsLock.withLock(() -> {
            Logger.log("Closing " + clients.size() + " client connections...");
            return new ArrayList<>(clients);
        });
        
        for (ClientHandler client : clientsCopy) {
            client.close();
        }
        
        clientsLock.withLock(clients::clear);
        
        // Close server socket
        closeServerSocket();
        
        // Save final state
        Logger.log("Saving final canvas state...");
        saveState();
        
        Logger.success("Server stopped successfully");
    }
    
    /**
     * Closes the server socket safely.
     */
    private void closeServerSocket() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                Logger.log("Server socket closed");
            } catch (IOException e) {
                Logger.error("Error closing server socket: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Broadcasts a message to all clients based on message type.
     * Routes messages to appropriate handlers.
     * 
     * @param message the serialized message to broadcast
     * @param sender the client that sent the message
     */
    void broadcast(String message, ClientHandler sender) {
        if (message.contains(MSG_PAINT)) {
            handlePaintMessage(message, sender);
        } else if (message.contains(MSG_JOIN)) {
            handleJoinMessage(message, sender);
        } else if (message.contains(MSG_LEAVE)) {
            handleLeaveMessage(message, sender);
        } else if (message.contains(MSG_ADMIN)) {
            handleAdminFromClient(message, sender);
        } else {
            broadcastToAllClients(message);
        }
    }
    
    /**
     * Broadcasts a message to all connected clients.
     * 
     * @param message the message to broadcast
     */
    private void broadcastToAllClients(String message) {
        List<ClientHandler> clientsCopy = clientsLock.withLock(() -> {
            Logger.log("Broadcasting to " + clients.size() + " clients");
            return new ArrayList<>(clients);
        });
        
        for (ClientHandler client : clientsCopy) {
            client.send(message);
        }
    }
    
    /**
     * Handles PAINT messages by enqueueing them for mutual exclusion processing.
     */
    private void handlePaintMessage(String message, ClientHandler sender) {
        enqueuePaintRequest(message, sender);
    }
    
    /**
     * Handles JOIN messages by broadcasting and sending canvas state.
     */
    private void handleJoinMessage(String message, ClientHandler sender) {
        Logger.log("Broadcasting JOIN to " + clients.size() + " clients");
        broadcastToAllClients(message);
        sendCanvasState(sender);
    }
    
    /**
     * Handles LEAVE messages by removing the client and broadcasting to others.
     */
    private void handleLeaveMessage(String message, ClientHandler sender) {
        Logger.log("Client " + sender.getClientId() + " sending LEAVE message");
        
        // Broadcast LEAVE to all clients (including the sender)
        broadcastToAllClients(message);
        
        // Remove the client from the server
        removeClient(sender);
        
        // Close the connection
        sender.close();
        
        Logger.info("Client " + sender.getClientId() + " left gracefully");
    }
    
    /**
     * Handles admin commands received from clients.
     */
    private void handleAdminFromClient(String message, ClientHandler sender) {
        try {
            String[] parts = message.split("\\|", 3);
            String command = parts[0]; // e.g., "/stats"
            handleAdminCommand(command);
            Message response = new Message(MSG_INFO, "Command executed: " + command);
            response.setTimestamp(lamportClock.tick());
            sender.send(response.serialize());
        } catch (ArrayIndexOutOfBoundsException e) {
            Logger.warn("Invalid admin command format from client " + sender.getClientId());
        }
    }
    
    /**
     * Adds a paint request to the mutual exclusion queue.
     * Updates server's Lamport clock based on client's timestamp.
     * 
     * @param message the paint message
     * @param sender the client that sent the paint request
     */
    private void enqueuePaintRequest(String message, ClientHandler sender) {
        try {
            String[] parts = message.split("\\|", 3);
            long clientTimestamp = Long.parseLong(parts[2]);
            int clientId = sender.getClientId();
            
            // Update server's Lamport clock: max(local, received) + 1
            lamportClock.update(clientTimestamp);
            
            PaintRequest request = new PaintRequest(message, clientTimestamp, clientId, sender);
            
            // Add to queue with custom mutex and notify waiting threads
            try {
                queueLock.lock();
                try {
                    requestQueue.offer(request);
                    queueLock.signalAll(); // Wake up worker thread
                } finally {
                    queueLock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            Logger.log("Enqueued paint request from client " + clientId + " with timestamp " + clientTimestamp);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            Logger.error("Error enqueuing paint request: " + e.getMessage(), e);
        }
    }
    
    /**
     * Processes paint requests from the queue in timestamp order.
     * Runs in a dedicated worker thread.
     */
    private void processPaintRequests() {
        try {
            while (running) {
                processNextRequest();
            }
            
            // Process remaining requests during shutdown
            processRemainingRequests();
        } catch (Exception e) {
            Logger.error("Error in mutual exclusion worker thread: " + e.getMessage(), e);
        }
    }
    
    /**
     * Processes the next request from the queue.
     */
    private void processNextRequest() {
        try {
            // Block until a request is available
            PaintRequest request;
            queueLock.lock();
            try {
                while (requestQueue.isEmpty()) {
                    queueLock.await(); // Block until signaled
                }
                request = requestQueue.poll();
            } finally {
                queueLock.unlock();
            }
            
            Logger.log("Processing request from client " + request.clientId + 
                      " (timestamp=" + request.timestamp + ")");
            
            // Process the paint message
            handlePaintMessage(request.message);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!running) {
                Logger.log("Worker thread interrupted during shutdown");
            }
        }
    }
    
    /**
     * Processes any remaining requests in the queue during shutdown.
     */
    private void processRemainingRequests() {
        int remaining = queueLock.withLock(requestQueue::size);
        
        if (remaining > 0) {
            Logger.log("Processing remaining " + remaining + " requests before shutdown");
            
            boolean empty = false;
            while (!empty) {
                PaintRequest request = queueLock.withLock(requestQueue::poll);
                empty = queueLock.withLock(requestQueue::isEmpty);
                
                if (request != null) {
                    handlePaintMessage(request.message);
                }
            }
        }
    }
    
    /**
     * Handles a paint message in the critical section.
     * Only one paint request is processed at a time.
     * 
     * @param message the paint message to process
     */
    private void handlePaintMessage(String message) {
        try {
            PaintMessageData data = parsePaintMessage(message);
            PixelState currentState = pixelStates.get(data.pixelKey);
            
            if (shouldAcceptPaintRequest(data, currentState)) {
                acceptPaintRequest(data, currentState, message);
            } else if (data.logicalClock == currentState.timestamp) {
                handlePaintConflict(data, currentState, message);
            } else {
                rejectPaintRequest(data, currentState);
            }
        } catch (IllegalArgumentException e) {
            Logger.error("Error processing paint message: " + e.getMessage(), null);
        }
    }
    
    /**
     * Parses a paint message into structured data.
     */
    private PaintMessageData parsePaintMessage(String message) {
        String[] parts = message.split("\\|", 3);
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid paint message format");
        }
        
        String coords = parts[0]; // "row,col,color"
        long logicalClock = Long.parseLong(parts[2]);
        
        String[] coordParts = coords.split(",", 3);
        if (coordParts.length < 3) {
            throw new IllegalArgumentException("Invalid coordinates format");
        }
        
        String row = coordParts[0];
        String col = coordParts[1];
        String color = coordParts[2];
        String pixelKey = row + "," + col;
        
        return new PaintMessageData(row, col, color, pixelKey, logicalClock);
    }
    
    /**
     * Determines if a paint request should be accepted.
     */
    private boolean shouldAcceptPaintRequest(PaintMessageData data, PixelState currentState) {
        return currentState == null || data.logicalClock > currentState.timestamp;
    }
    
    /**
     * Accepts and processes a paint request.
     */
    private void acceptPaintRequest(PaintMessageData data, PixelState currentState, String message) {
        pixelStatesLock.withLock(() -> 
            pixelStates.put(data.pixelKey, new PixelState(data.color, data.logicalClock))
        );
        
        totalPaintsLock.withLock(() -> totalPaints++);
        
        String status = (currentState == null) ? "new pixel" : "update";
        Logger.success("Accepted paint for " + status + " " + data.pixelKey + 
                      " with clock " + data.logicalClock);
        
        broadcastToAllClients(message);
        autoSave();
    }
    
    /**
     * Handles a paint conflict using tie-breaking logic.
     */
    private void handlePaintConflict(PaintMessageData data, PixelState currentState, String message) {
        totalConflictsLock.withLock(() -> totalConflicts++);
        
        // Tie-breaker: use lexicographic order of color
        if (data.color.compareTo(currentState.color) > 0) {
            pixelStatesLock.withLock(() -> 
                pixelStates.put(data.pixelKey, new PixelState(data.color, data.logicalClock))
            );
            
            totalPaintsLock.withLock(() -> totalPaints++);
            
            Logger.warn("Accepted paint for pixel " + data.pixelKey + " (tie-breaker won)");
            broadcastToAllClients(message);
        } else {
            totalRejectedLock.withLock(() -> totalRejected++);
            Logger.warn("Rejected paint for pixel " + data.pixelKey + " (tie-breaker lost)");
        }
    }
    
    /**
     * Rejects an outdated paint request.
     */
    private void rejectPaintRequest(PaintMessageData data, PixelState currentState) {
        totalRejectedLock.withLock(() -> totalRejected++);
        Logger.warn("Rejected outdated paint for pixel " + data.pixelKey + 
                   " (clock " + data.logicalClock + " < " + currentState.timestamp + ")");
    }
    
    /**
     * Sends the current canvas state to a specific client.
     * 
     * @param client the client to send the state to
     */
    private void sendCanvasState(ClientHandler client) {
        Map<String, PixelState> statesCopy;
        int size;
        
        try {
            pixelStatesLock.lock();
            try {
                size = pixelStates.size();
                statesCopy = new HashMap<>(pixelStates);
            } finally {
                pixelStatesLock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.error("Interrupted while acquiring lock to send canvas state to client " + 
                 client.getClientId(), e);
            return;
        }
        
        Logger.log("Sending canvas state (" + size + " pixels) to client " + client.getClientId());
        
        // Send each pixel state as a SYNC_DATA message
        for (Map.Entry<String, PixelState> entry : statesCopy.entrySet()) {
            String pixelKey = entry.getKey();
            PixelState state = entry.getValue();
            
            // Format: "row,col,color|SYNC_DATA|timestamp"
            String content = pixelKey + "," + state.color;
            Message syncMsg = new Message(MSG_SYNC_DATA, content);
            syncMsg.setTimestamp(lamportClock.tick());
            client.send(syncMsg.serialize());
        }
        
        // Send completion marker
        Message completeMsg = new Message(MSG_SYNC_COMPLETE, "");
        completeMsg.setTimestamp(lamportClock.tick());
        client.send(completeMsg.serialize());
        
        Logger.log("Canvas state sent to client " + client.getClientId());
    }
    
    /**
     * Saves the current canvas state to a file.
     * Format: Each line is "row,col,color,timestamp"
     */
    public void saveState() {
        File file = new File(STATE_FILE);
        
        Map<String, PixelState> statesCopy;
        int size;
        try {
            pixelStatesLock.lock();
            try {
                size = pixelStates.size();
                statesCopy = new HashMap<>(pixelStates);
            } finally {
                pixelStatesLock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.error("Interrupted while acquiring lock for saving state", e);
            return;
        }
        
        Logger.log("Saving canvas state (" + size + " pixels) to " + 
                  file.getAbsolutePath());
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (Map.Entry<String, PixelState> entry : statesCopy.entrySet()) {
                String pixelKey = entry.getKey();
                PixelState state = entry.getValue();
                writer.write(pixelKey + "," + state.color + "," + state.timestamp);
                writer.newLine();
            }
            Logger.success("Canvas state saved successfully!");
        } catch (IOException e) {
            Logger.error("Failed to save canvas state: " + e.getMessage(), e);
        }
    }
    
    /**
     * Loads the canvas state from a file.
     * Called on server startup.
     */
    public void loadState() {
        File file = new File(STATE_FILE);
        if (!file.exists()) {
            Logger.log("No saved state found. Starting with empty canvas.");
            return;
        }
        
        Logger.info("Loading canvas state from " + STATE_FILE);
        int pixelCount = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(STATE_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (parseStateLine(line)) {
                    pixelCount++;
                }
            }
            Logger.info("Canvas state loaded: " + pixelCount + " pixels restored!");
        } catch (IOException e) {
            Logger.error("Error loading canvas state: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parses a single line from the state file.
     * 
     * @param line the line to parse
     * @return true if the line was successfully parsed, false otherwise
     */
    private boolean parseStateLine(String line) {
        try {
            String[] parts = line.split(",", 4);
            if (parts.length < 4) {
                Logger.warn("Skipping invalid state line: " + line);
                return false;
            }
            
            String row = parts[0];
            String col = parts[1];
            String color = parts[2];
            long logicalClock = Long.parseLong(parts[3]);
            
            String pixelKey = row + "," + col;
            pixelStatesLock.withLock(() -> 
                pixelStates.put(pixelKey, new PixelState(color, logicalClock))
            );
            return true;
        } catch (NumberFormatException e) {
            Logger.warn("Skipping malformed state line: " + line);
            return false;
        }
    }
    
    /**
     * Auto-saves the canvas state if conditions are met.
     * Conditions: enough time passed OR enough paint operations performed.
     */
    private void autoSave() {
        int paintCount = paintCountLock.withLock(() -> {
            paintCountSinceLastSave++;
            return paintCountSinceLastSave;
        });
        
        long currentTime = System.currentTimeMillis();
        long lastSave = lastSaveTimeLock.withLock(() -> lastSaveTime);
        
        boolean timeToSave = (currentTime - lastSave >= AUTO_SAVE_INTERVAL_MS);
        boolean paintThresholdReached = (paintCount >= PAINT_COUNT_THRESHOLD);
        
        if (timeToSave || paintThresholdReached) {
            int pixelCount = pixelStatesLock.withLock(pixelStates::size);
            
            Logger.log("Auto-saving canvas state... (pixels: " + pixelCount + 
                      ", paint ops since last save: " + paintCount + ")");
            saveState();
            
            lastSaveTimeLock.withLock(() -> lastSaveTime = currentTime);
            paintCountLock.withLock(() -> paintCountSinceLastSave = 0);
        }
    }
    
    /**
     * Handles admin commands from the server console or clients.
     * 
     * @param command the admin command to execute
     */
    public void handleAdminCommand(String command) {
        Logger.admin("Executing command: " + command);
        
        switch (command) {
            case "/help":
                printHelp();
                break;
            case "/stats":
                printStats();
                break;
            case "/clients":
                printClients();
                break;
            case "/save":
                saveState();
                break;
            case "/clear":
                clearCanvas();
                break;
            default:
                Logger.warn("Unknown admin command: " + command);
                Logger.info("Type /help for available commands");
                break;
        }
    }
    
    /**
     * Displays available admin commands.
     */
    private void printHelp() {
        Logger.separator();
        Logger.admin("Available Admin Commands:");
        Logger.info("  /help      - Show this help message");
        Logger.info("  /stats     - Display server statistics");
        Logger.info("  /clients   - List all connected clients");
        Logger.info("  /save      - Save canvas state to file");
        Logger.info("  /clear     - Clear the entire canvas");
        Logger.info("  /exit      - Stop the server and exit");
        Logger.info("  /quit      - Stop the server and exit");
        Logger.separator();
    }
    
    /**
     * Displays server statistics.
     */
    private void printStats() {
        long uptime = System.currentTimeMillis() - serverStartTime;
        long uptimeSeconds = uptime / 1000;
        long uptimeMinutes = uptimeSeconds / 60;
        long uptimeHours = uptimeMinutes / 60;
        
        int clientCount = clientsLock.withLock(clients::size);
        int queueSize = queueLock.withLock(requestQueue::size);
        int pixelCount = pixelStatesLock.withLock(pixelStates::size);
        
        int paintsCount = totalPaintsLock.withLock(() -> totalPaints);
        int conflictsCount = totalConflictsLock.withLock(() -> totalConflicts);
        int rejectedCount = totalRejectedLock.withLock(() -> totalRejected);
        
        Logger.separator();
        Logger.admin("Server Statistics:");
        Logger.info("  Uptime: " + uptimeHours + "h " + (uptimeMinutes % 60) + "m " + 
                   (uptimeSeconds % 60) + "s");
        Logger.info("  Connected Clients: " + clientCount);
        Logger.info("  Total Paint Operations: " + paintsCount);
        Logger.info("  Conflicts Detected: " + conflictsCount);
        Logger.info("  Rejected Paints: " + rejectedCount);
        Logger.info("  Canvas Pixels Painted: " + pixelCount);
        Logger.info("  Queue Size: " + queueSize);
        
        if (paintsCount > 0) {
            double conflictRate = (conflictsCount * 100.0) / paintsCount;
            double rejectionRate = (rejectedCount * 100.0) / paintsCount;
            Logger.info("  Conflict Rate: " + String.format("%.2f%%", conflictRate));
            Logger.info("  Rejection Rate: " + String.format("%.2f%%", rejectionRate));
        }
        Logger.separator();
    }
    
    /**
     * Lists all connected clients.
     */
    private void printClients() {
        Logger.separator();
        
        List<ClientHandler> clientsCopy;
        int size;
        boolean empty;
        
        try {
            clientsLock.lock();
            try {
                size = clients.size();
                empty = clients.isEmpty();
                clientsCopy = empty ? null : new ArrayList<>(clients);
            } finally {
                clientsLock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.error("Interrupted while acquiring lock for printing clients", e);
            return;
        }
        
        Logger.admin("Connected Clients (" + size + "):");
        if (empty) {
            Logger.info("  No clients currently connected");
        } else {
            for (ClientHandler client : clientsCopy) {
                Logger.info("  Client " + client.getClientId() + " - " + client.getAddress());
            }
        }
        Logger.separator();
    }
    
    /**
     * Clears the entire canvas and notifies all clients.
     */
    private void clearCanvas() {
        int pixelCount = pixelStatesLock.withLock(() -> {
            int count = pixelStates.size();
            pixelStates.clear();
            return count;
        });
        
        Logger.admin("Canvas cleared! Removed " + pixelCount + " pixels");
        
        // Broadcast CLEAR message to all clients
        Message clearMsg = new Message(MSG_CLEAR, "");
        clearMsg.setTimestamp(lamportClock.tick());
        String clearMessage = clearMsg.serialize();
        
        List<ClientHandler> clientsCopy = clientsLock.withLock(() -> new ArrayList<>(clients));
        Logger.log("Broadcasting CLEAR to " + clientsCopy.size() + " clients");
        
        for (ClientHandler client : clientsCopy) {
            client.send(clearMessage);
        }
        
        // Save empty state
        saveState();
    }
    
    /**
     * Removes a client from the server.
     * 
     * @param client the client to remove
     */
    void removeClient(ClientHandler client) {
        int remainingClients = clientsLock.withLock(() -> {
            clients.remove(client);
            return clients.size();
        });
        Logger.log("Client disconnected. Total clients: " + remainingClients);
    }

    /**
     * Represents the state of a single pixel on the canvas.
     */
    private static class PixelState {
        final String color;
        final long timestamp;
        
        PixelState(String color, long timestamp) {
            this.color = color;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Represents a paint request in the mutual exclusion queue.
     * Implements Comparable for timestamp-based ordering.
     */
    private static class PaintRequest implements Comparable<PaintRequest> {
        final String message;
        final long timestamp;
        final int clientId;
        final ClientHandler sender;
        
        PaintRequest(String message, long timestamp, int clientId, ClientHandler sender) {
            this.message = message;
            this.timestamp = timestamp;
            this.clientId = clientId;
            this.sender = sender;
        }
        
        @Override
        public int compareTo(PaintRequest other) {
            // Primary: order by timestamp
            int timestampCompare = Long.compare(this.timestamp, other.timestamp);
            if (timestampCompare != 0) {
                return timestampCompare;
            }
            // Tie-breaker: order by client ID
            return Integer.compare(this.clientId, other.clientId);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            PaintRequest that = (PaintRequest) obj;
            return timestamp == that.timestamp && clientId == that.clientId;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(timestamp, clientId);
        }
    }
    
    /**
     * Data class for parsed paint message information.
     */
    private static class PaintMessageData {
        final String row;
        final String col;
        final String color;
        final String pixelKey;
        final long logicalClock;
        
        PaintMessageData(String row, String col, String color, String pixelKey, long logicalClock) {
            this.row = row;
            this.col = col;
            this.color = color;
            this.pixelKey = pixelKey;
            this.logicalClock = logicalClock;
        }
    }
    
    /**
     * Handles communication with a single client.
     * Runs in its own thread.
     */
    static class ClientHandler implements Runnable {
        private final Socket socket;
        private final Server server;
        private final int clientId;
        private BufferedReader input;
        private PrintWriter output;
        
        ClientHandler(Socket socket, Server server, int clientId) {
            this.socket = socket;
            this.server = server;
            this.clientId = clientId;
        }
        
        int getClientId() {
            return clientId;
        }
        
        String getAddress() {
            return socket.getRemoteSocketAddress().toString();
        }
        
        @Override
        public void run() {
            try {
                initializeStreams();
                processClientMessages();
            } catch (IOException e) {
                Logger.error("Client " + clientId + " error: " + e.getMessage(), null);
            } finally {
                cleanup();
            }
        }
        
        /**
         * Initializes input and output streams for the client.
         */
        private void initializeStreams() throws IOException {
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(socket.getOutputStream(), true);
        }
        
        /**
         * Processes messages from the client.
         */
        private void processClientMessages() throws IOException {
            String line;
            while ((line = input.readLine()) != null) {
                Logger.log("Received: " + line);
                server.broadcast(line, this);
            }
        }
        
        /**
         * Sends a raw message to this client.
         * 
         * @param message the message to send
         */
        void send(String message) {
            if (output != null && !socket.isClosed()) {
                output.println(message);
            }
        }
        
        /**
         * Closes the client connection.
         */
        void close() {
            try {
                if (input != null) input.close();
                if (output != null) output.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                Logger.error("Error closing client " + clientId + " connection: " + 
                           e.getMessage(), null);
            }
        }
        
        /**
         * Cleanup when the client disconnects.
         */
        private void cleanup() {
            close();
            server.removeClient(this);
        }
    }
}
