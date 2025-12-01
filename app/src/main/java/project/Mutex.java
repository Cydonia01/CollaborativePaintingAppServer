package project;

/**
 * A simple mutex lock implementation for mutual exclusion.
 * Provides exclusive access to critical sections and condition variable support.
 * 
 * <p>Features:
 * <ul>
 *   <li>Basic locking: lock() and unlock()</li>
 *   <li>Convenience method: withLock() for automatic lock management</li>
 *   <li>Condition variables: await() and signalAll() for thread coordination</li>
 * </ul>
 */
public class Mutex {
    private boolean locked = false;
    private Thread owner = null;
    
    /**
     * Acquires the mutex lock.
     * If the lock is already held by another thread, the calling thread will block until the lock becomes available.
     * 
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public synchronized void lock() throws InterruptedException {
        Thread currentThread = Thread.currentThread();
        
        while (locked && owner != currentThread) {
            wait();
        }
        
        locked = true;
        owner = currentThread;
    }
    
    /**
     * Releases the mutex lock.
     * Should only be called by the thread that currently holds the lock.
     * 
     * @throws IllegalMonitorStateException if called by a thread that doesn't own the lock
     */
    public synchronized void unlock() {
        Thread currentThread = Thread.currentThread();
        
        // Verify that the current thread owns the lock
        if (!locked) {
            throw new IllegalMonitorStateException("Attempted to unlock an unlocked mutex");
        }
        if (owner != currentThread) {
            throw new IllegalMonitorStateException(
                "Thread " + currentThread.getName() + " attempted to unlock mutex owned by " + owner.getName()
            );
        }
        
        // Release the lock
        locked = false;
        owner = null;
        
        notifyAll();
    }
    
    /**
     * Causes the current thread to wait until it is awakened by signalAll().
     * The mutex must be locked by the calling thread.
     * This temporarily releases the lock while waiting.
     * 
     * @throws InterruptedException if the thread is interrupted while waiting
     * @throws IllegalMonitorStateException if called without holding the lock
     */
    public synchronized void await() throws InterruptedException {
        Thread currentThread = Thread.currentThread();
        
        // Verify that the current thread owns the lock
        if (!locked || owner != currentThread) {
            throw new IllegalMonitorStateException("Thread must own the lock to await");
        }
        
        // Temporarily release the lock and wait
        boolean wasLocked = locked;
        Thread previousOwner = owner;
        locked = false;
        owner = null;
        
        try {
            wait();
        } finally {
            // Re-acquire the lock after waking up
            while (locked) {
                wait();
            }
            locked = wasLocked;
            owner = previousOwner;
        }
    }
    
    /**
     * Wakes up all threads waiting on this mutex's condition.
     * The mutex must be locked by the calling thread.
     * 
     * @throws IllegalMonitorStateException if called without holding the lock
     */
    public synchronized void signalAll() {
        Thread currentThread = Thread.currentThread();
        
        // Verify that the current thread owns the lock
        if (!locked || owner != currentThread) {
            throw new IllegalMonitorStateException("Thread must own the lock to signalAll");
        }
        
        notifyAll();
    }
    
    /**
     * Executes the given action while holding the mutex lock.
     * Automatically acquires the lock before executing and releases it afterward.
     * This provides convenient automatic lock management.
     * 
     * @param action the action to execute under the lock
     * @param <T> the return type
     * @return the result of the action
     * @throws RuntimeException wrapping any InterruptedException
     */
    public <T> T withLock(LockAction<T> action) {
        try {
            lock();
            try {
                return action.execute();
            } finally {
                unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while acquiring lock", e);
        }
    }
    
    /**
     * Executes the given action while holding the mutex lock (void version).
     * 
     * @param action the action to execute under the lock
     * @throws RuntimeException wrapping any InterruptedException
     */
    public void withLock(VoidLockAction action) {
        try {
            lock();
            try {
                action.execute();
            } finally {
                unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while acquiring lock", e);
        }
    }
    
    /**
     * Functional interface for actions that return a value.
     */
    @FunctionalInterface
    public interface LockAction<T> {
        T execute();
    }
    
    /**
     * Functional interface for actions that return void.
     */
    @FunctionalInterface
    public interface VoidLockAction {
        void execute();
    }
}
