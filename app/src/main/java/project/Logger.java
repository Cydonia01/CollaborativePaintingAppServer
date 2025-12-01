package project;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Enhanced Logger utility with timestamps, log levels, color-coded output, and file logging.
 * 
 * <p>Features:
 * <ul>
 *   <li>Multiple log levels: INFO, SUCCESS, WARN, ERROR, DEBUG, ADMIN</li>
 *   <li>Color-coded console output using ANSI escape codes</li>
 *   <li>Automatic timestamp formatting</li>
 *   <li>Dual output: console and file (server.log)</li>
 *   <li>Thread-safe synchronized methods</li>
 * </ul>
 * 
 * <p>Log Levels:
 * <ul>
 *   <li><b>INFO</b> (Cyan): General information messages</li>
 *   <li><b>SUCCESS</b> (Green): Successful operations</li>
 *   <li><b>WARN</b> (Yellow): Warning messages for potential issues</li>
 *   <li><b>ERROR</b> (Red): Error messages with optional stack traces</li>
 *   <li><b>DEBUG</b> (Purple): Detailed diagnostic information</li>
 *   <li><b>ADMIN</b> (Blue): Administrative command messages</li>
 * </ul>
 * 
 * <p>This is a utility class and cannot be instantiated.
 * 
 * @see Server
 * @see ServerApp
 */
public class Logger {
    
    // ANSI color codes for console output
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String PURPLE = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";
    
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String LOG_FILE = "server.log";
    private static boolean fileLoggingEnabled = true;
    
    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     * 
     * @throws UnsupportedOperationException always, to prevent instantiation
     */
    private Logger() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Gets the current timestamp formatted as "yyyy-MM-dd HH:mm:ss".
     * 
     * @return the formatted timestamp string
     */
    private static String getTimestamp() {
        return LocalDateTime.now().format(TIME_FORMATTER);
    }
    
    /**
     * Writes a log message to the log file.
     * Silently fails if file writing encounters an error to prevent logging from crashing the application.
     * 
     * @param message the message to write to the file
     */
    private static void writeToFile(String message) {
        if (!fileLoggingEnabled) return;
        
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println(message);
        } catch (IOException e) {
            // Silently fail - don't want logging to crash the app
        }
    }

    /**
     * Logs an INFO level message with cyan color.
     * Used for general information about server operations.
     * 
     * @param message the message to log
     */
    public static synchronized void info(String message) {
        String timestamp = getTimestamp();
        String logMessage = String.format("[%s] [INFO] %s", timestamp, message);
        System.out.println(CYAN + logMessage + RESET);
        writeToFile(logMessage);
    }

    /**
     * Logs a general message (backward compatibility).
     * Delegates to {@link #info(String)}.
     * 
     * @param message the message to log
     */
    public static synchronized void log(String message) {
        info(message);
    }
    
    /**
     * Logs a SUCCESS message with green color.
     * Used for successful completion of operations.
     * 
     * @param message the success message to log
     */
    public static synchronized void success(String message) {
        String timestamp = getTimestamp();
        String logMessage = String.format("[%s] [SUCCESS] %s", timestamp, message);
        System.out.println(GREEN + logMessage + RESET);
        writeToFile(logMessage);
    }
    
    /**
     * Logs a WARN level message with yellow color.
     * Used for potential issues that don't prevent operation but need attention.
     * 
     * @param message the warning message to log
     */
    public static synchronized void warn(String message) {
        String timestamp = getTimestamp();
        String logMessage = String.format("[%s] [WARN] %s", timestamp, message);
        System.out.println(YELLOW + logMessage + RESET);
        writeToFile(logMessage);
    }

    /**
     * Logs an ERROR level message with red color.
     * Includes optional exception stack trace.
     * 
     * @param message the error message to log
     * @param t the throwable/exception to log, or null if none
     */
    public static synchronized void error(String message, Throwable t) {
        String timestamp = getTimestamp();
        String logMessage = String.format("[%s] [ERROR] %s", timestamp, message);
        System.err.println(RED + logMessage + RESET);
        writeToFile(logMessage);
        
        if (t != null) {
            t.printStackTrace(System.err);
            writeToFile("Stack trace: " + t.toString());
        }
    }
    
    /**
     * Logs a DEBUG level message with purple color.
     * Used for detailed diagnostic information during development.
     * 
     * @param message the debug message to log
     */
    public static synchronized void debug(String message) {
        String timestamp = getTimestamp();
        String logMessage = String.format("[%s] [DEBUG] %s", timestamp, message);
        System.out.println(PURPLE + logMessage + RESET);
        writeToFile(logMessage);
    }
    
    /**
     * Logs an ADMIN command message with blue color and special prefix.
     * Used for administrative commands executed on the server.
     * 
     * @param message the admin message to log
     */
    public static synchronized void admin(String message) {
        String timestamp = getTimestamp();
        String logMessage = String.format("[%s] [ADMIN] %s", timestamp, message);
        System.out.println(BLUE + ">>> " + logMessage + RESET);
        writeToFile(logMessage);
    }
    
    /**
     * Prints a separator line for visual organization of log output.
     * Useful for grouping related log messages.
     */
    public static synchronized void separator() {
        System.out.println(WHITE + "=================================================================================" + RESET);
    }
    
    /**
     * Enables or disables file logging.
     * When disabled, logs are only printed to console.
     * 
     * @param enabled true to enable file logging, false to disable
     */
    public static void setFileLogging(boolean enabled) {
        fileLoggingEnabled = enabled;
    }
}
