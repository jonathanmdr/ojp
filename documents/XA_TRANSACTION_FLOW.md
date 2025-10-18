# XA Transaction Flow in OJP

This document describes the complete flow of a simple XA transaction through the OJP JDBC driver and OJP server, including interactions with PostgreSQL's XA implementation.

## Transaction ID (Xid) Formation

**Where:** Client-side (OJP JDBC Driver test code)
**Who Creates It:** Application/Transaction Manager (in tests: `PostgresXAIntegrationTest.createXid()`)

```java
// Client creates Xid with:
// - formatId: 1 (application-defined)
// - globalTransactionId: "global-tx-1" (unique across all transactions)
// - branchQualifier: "branch-1" (unique per resource in distributed transaction)
```

The Xid is then passed through all XA operations to identify the transaction.

## Complete XA Transaction Flow

### Phase 0: Setup (Before XA Transaction)

#### Step 1: Create XA DataSource
**Location:** Client - `OjpXADataSource`
```
Application → OjpXADataSource(url, user, password)
```
- Stores connection parameters
- No server communication yet

#### Step 2: Get XA Connection
**Location:** Client → Server gRPC call
```
Application → OjpXADataSource.getXAConnection()
  → OjpXAConnection.<init>()
    → StatementServiceGrpcClient.connect(url, user, password, isXA=true)
      → [gRPC] StatementService.connect()
        → StatementServiceImpl.connect() [SERVER]
          → DataSourceManager.getDataSource(dataSourceName)
          → createXADataSource(url) // Creates PGXADataSource
          → xaDataSource.getXAConnection(user, password) [POSTGRESQL XA]
          → connection = xaConnection.getConnection() [POSTGRESQL XA]
          → connection.setAutoCommit(true) // For non-XA operations
          → sessionManager.createXASession(sessionUUID, xaConnection, connection)
          → Returns SessionInfo(sessionUUID, isXA=true)
```

**Server State Created:**
- Session object with:
  - `sessionUUID`: Unique identifier
  - `isXA`: true
  - `xaConnection`: PostgreSQL XAConnection instance
  - `connection`: Regular JDBC connection from XAConnection
  - `xaResource`: PostgreSQL XAResource instance

**Info Stored:**
- Client: sessionInfo (UUID, isXA flag), statementService reference
- Server: Session in SessionManager map, keyed by UUID

#### Step 3: Get Connection from XA Connection
**Location:** Client-side only
```
Application → xaConnection.getConnection()
  → Returns OjpXALogicalConnection(sessionInfo, statementService, dbName)
```
- Creates logical wrapper around XA session
- No server communication
- Prevents direct commit/rollback (must use XA protocol)

#### Step 4: Get XA Resource
**Location:** Client-side only
```
Application → xaConnection.getXAResource()
  → Returns OjpXAResource(sessionInfo, statementService)
```
- Provides XA control operations
- No server communication

---

### Phase 1: XA Transaction Start

#### Step 5: Start XA Transaction
**Location:** Client → Server gRPC call
```
Application → xaResource.start(xid, TMNOFLAGS)
  → OjpXAResource.start()
    → StatementServiceGrpcClient.xaStart(sessionInfo, xid, flags)
      → [gRPC] StatementService.xaStart()
        → StatementServiceImpl.xaStart() [SERVER]
          → session = sessionManager.getSession(sessionUUID)
          → xidImpl = convertXid(xidProto) // Convert protobuf to Xid
          → session.getXaResource().start(xidImpl, flags) [POSTGRESQL XA]
```

**PostgreSQL XA Behavior:**
- Internally calls `XAConnection.getConnection()` if not already obtained
- Associates the Xid with the connection
- Sets connection to XA transaction mode (auto-commit disabled)
- Stores Xid in internal PostgreSQL XA state

**OJP Behavior:**
- Passes Xid through as-is (delegation pattern)
- No transaction state stored in OJP

**Transaction State:**
- PostgreSQL: Xid → Connection mapping active
- Connection: auto-commit OFF, in XA transaction

---

### Phase 2: Execute SQL Operations

#### Step 6: Execute SQL in XA Transaction
**Location:** Client → Server gRPC call
```
Application → connection.createStatement().executeUpdate("INSERT INTO...")
  → OjpXALogicalConnection.createStatement()
    → Inherits from Connection class
      → Statement.executeUpdate(sql)
        → StatementServiceGrpcClient.executeUpdate(sessionInfo, sql, params,...)
          → [gRPC] StatementService.executeUpdate()
            → StatementServiceImpl.executeUpdate() [SERVER]
              → session = sessionManager.getSession(sessionUUID)
              → connection = session.getConnection()
              → connection.createStatement().executeUpdate(sql) [POSTGRESQL]
```

**PostgreSQL Behavior:**
- Executes SQL within XA transaction context
- Changes are NOT committed (transaction still open)
- Changes are visible only to this connection
- PostgreSQL tracks changes internally for the Xid

**OJP Behavior:**
- Routes SQL to the XA session's connection
- No awareness of XA transaction state

---

### Phase 3: End XA Transaction Branch

#### Step 7: End XA Transaction
**Location:** Client → Server gRPC call
```
Application → xaResource.end(xid, TMSUCCESS)
  → OjpXAResource.end()
    → StatementServiceGrpcClient.xaEnd(sessionInfo, xid, flags)
      → [gRPC] StatementService.xaEnd()
        → StatementServiceImpl.xaEnd() [SERVER]
          → session = sessionManager.getSession(sessionUUID)
          → xidImpl = convertXid(xidProto)
          → session.getXaResource().end(xidImpl, flags) [POSTGRESQL XA]
```

