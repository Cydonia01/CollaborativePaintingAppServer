package project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stress tests for custom Mutex implementation under high concurrency.
 * Validates mutual exclusion, fairness, and correctness with many threads.
 */
class MutexStressTest {

    private Mutex mutex;
    
    @BeforeEach
    void setUp() {
        mutex = new Mutex();
    }
    
    @Test
    @DisplayName("Mutual exclusion with 20 threads and 500 operations each")
    void testMutualExclusion() throws InterruptedException {
        final int NUM_THREADS = 20;
        final int OPS_PER_THREAD = 500;
        
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(NUM_THREADS);
        
        for (int i = 0; i < NUM_THREADS; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < OPS_PER_THREAD; j++) {
                        mutex.lock();
                        try {
                            int value = counter.get();
                            Thread.yield(); // Force context switch
                            counter.set(value + 1);
                        } finally {
                            mutex.unlock();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }
        
        startLatch.countDown();
        doneLatch.await();
        
        // No lost updates if mutex works correctly
        assertEquals(NUM_THREADS * OPS_PER_THREAD, counter.get());
    }
    
    @Test
    @DisplayName("All threads acquire lock eventually (no starvation)")
    void testNoStarvation() throws InterruptedException {
        final int NUM_THREADS = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(NUM_THREADS);
        AtomicInteger completed = new AtomicInteger(0);
        
        for (int i = 0; i < NUM_THREADS; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    
                    mutex.lock();
                    try {
                        Thread.sleep(5);
                        completed.incrementAndGet();
                    } finally {
                        mutex.unlock();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }
        
        startLatch.countDown();
        
        // All threads should complete within reasonable time
        boolean allCompleted = doneLatch.await(30, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(allCompleted);
        assertEquals(NUM_THREADS, completed.get());
    }
    
    @Test
    @DisplayName("withLock() convenience method is thread-safe")
    void testWithLockThreadSafe() throws InterruptedException {
        final int NUM_THREADS = 10;
        final int OPS_PER_THREAD = 500;
        List<Integer> sharedList = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(NUM_THREADS);
        
        for (int i = 0; i < NUM_THREADS; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < OPS_PER_THREAD; j++) {
                        final int value = threadId * 1000 + j;
                        mutex.withLock(() -> sharedList.add(value));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }
        
        startLatch.countDown();
        doneLatch.await();
        
        // All operations should be recorded without corruption
        assertEquals(NUM_THREADS * OPS_PER_THREAD, sharedList.size());
    }
}
