# Collaborative Painting Application

A real-time collaborative painting application using **distributed Lamport clocks** for conflict resolution. Built with Java server and Android client. Originally deployed on Google Cloud Platform but currently the server is closed. [Android Repository](https://github.com/Cydonia01/CollaborativePaintingApp)

## Features

- **Real-time collaboration**: Multiple users can paint simultaneously
- **Distributed Lamport Clock**: Ensures consistent ordering of events across all clients
- **Mutual Exclusion**: Thread-safe operations using Mutex implementation
- **Conflict Resolution**: Deterministic conflict handling using logical timestamps
- **Persistent Canvas**: Server saves and restores canvas state
- **Admin Commands**: Remote server management and monitoring

## Architecture

### Server (Java 21)

- **TCP Socket Server** on port 5000
- **Mutex** for thread synchronization
- **Lamport Logical Clock** for distributed event ordering
- **Priority Queue** for message ordering by timestamp
- **State Persistence** with auto-save functionality

### Client (Android/Kotlin)

- **Jetpack Compose UI** with 20×40 grid canvas
- **TCP Socket Communication** with server
- **Local Lamport Clock** synchronized with server
- **Real-time canvas updates**

## Quick Start

### Prerequisites

- Java 21 (OpenJDK or Temurin)
- Gradle 9.0+
- Android Studio (for mobile client)

### Run Server Locally

```bash
# Clone repository
git clone https://github.com/YOUR_USERNAME/collaborative-painting.git
cd collaborative-painting

# Build project
./gradlew build

# Run server
./gradlew runServer

# Server will start on localhost:5000
```

### Run Admin Client

```bash
# In another terminal
./gradlew runAdmin

# Use commands: /save, /clear, etc.
```

## Testing

```bash
# Run all tests
./gradlew test
```

### Test Coverage

- **31 tests** total
- Mutex stress testing (10,000 operations)
- Lamport clock synchronization
- Conflict resolution scenarios
- Concurrent paint operations (5 clients simultaneously)
- State persistence and recovery

## Usage

### Message Protocol

All messages follow the format: `content|type|timestamp`

**Message Types:**

- `JOIN` - Client connects to server
- `LEAVE` - Client disconnects
- `PAINT` - Paint a pixel (format: `row,col,#RRGGBB`)
- `SYNC_DATA` - Canvas state synchronization
- `CLEAR` - Clear entire canvas
- `ADMIN` - Admin commands

### Admin Commands

```
/help      - Show available commands
/stats     - Display server statistics
/clients   - List connected clients
/save      - Save canvas state to file
/clear     - Clear the canvas
```

## Technical Implementation

### Distributed Lamport Clock

- Each client maintains its own logical clock
- Clock increments before sending messages
- Server updates: `clock = max(local, received) + 1`
- Ensures total ordering of events

### Mutex

- Uses `wait()` and `notifyAll()` for condition variables
- Provides: `lock()`, `unlock()`, `await()`, `signalAll()`, `withLock()`
- Tested with 20 threads × 500 operations = 10,000 concurrent operations

### Conflict Resolution

1. **Primary**: Compare Lamport timestamps
2. **Tie-breaker**: Lexicographic comparison of colors
3. **Result**: Deterministic, consistent across all clients