**PostgreSQL XA Behavior:**
- Marks the transaction branch as complete
- Transaction still open, but no more work can be done
- Prepares for two-phase commit
- Stores state: "transaction branch ended, ready for prepare"

**OJP Behavior:**
- Delegates to PostgreSQL XA

---

### Phase 4: Prepare (First Phase of 2PC)

#### Step 8: Prepare XA Transaction
**Location:** Client → Server gRPC call
```
Application → xaResource.prepare(xid)
  → OjpXAResource.prepare()
    → StatementServiceGrpcClient.xaPrepare(sessionInfo, xid)
      → [gRPC] StatementService.xaPrepare()
        → StatementServiceImpl.xaPrepare() [SERVER]
          → session = sessionManager.getSession(sessionUUID)
          → xidImpl = convertXid(xidProto)
          → result = session.getXaResource().prepare(xidImpl) [POSTGRESQL XA]
          → Returns XA_OK or XA_RDONLY
```

**PostgreSQL XA Behavior:**
- Flushes all changes to disk
- Creates prepared transaction in PostgreSQL
- Transaction ID stored in pg_prepared_xacts
- Transaction can survive crashes
- Returns XA_OK if changes present, XA_RDONLY if read-only

**OJP Behavior:**
- Passes through result code

**Transaction State:**
- PostgreSQL: Transaction in "prepared" state
- Can be committed or rolled back even after server restart

---

### Phase 5: Commit (Second Phase of 2PC)

#### Step 9: Commit XA Transaction
**Location:** Client → Server gRPC call
```
Application → xaResource.commit(xid, false) // false = two-phase
  → OjpXAResource.commit()
    → StatementServiceGrpcClient.xaCommit(sessionInfo, xid, onePhase)
      → [gRPC] StatementService.xaCommit()
        → StatementServiceImpl.xaCommit() [SERVER]
          → session = sessionManager.getSession(sessionUUID)
          → xidImpl = convertXid(xidProto)
          → session.getXaResource().commit(xidImpl, onePhase) [POSTGRESQL XA]
```

**PostgreSQL XA Behavior:**
- Commits the prepared transaction
- Makes all changes permanent
- Removes entry from pg_prepared_xacts
- Transaction complete

**OJP Behavior:**
- Delegates to PostgreSQL XA

---

### Alternative: Rollback Instead of Commit

#### Step 9 (Alternative): Rollback XA Transaction
**Location:** Client → Server gRPC call
```
Application → xaResource.rollback(xid)
  → OjpXAResource.rollback()
    → StatementServiceGrpcClient.xaRollback(sessionInfo, xid)
      → [gRPC] StatementService.xaRollback()
        → StatementServiceImpl.xaRollback() [SERVER]
          → session = sessionManager.getSession(sessionUUID)
          → xidImpl = convertXid(xidProto)
          → session.getXaResource().rollback(xidImpl) [POSTGRESQL XA]
```

**PostgreSQL XA Behavior:**
- Rolls back the transaction
- Discards all changes
- Removes entry from pg_prepared_xacts
- Transaction complete, no changes committed

---

### Phase 6: Cleanup

#### Step 10: Close Connection
**Location:** Client → Server gRPC call
```
Application → connection.close()
  → OjpXALogicalConnection.close()
    → Calls super.close()
      → Connection.close()
        → StatementServiceGrpcClient.terminateSession(sessionInfo)
          → [gRPC] StatementService.terminateSession()
            → StatementServiceImpl.terminateSession() [SERVER]
              → session = sessionManager.getSession(sessionUUID)
              → session.terminate()
                → xaConnection.close() [POSTGRESQL XA] (for XA sessions)
              → sessionManager.removeSession(sessionUUID)
```

**PostgreSQL XA Behavior:**
- Closes XAConnection
- Releases database resources
- Connection no longer usable

---

## Key Points

### OJP's Role
- **Delegation Pattern**: OJP doesn't implement XA logic, it delegates to PostgreSQL XAResource
- **Session Management**: OJP manages the session lifecycle and routes operations to the correct XA session
- **Protocol Translation**: Converts between gRPC and JDBC/XA interfaces

### PostgreSQL XA's Role
- **Transaction Management**: Implements all XA protocol logic
- **Persistence**: Handles prepared transactions, crash recovery
- **State Tracking**: Maintains Xid → Connection mappings

### Transaction ID (Xid)
- **Created By**: Application/Transaction Manager (client-side)
- **Travels Through**: Client → gRPC (protobuf) → Server → PostgreSQL XA
- **Converted At**: Server (StatementServiceImpl.convertXid())
- **Used By**: PostgreSQL XA for transaction identification

### Where State is Stored

#### Client Side:
- `sessionInfo`: Contains UUID and isXA flag
- `statementService`: Reference for gRPC communication
- No transaction state

#### Server Side (OJP):
- `Session` object: Maps UUID → XAConnection, Connection, XAResource
- `SessionManager`: Maps UUID → Session
- No transaction state (delegated to PostgreSQL)

#### PostgreSQL Side:
- XA transaction state: Xid → Connection mapping
- Prepared transactions: In pg_prepared_xacts
- Transaction log: For crash recovery

### Auto-commit Behavior
1. **Initial**: `setAutoCommit(true)` when XA connection created
2. **During xaStart()**: PostgreSQL automatically sets auto-commit OFF
3. **After commit/rollback**: PostgreSQL restores previous auto-commit state

This allows DDL operations before XA transactions while ensuring XA control during transactions.
