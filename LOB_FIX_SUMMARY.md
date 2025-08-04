# Fix for Issue #23: LOB Handling Race Condition

## Problem Statement

Integration tests sometimes hang for long periods, particularly in Java 22, with the error:
```
Cannot invoke "java.sql.Blob.setBytes(long, byte[])" because "blob" is null
```

## Root Cause Analysis

The issue occurs in the LOB (Large Object) handling flow when multiple asynchronous operations access the same session concurrently:

1. **Race Condition**: Between LOB registration and retrieval in `StatementServiceImpl.createLob()`
2. **Null Pointer Exception**: `sessionManager.getLob()` returns `null` but code attempts to call `blob.setBytes()` without null checking
3. **Hanging Behavior**: Exception in async `CompletableFuture` operations doesn't properly propagate, causing tests to hang instead of failing cleanly

### Key Problematic Code Paths

1. **StatementServiceImpl.createLob()** (lines 363-365):
```java
Blob blob = sessionManager.getLob(dto.getSession(), this.lobUUID);
byte[] byteArrayData = lobDataBlock.getData().toByteArray();
bytesWritten = blob.setBytes(lobDataBlock.getPosition(), byteArrayData); // NPE here when blob is null
```

2. **SessionManagerImpl.getLob()** (line 103):
```java
return (T) this.sessionMap.get(sessionInfo.getSessionUUID()).getLob(uuid); // Can return null
```

3. **Lob.setBinaryStream()** (line 75):
```java
CompletableFuture.supplyAsync(() -> {
    this.lobReference.set(this.lobService.sendBytes(lobType, pos, in)); // Exception not properly propagated
});
```

## Solution Implementation

### 1. Added Null Checks in StatementServiceImpl

Added defensive null checks before calling `setBytes()` on Blob objects:

```java
case LT_BLOB: {
    Blob blob = sessionManager.getLob(dto.getSession(), this.lobUUID);
    if (blob == null) {
        throw new SQLException("Unable to write LOB: Blob object is null for UUID " + this.lobUUID + 
            ". This may indicate a race condition or session management issue.");
    }
    byte[] byteArrayData = lobDataBlock.getData().toByteArray();
    bytesWritten = blob.setBytes(lobDataBlock.getPosition(), byteArrayData);
    break;
}
```

### 2. Enhanced SessionManagerImpl Error Handling

Added defensive programming and logging to detect session management issues:

```java
@Override
public <T> T getLob(SessionInfo sessionInfo, String uuid) {
    Session session = this.sessionMap.get(sessionInfo.getSessionUUID());
    if (session == null) {
        log.error("Attempting to get LOB {} from null session {}", uuid, sessionInfo.getSessionUUID());
        return null;
    }
    T lob = (T) session.getLob(uuid);
    if (lob == null) {
        log.warn("LOB with UUID {} not found in session {}", uuid, sessionInfo.getSessionUUID());
    }
    return lob;
}
```

### 3. Improved Exception Propagation in Async Operations

Enhanced error handling in `Lob.setBinaryStream()` to ensure exceptions are properly propagated:

```java
CompletableFuture.supplyAsync(() -> {
    try {
        this.lobReference.set(this.lobService.sendBytes(lobType, pos, in));
    } catch (SQLException e) {
        log.error("SQLException in setBinaryStream async - sendBytes", e);
        this.lobReference.setException(e); // Ensure exception is propagated
        throw new RuntimeException(e);
    } catch (Exception e) {
        log.error("Unexpected exception in setBinaryStream async - sendBytes", e);
        this.lobReference.setException(e); // Ensure exception is propagated
        throw new RuntimeException(e);
    }
    // ... rest of the method
});
```

## Benefits of the Fix

1. **Prevents NPE**: Null checks prevent `NullPointerException` and provide descriptive error messages
2. **Stops Hanging**: Proper exception propagation ensures tests fail quickly instead of hanging
3. **Better Debugging**: Enhanced logging helps identify root causes of LOB issues
4. **Race Condition Detection**: Clear error messages indicate when race conditions occur
5. **Backward Compatible**: Changes are minimal and don't affect normal operation

## Validation

1. **Unit Tests**: Created focused tests to validate null handling scenarios
2. **Compilation**: Verified changes compile correctly with both Java 17 and Java 22
3. **Error Messages**: Confirmed descriptive error messages are generated when issues occur

## Files Modified

- `ojp-server/src/main/java/org/openjdbcproxy/grpc/server/StatementServiceImpl.java`
- `ojp-server/src/main/java/org/openjdbcproxy/grpc/server/SessionManagerImpl.java`
- `ojp-jdbc-driver/src/main/java/org/openjdbcproxy/jdbc/Lob.java`

## Risk Assessment

**Low Risk**: 
- Changes are defensive programming improvements
- Only add null checks and logging
- Do not modify core business logic
- Maintain backward compatibility
- Fail fast approach prevents hanging issues

This fix ensures that when race conditions or session management issues occur, they result in clear, immediate failures rather than hanging tests, making the system more robust and debuggable.