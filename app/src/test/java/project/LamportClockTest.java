package project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Lamport Logical Clock implementation with Mutex.
 * Tests the fundamental properties of Lamport clocks:
 * 1. Monotonically increasing timestamps
 * 2. Proper synchronization with received messages
 * 3. Thread-safety using Mutex
 */
public class LamportClockTest {

    private LamportClock clock;

    @BeforeEach
    public void setUp() {
        clock = new LamportClock();
    }

    @Test
    @DisplayName("Initial timestamp is 0")
    public void testInitialTimestamp() {
        assertEquals(0L, clock.getTime());
    }

    @Test
    @DisplayName("Tick increments timestamp by 1")
    public void testTick() {
        long t1 = clock.tick();
        assertEquals(1L, t1);
        assertEquals(1L, clock.getTime());
        
        long t2 = clock.tick();
        assertEquals(2L, t2);
        assertEquals(2L, clock.getTime());
    }

    @Test
    @DisplayName("Update with larger timestamp synchronizes correctly")
    public void testUpdateWithLargerTimestamp() {
        clock.tick(); // time = 1
        
        long result = clock.update(5L); // max(1, 5) + 1 = 6
        assertEquals(6L, result);
        assertEquals(6L, clock.getTime());
    }

    @Test
    @DisplayName("Update with smaller timestamp still increments")
    public void testUpdateWithSmallerTimestamp() {
        clock.tick(); // time = 1
        clock.tick(); // time = 2
        
        long result = clock.update(1L); // max(2, 1) + 1 = 3
        assertEquals(3L, result);
        assertEquals(3L, clock.getTime());
    }

    @Test
    @DisplayName("Sequence of operations maintains monotonicity")
    public void testMonotonicity() {
        long t1 = clock.tick(); // 1
        long t2 = clock.update(5L); // max(1, 5) + 1 = 6
        long t3 = clock.update(3L); // max(6, 3) + 1 = 7
        long t4 = clock.tick(); // 8
        
        assertTrue(t1 < t2);
        assertTrue(t2 < t3);
        assertTrue(t3 < t4);
        assertEquals(8L, clock.getTime());
    }

    @Test
    @DisplayName("Message synchronization between two processes")
    public void testTwoProcessSync() {
        LamportClock clockA = new LamportClock();
        LamportClock clockB = new LamportClock();
        
        // Process A sends message
        long sendTime = clockA.tick(); // A: 1
        
        // Process B receives and updates
        long receiveTime = clockB.update(sendTime); // B: max(0, 1) + 1 = 2
        
        assertTrue(sendTime < receiveTime);
        assertEquals(1L, clockA.getTime());
        assertEquals(2L, clockB.getTime());
    }

    @Test
    @DisplayName("Thread-safe concurrent ticks with custom Mutex")
    public void testConcurrentTicks() throws InterruptedException {
        final int numThreads = 10;
        final int ticksPerThread = 100;
        Thread[] threads = new Thread[numThreads];
        
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < ticksPerThread; j++) {
                    clock.tick();
                }
            });
            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Mutex ensures all 1000 ticks are counted
        assertEquals(numThreads * ticksPerThread, clock.getTime());
    }

    @Test
    @DisplayName("Thread-safe concurrent updates with Mutex")
    public void testConcurrentUpdates() throws InterruptedException {
        final int numThreads = 10;
        Thread[] threads = new Thread[numThreads];
        
        for (int i = 0; i < numThreads; i++) {
            final long timestamp = (i + 1) * 10L;
            threads[i] = new Thread(() -> {
                clock.update(timestamp);
            });
            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        // After all updates, clock should be at least max(received) + updates
        // With 10 threads updating with timestamps 10-100, final time >= 100
        assertTrue(clock.getTime() >= 100L);
    }

    @Test
    @DisplayName("Mixed concurrent operations maintain correctness")
    public void testMixedConcurrentOps() throws InterruptedException {
        Thread ticker = new Thread(() -> {
            for (int i = 0; i < 50; i++) {
                clock.tick();
            }
        });
        
        Thread updater = new Thread(() -> {
            for (int i = 0; i < 50; i++) {
                clock.update(i * 2L);
            }
        });
        
        ticker.start();
        updater.start();
        ticker.join();
        updater.join();
        
        // Clock should be monotonically increased, exact value depends on interleaving
        // but should be at least 50 (from ticks) and consider updates
        assertTrue(clock.getTime() >= 50L);
    }
}
