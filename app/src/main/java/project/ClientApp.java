package project;

import java.net.*;
import java.io.*;
import java.util.Scanner;

/**
 * TCP Client application for the collaborative painting system.
 * 
 * <p>Connects to the server and allows users to:
 * <ul>
 *   <li>Send messages to the server</li>
 *   <li>Receive and display messages from the server</li>
 *   <li>Quit the application gracefully</li>
 * </ul>
 */
public class ClientApp {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5000;
    private static final String QUIT_COMMAND = "/quit";
    
    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    private final String host;
    private final int port;
    private volatile boolean running;

    /**
     * Creates a new client application.
     * 
     * @param host the server hostname or IP address to connect to
     * @param port the server port number
     */
    public ClientApp(String host, int port) {
        this.host = host;
        this.port = port;
        this.running = false;
    }

    /**
     * Starts the client and connects to the server.
     * Blocks until the client disconnects or encounters an error.
     * 
     * <p>This method:
     * <ul>
     *   <li>Establishes TCP connection to the server</li>
     *   <li>Sends a JOIN message to announce presence</li>
     *   <li>Starts a background thread to receive messages</li>
     *   <li>Reads user input from console and sends to server</li>
     * </ul>
     */
    public void start() {
        try {
            socket = new Socket(host, port);
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(socket.getOutputStream(), true);
            running = true;
            
            Logger.log("Connected to server at " + host + ":" + port);
            
            // Send JOIN message
            sendMessage(new Message("", "JOIN"));

            // Start receiver thread
            new Thread(this::receiveMessages).start();

            // Handle user input
            Scanner scanner = new Scanner(System.in);

            while (running) {
                String userInput = scanner.nextLine();

                if (QUIT_COMMAND.equalsIgnoreCase(userInput)) {
                    sendMessage(new Message("", "LEAVE"));
                    stop();
                    break;
                }

                sendMessage(new Message(userInput, "MESSAGE"));
            }

            scanner.close();
        } catch (IOException e) {
            Logger.error("Failed to connect to server: " + e.getMessage(), e);
        }
    }

    /**
     * Sends a message to the server.
     * 
     * @param message the message to send
     */
    private void sendMessage(Message message) {
        if (output != null && !socket.isClosed()) {
            output.println(message.serialize());
        }
    }

    /**
     * Receives messages from the server in a loop.
     * Runs in a separate thread.
     */
    private void receiveMessages() {
        try {
            String line;
            while (running && (line = input.readLine()) != null) {
                try {
                    Message message = Message.deserialize(line);
                    
                    if (message.getType().equals("INFO")) {
                        Logger.log("[SERVER] " + message.getContent());
                    } else {
                        Logger.log("Message: " + message.getContent());
                    }
                } catch (Exception e) {
                    Logger.log("Error parsing message: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            if (running) {
                Logger.log("Connection lost: " + e.getMessage());
            }
        } finally {
            stop();
        }
    }

    /**
     * Stops the client and closes all connections.
     * Closes input/output streams and the socket connection gracefully.
     */
    public void stop() {
        running = false;
        try {
            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            Logger.error("Error closing connection: " + e.getMessage(), e);
        }
        Logger.log("Disconnected from server.");
    }

    /**
     * Main entry point for the client application.
     * Prompts user for server connection details and starts the client.
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        // Get connection details
        System.out.print("Enter server IP (default: " + DEFAULT_HOST + "): ");
        String host = scanner.nextLine().trim();
        if (host.isEmpty()) {
            host = DEFAULT_HOST;
        }
        
        System.out.print("Enter server port (default: " + DEFAULT_PORT + "): ");
        String portInput = scanner.nextLine().trim();
        int port = DEFAULT_PORT;
        if (!portInput.isEmpty()) {
            try {
                port = Integer.parseInt(portInput);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port. Using default " + DEFAULT_PORT + ".");
            }
        }
        
        ClientApp client = new ClientApp(host, port);
        client.start();
        
        scanner.close();
    }
}
