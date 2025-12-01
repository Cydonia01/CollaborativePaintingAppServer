package project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Message serialization and deserialization.
 * Tests the message protocol format: "content|type|timestamp"
 */
public class MessageTest {

    @Test
    @DisplayName("Serialize and deserialize message with Lamport timestamp")
    public void testSerializeDeserialize() {
        Message message = new Message("PAINT", "10,20,#FF5733");
        message.setTimestamp(42);
        
        String serialized = message.serialize();
        assertEquals("10,20,#FF5733|PAINT|42", serialized);
        
        Message deserialized = Message.deserialize(serialized);
        assertEquals("PAINT", deserialized.getType());
        assertEquals("10,20,#FF5733", deserialized.getContent());
        assertEquals(42, deserialized.getTimestamp());
    }

    @Test
    @DisplayName("Message with empty content")
    public void testEmptyContent() {
        Message message = new Message("JOIN", "");
        message.setTimestamp(100);
        
        String serialized = message.serialize();
        assertEquals("|JOIN|100", serialized);
        
        Message deserialized = Message.deserialize(serialized);
        assertEquals("JOIN", deserialized.getType());
        assertEquals("", deserialized.getContent());
        assertEquals(100, deserialized.getTimestamp());
    }

    @Test
    @DisplayName("Round-trip preserves all fields")
    public void testRoundTrip() {
        Message original = new Message("CLEAR", "canvas_cleared");
        original.setTimestamp(999);
        
        String serialized = original.serialize();
        Message deserialized = Message.deserialize(serialized);
        
        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getContent(), deserialized.getContent());
        assertEquals(original.getTimestamp(), deserialized.getTimestamp());
    }

    @Test
    @DisplayName("Deserialize throws exception for invalid format")
    public void testDeserializeInvalid() {
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> {
            Message.deserialize("invalid");
        });
        
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> {
            Message.deserialize("only|two");
        });
    }

    @Test
    @DisplayName("Deserialize throws exception for invalid timestamp")
    public void testInvalidTimestamp() {
        assertThrows(NumberFormatException.class, () -> {
            Message.deserialize("content|TYPE|notanumber");
        });
    }

    @Test
    @DisplayName("Default timestamp is zero before setTimestamp")
    public void testDefaultTimestamp() {
        Message message = new Message("INFO", "test");
        assertEquals(0, message.getTimestamp());
        
        message.setTimestamp(123);
        assertEquals(123, message.getTimestamp());
    }
}
