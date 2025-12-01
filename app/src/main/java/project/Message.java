package project;

import java.io.Serializable;

/**
 * Represents a message sent between processes in the distributed system.
 * 
 * <p>Messages contain:
 * <ul>
 *   <li>Type: The message category (PAINT, JOIN etc.)</li>
 *   <li>Content: The actual message payload</li>
 *   <li>Timestamp: Lamport logical clock value for ordering</li>
 * </ul>
 * 
 * <p>Serialization Format: {@code content|type|timestamp}
 * 
 * @see LamportClock
 */
public class Message implements Serializable {

    private final String content;
    private final String type;
    private long timestamp;

    /**
     * Creates a new message with the specified type and content.
     * Timestamp must be set separately using setTimestamp().
     * 
     * @param type the message type (e.g., "PAINT", "JOIN")
     * @param content the message content/payload
     */
    public Message(String type, String content) {
        this.content = content;
        this.type = type;
        this.timestamp = 0; // Will be set by server using Lamport clock
    }
    
    /**
     * Sets the Lamport logical timestamp for this message.
     * 
     * @param timestamp the Lamport clock timestamp
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    /**
     * Gets the message timestamp.
     * 
     * @return the Lamport logical timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Gets the message type.
     * 
     * @return the message type
     */
    public String getType() {
        return type;
    }

    /**
     * Gets the message content.
     * 
     * @return the message content
     */
    public String getContent() {
        return content;
    }

    /**
     * Serializes this message into a string format for transmission.
     * Format: {@code content|type|timestamp}
     * 
     * @return the serialized message string
     */
    public String serialize() {
        return content + "|" + type + "|" + timestamp;
    }

    /**
     * Deserializes a message from a string.
     * 
     * @param data the serialized message string in format {@code content|type|timestamp}
     * @return the deserialized Message object
     * @throws NumberFormatException if timestamp cannot be parsed
     * @throws ArrayIndexOutOfBoundsException if data format is invalid
     */
    public static Message deserialize(String data) {
        String[] parts = data.split("\\|", 3);
        Message msg = new Message(parts[1], parts[0]);
        msg.timestamp = Long.parseLong(parts[2]);
        return msg;
    }
}
