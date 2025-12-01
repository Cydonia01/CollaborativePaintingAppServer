package project;

/**
 * Implementation of Lamport Logical Clock for distributed systems.
 * 
 * <p>Lamport Clocks provide a way to order events in a distributed system
 * where there is no global clock. Each process maintains its own logical clock
 * and follows these rules:
 * <ol>
 *   <li>Before an event, increment the local clock</li>
 *   <li>When sending a message, include the current clock value</li>
 *   <li>When receiving a message, set clock to max(local, received) + 1</li>
 * </ol>
 * 
 * @see Server
 * @see Message
 * @see Mutex
 */
public class LamportClock {
    
    private long time;
    private final Mutex timeLock;
    
    /**
     * Creates a new Lamport Clock initialized to 0.
     */
    public LamportClock() {
        this.time = 0;
        this.timeLock = new Mutex();
    }
    
    /**
     * Increments the clock for a local event.
     * 
     * @return the new timestamp after incrementing
     */
    public long tick() {
        return timeLock.withLock(() -> ++time);
    }
    
    /**
     * Updates the clock when receiving a message from another process.
     * Implements the Lamport Clock rule: clock = max(local_time, received_time) + 1
     * 
     * @param receivedTime the timestamp from the received message
     * @return the new timestamp after synchronization
     */
    public long update(long receivedTime) {
        return timeLock.withLock(() -> {
            time = Math.max(time, receivedTime) + 1;
            return time;
        });
    }
    
    /**
     * Gets the current clock value without incrementing.
     * This is useful for querying the current state without affecting the clock.
     * 
     * @return the current timestamp
     */
    public long getTime() {
        return timeLock.withLock(() -> time);
    }
}
