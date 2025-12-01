package project;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

/**
 * Command-line admin client for the collaborative painting server.
 * Allows remote administration of the server.
 */
public class AdminClient {
    
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private final String serverIp;
    private final int serverPort;
    
    public AdminClient(String serverIp, int serverPort) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
    }
    
    public void connect() throws IOException {
        System.out.println("Connecting to server at " + serverIp + ":" + serverPort + "...");
        socket = new Socket(serverIp, serverPort);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        System.out.println("Connected successfully!");
        
        // Send JOIN message
        Message joinMsg = new Message("JOIN", "AdminClient");
        joinMsg.setTimestamp(0);
        out.println(joinMsg.serialize());
        
        // Start listener thread
        new Thread(this::listenForMessages).start();
    }
    
    private void listenForMessages() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                Message msg = Message.deserialize(line);
                System.out.println("[SERVER] " + msg.getType() + ": " + msg.getContent());
            }
        } catch (IOException e) {
            System.err.println("Disconnected from server");
        }
    }
    
    public void sendCommand(String command) {
        Message adminMsg = new Message("ADMIN", command);
        adminMsg.setTimestamp(0);
        out.println(adminMsg.serialize());
    }
    
    public void disconnect() {
        try {
            if (out != null) {
                Message leaveMsg = new Message("LEAVE", "AdminClient");
                leaveMsg.setTimestamp(0);
                out.println(leaveMsg.serialize());
            }
            if (socket != null) socket.close();
            System.out.println("Disconnected.");
        } catch (IOException e) {
            System.err.println("Error disconnecting: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        // Get server details
        System.out.print("Enter server IP (or press Enter for localhost): ");
        String ip = scanner.nextLine().trim();
        if (ip.isEmpty()) {
            ip = "localhost";
        }
        
        System.out.print("Enter server port (or press Enter for 5000): ");
        String portStr = scanner.nextLine().trim();
        int port = portStr.isEmpty() ? 5000 : Integer.parseInt(portStr);
        
        AdminClient client = new AdminClient(ip, port);
        
        try {
            client.connect();
            
            System.out.println("\n=== Admin Client ===");
            System.out.println("Available commands:");
            System.out.println("  /help     - Show available admin commands");
            System.out.println("  /stats    - Show server statistics");
            System.out.println("  /clients  - List connected clients");
            System.out.println("  /save     - Save canvas state");
            System.out.println("  /clear    - Clear the canvas");
            System.out.println("  /exit     - Disconnect and exit");
            System.out.println("  /quit     - Disconnect and exit");
            System.out.println("==================\n");
            
            // Command loop
            while (true) {
                System.out.print("Admin> ");
                String command = scanner.nextLine().trim();
                
                if (command.isEmpty()) {
                    continue;
                }
                
                if (command.equalsIgnoreCase("/exit") || command.equalsIgnoreCase("/quit")) {
                    break;
                }
                
                client.sendCommand(command);
            }
            
            client.disconnect();
            
        } catch (IOException e) {
            System.err.println("Failed to connect: " + e.getMessage());
        } finally {
            scanner.close();
        }
    }
}
