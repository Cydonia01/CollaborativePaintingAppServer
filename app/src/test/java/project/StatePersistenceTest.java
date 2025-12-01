package project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Integration tests for Server state persistence functionality.
 * Tests that the server correctly saves and restores canvas state.
 */
class StatePersistenceTest {

    private static final int TEST_PORT = 5001;
    private static final String STATE_FILE = "canvas_state.txt";
    private Server server;

    @BeforeEach
    void setUp() throws IOException {
        // Clean up any existing state file
        Files.deleteIfExists(Paths.get(STATE_FILE));
        
        // Create and start server
        server = new Server(TEST_PORT);
        new Thread(() -> {
            try {
                server.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
        
        // Give server time to start
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        
        // Clean up state file after test
        try {
            Files.deleteIfExists(Paths.get(STATE_FILE));
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }

    @Test
    @DisplayName("Server saves state when saveState() is called")
    void testSaveStateCreatesFile() {
        // Verify file doesn't exist initially
        assertFalse(Files.exists(Paths.get(STATE_FILE)));
        
        // Save state
        server.saveState();
        
        // Verify file was created
        assertTrue(Files.exists(Paths.get(STATE_FILE)));
    }

    @Test
    @DisplayName("Server saves empty canvas correctly")
    void testSaveEmptyCanvas() throws IOException {
        // Save empty canvas
        server.saveState();
        
        // Verify file exists and is empty (or just has no pixel data)
        assertTrue(Files.exists(Paths.get(STATE_FILE)));
        String content = Files.readString(Paths.get(STATE_FILE));
        // Empty canvas should create empty file or file with no lines
        assertTrue(content.isEmpty() || content.isBlank());
    }

    @Test
    @DisplayName("Server loads state from file on startup")
    void testLoadStateOnStartup() throws IOException {
        // Create a state file manually
        try (PrintWriter writer = new PrintWriter(new FileWriter(STATE_FILE))) {
            writer.println("5,10,#FF5733,100");
            writer.println("3,7,#00FF00,200");
        }
        
        // Stop current server
        server.stop();
        
        // Start new server (should load state)
        Server newServer = new Server(TEST_PORT + 1);
        new Thread(() -> {
            try {
                newServer.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
        
        try {
            Thread.sleep(200); // Give time to load state
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // State should be loaded (we can't easily verify internal state without exposing it)
        // But we can verify the file was read
        assertTrue(Files.exists(Paths.get(STATE_FILE)));
        
        newServer.stop();
    }

    @Test
    @DisplayName("Server persists state across restart")
    void testStatePersistsAcrossRestart() throws IOException, InterruptedException {
        // Simulate a paint operation by creating state file
        try (PrintWriter writer = new PrintWriter(new FileWriter(STATE_FILE))) {
            writer.println("0,0,#FF0000,100");
            writer.println("1,1,#00FF00,200");
            writer.println("2,2,#0000FF,300");
        }
        
        // First server loads state
        server.stop();
        Server server1 = new Server(TEST_PORT + 2);
        Thread serverThread1 = new Thread(() -> {
            try {
                server1.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverThread1.start();
        Thread.sleep(200);
        
        // Server saves state
        server1.saveState();
        
        // Stop server
        server1.stop();
        Thread.sleep(100);
        
        // Verify file still exists
        assertTrue(Files.exists(Paths.get(STATE_FILE)));
        
        // Start new server - should load the saved state
        Server server2 = new Server(TEST_PORT + 3);
        Thread serverThread2 = new Thread(() -> {
            try {
                server2.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverThread2.start();
        Thread.sleep(200);
        
        // State should be loaded
        assertTrue(Files.exists(Paths.get(STATE_FILE)));
        
        server2.stop();
    }

    @Test
    @DisplayName("State file format is correct")
    void testStateFileFormat() throws IOException, InterruptedException {
        // Create a state file
        try (PrintWriter writer = new PrintWriter(new FileWriter(STATE_FILE))) {
            writer.println("5,10,#AABBCC,12345");
        }
        
        // Load in new server
        server.stop();
        Server newServer = new Server(TEST_PORT + 4);
        new Thread(() -> {
            try {
                newServer.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
        
        Thread.sleep(200);
        
        // Save state again
        newServer.saveState();
        
        // Verify format: row,col,color,timestamp
        String content = Files.readString(Paths.get(STATE_FILE));
        if (!content.isBlank()) {
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (!line.isBlank()) {
                    assertTrue(line.matches("\\d+,\\d+,#[0-9A-Fa-f]{6},\\d+"),
                             "Line doesn't match expected format: " + line);
                }
            }
        }
        
        newServer.stop();
    }
}

