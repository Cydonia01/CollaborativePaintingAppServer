package project;

import java.io.IOException;
import java.util.Scanner;

/**
 * Main application entry point for the collaborative painting server.
 * 
 * <p>This class:
 * <ul>
 *   <li>Initializes and starts the TCP server</li>
 *   <li>Provides an admin console for server management</li>
 *   <li>Handles graceful shutdown on termination</li>
 *   <li>Supports custom port configuration via command line</li>
 * </ul>
 * 
 * @see Server
 * @see Logger
 */
public class ServerApp {
    
    private static final int DEFAULT_PORT = 5000;
    private static final long ADMIN_CONSOLE_DELAY_MS = 1000;
    
    /**
     * Main entry point for the server application.
     * 
     * @param args command line arguments, optionally containing port number
     */
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        
        // Allow custom port via command line argument
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default port " + DEFAULT_PORT + ".");
            }
        }
        
        Server server = new Server(port);
        
        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Shutting down server...");
            server.stop();
        }));
        
        // Start admin console in separate thread
        Thread adminThread = new Thread(() -> {
            try {
                Thread.sleep(ADMIN_CONSOLE_DELAY_MS); // Give server time to start
                runAdminConsole(server);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        adminThread.setDaemon(true);
        adminThread.start();
        
        try {
            Logger.separator();
            Logger.success("Starting Collaborative Painting Server");
            Logger.info("Type /help for available admin commands");
            Logger.separator();
            server.start();
        } catch (IOException e) {
            Logger.error("Failed to start server: " + e.getMessage(), e);
            System.exit(1);
        }
    }
    
    /**
     * Runs the admin console in a loop, processing admin commands from standard input.
     * This method blocks indefinitely, reading commands until the server is shut down.
     * 
     * <p>Supported commands:
     * <ul>
     *   <li>/quit or /exit - Shutdown the server</li>
     *   <li>/help - Show available commands</li>
     *   <li>/stats - Display server statistics</li>
     *   <li>/clients - List connected clients</li>
     *   <li>/save - Save canvas state</li>
     *   <li>/clear - Clear the canvas</li>
     * </ul>
     * 
     * @param server the server instance to manage
     */
    private static void runAdminConsole(Server server) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                try {
                    if (scanner.hasNextLine()) {
                        String input = scanner.nextLine().trim();
                        if (!input.isEmpty()) {
                            if (input.equals("/quit") || input.equals("/exit")) {
                                Logger.info("Shutting down server...");
                                server.stop();
                                System.exit(0);
                            } else if (input.startsWith("/")) {
                                server.handleAdminCommand(input);
                            } else {
                                Logger.warn("Unknown command. Type /help for available commands.");
                            }
                        }
                    }
                } catch (Exception e) {
                    Logger.error("Error in admin console: " + e.getMessage(), e);
                }
            }
        }
    }
}
