# Oracle XA Transaction Setup

## Overview

Oracle XA (eXtended Architecture) transactions require specific database privileges and configuration. This document explains the requirements and how to set them up.

## Required Privileges

The database user connecting via XA must have the following privileges:

```sql
-- Grant SELECT privileges on XA system tables
GRANT SELECT ON sys.dba_pending_transactions TO your_user;
GRANT SELECT ON sys.pending_trans$ TO your_user;
GRANT SELECT ON sys.dba_2pc_pending TO your_user;

-- Grant EXECUTE on XA procedures
GRANT EXECUTE ON sys.dbms_system TO your_user;

-- Grant transaction recovery privileges
GRANT FORCE ANY TRANSACTION TO your_user;
```

### For Oracle 12c and later

Oracle 12c+ provides a simplified privilege:

```sql
GRANT XA_RECOVER_ADMIN TO your_user;
```

### For Development/Testing

For development and testing environments, you can grant DBA role (not recommended for production):

```sql
GRANT DBA TO your_user;
```

## Common Errors

### ORA-6550: PL/SQL error during XA start

**Cause**: The user doesn't have required privileges to access XA system tables or procedures.

**Solution**: Grant the privileges listed above.

### ORA-24756: transaction does not exist

**Cause**: XA transaction was not properly started or the user doesn't have FORCE ANY TRANSACTION privilege.

**Solution**: Ensure the user has all required XA privileges.

### ORA-24757: transaction branch does not exist

**Cause**: The user doesn't have SELECT privilege on `sys.dba_2pc_pending` (critical for XA transaction tracking).

**Solution**: Grant the missing privilege:
```sql
GRANT SELECT ON sys.dba_2pc_pending TO your_user;
```

**Important**: All XA privileges listed above must be granted. Missing even one will cause XA transactions to fail with various ORA-24xxx errors.

### ORA-02089: COMMIT is not allowed in a subordinate session

**Cause**: Using regular `Connection.commit()` instead of `XAResource.commit()` on an XA connection.

**Solution**: This is handled automatically by the OJP test framework via `TestDBUtils.ConnectionResult.commit()`.

## Testing XA Setup

To verify XA setup is correct:

```sql
-- Connect as SYSDBA and check user privileges
SELECT * FROM dba_sys_privs WHERE grantee = 'YOUR_USER';
SELECT * FROM dba_tab_privs WHERE grantee = 'YOUR_USER';

-- Should show:
-- FORCE ANY TRANSACTION
-- EXECUTE on SYS.DBMS_SYSTEM
-- SELECT on SYS.DBA_PENDING_TRANSACTIONS
-- SELECT on SYS.PENDING_TRANS$
-- SELECT on SYS.DBA_2PC_PENDING
```

## XADataSource Configuration

OJP automatically configures OracleXADataSource with the correct properties:

1. **driverType**: Set to "thin"
2. **serverName**: Extracted from JDBC URL
3. **portNumber**: Extracted from JDBC URL (default: 1521)
4. **serviceName** or **databaseName**: Extracted from JDBC URL
5. **user** and **password**: From connection details

## Running Tests

Once privileges are granted, tests using `isXA=true` in CSV files will:

1. Create `OracleXADataSource` connections
2. Start XA transactions via `XAResource.start()`
3. Execute SQL operations
4. Commit via `XAResource.commit()`
5. Properly cleanup XA resources

Example test CSV entry:
```csv
org.openjproxy.jdbc.Driver,jdbc:ojp[localhost:1059]_oracle:thin:@localhost:1521/XEPDB1,testuser,testpass,false
org.openjproxy.jdbc.Driver,jdbc:ojp[localhost:1059]_oracle:thin:@localhost:1521/XEPDB1,testuser,testpass,true
```

The first line runs in standard JDBC mode, the second in XA mode.

## Troubleshooting

If you see XA-related errors:

1. **Check user privileges**: Run the verification query above
2. **Check Oracle version**: XA support varies by version
3. **Check server logs**: Look for detailed error messages in ojp-server logs
4. **Enable XA debugging**: Set Oracle JDBC logging to FINE level

## References

- Oracle XA Documentation: https://docs.oracle.com/en/database/oracle/oracle-database/
- Oracle JDBC XA Guide: https://docs.oracle.com/en/database/oracle/oracle-database/21/jjdbc/distributed-transactions.html
