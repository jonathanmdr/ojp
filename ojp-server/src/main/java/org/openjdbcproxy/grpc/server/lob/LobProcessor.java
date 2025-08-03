package org.openjdbcproxy.grpc.server.lob;

import com.openjdbcproxy.grpc.DbName;
import com.openjdbcproxy.grpc.SessionInfo;
import lombok.SneakyThrows;
import org.openjdbcproxy.grpc.server.SessionManager;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class for handling LOB (Large Object) operations.
 * Extracted from StatementServiceImpl to improve modularity.
 */
public class LobProcessor {

    /**
     * Processes a BLOB from a result set, handling database-specific logic.
     *
     * @param sessionManager The session manager for LOB registration
     * @param session       The current session
     * @param rs           The result set
     * @param columnIndex  The column index (0-based)
     * @param dbNameMap    Map of connection hash to database name
     * @return The processed BLOB value (UUID or byte array)
     * @throws SQLException if BLOB processing fails
     */
    @SneakyThrows
    public static Object treatAsBlob(SessionManager sessionManager, SessionInfo session, 
                                   ResultSet rs, int columnIndex, Map<String, DbName> dbNameMap) throws SQLException {
        Blob blob = rs.getBlob(columnIndex + 1);
        if (blob == null) {
            return null;
        }
        DbName dbName = dbNameMap.get(session.getConnHash());
        //SQL Server and DB2 must eagerly hydrate LOBs as per LOBs get invalidated once cursor moves.
        if (DbName.SQL_SERVER.equals(dbName) || DbName.DB2.equals(dbName)) {
            return blob.getBinaryStream().readAllBytes();
        }
        String lobUUID = UUID.randomUUID().toString();
        sessionManager.registerLob(session, blob, lobUUID);
        return lobUUID;
    }

    /**
     * Processes binary data from a result set, handling database-specific logic.
     *
     * @param sessionManager The session manager for LOB registration
     * @param session       The current session
     * @param dbName        The database name
     * @param rs           The result set
     * @param columnIndex  The column index (0-based)
     * @param inputStreamTypes List of input stream types
     * @return The processed binary value
     * @throws SQLException if binary processing fails
     */
    @SneakyThrows
    public static Object treatAsBinary(SessionManager sessionManager, SessionInfo session, 
                                     DbName dbName, ResultSet rs, int columnIndex, 
                                     java.util.List<String> inputStreamTypes) throws SQLException {
        int precision = rs.getMetaData().getPrecision(columnIndex + 1);
        String catalogName = rs.getMetaData().getCatalogName(columnIndex + 1);
        String colClassName = rs.getMetaData().getColumnClassName(columnIndex + 1);
        String colTypeName = rs.getMetaData().getColumnTypeName(columnIndex + 1);
        colTypeName = colTypeName != null ? colTypeName : "";
        Object binaryValue = null;
        
        if (precision == 1 && !"[B".equalsIgnoreCase(colClassName) && !"byte[]".equalsIgnoreCase(colClassName)) { 
            //it is a single byte and is not of class byte array([B)
            binaryValue = rs.getByte(columnIndex + 1);
        } else if ((org.apache.commons.lang3.StringUtils.isNotEmpty(catalogName) || 
                   "[B".equalsIgnoreCase(colClassName) || "byte[]".equalsIgnoreCase(colClassName)) &&
                   !inputStreamTypes.contains(colTypeName.toUpperCase())) {
            binaryValue = rs.getBytes(columnIndex + 1);
        } else {
            InputStream inputStream = rs.getBinaryStream(columnIndex + 1);
            if (inputStream == null) {
                return null;
            }

            //SQL Server and DB2 must eagerly hydrate LOBs as per LOBs get invalidated once cursor moves.
            if (DbName.SQL_SERVER.equals(dbName) || DbName.DB2.equals(dbName)) {
                byte[] allBytes = inputStream.readAllBytes();
                inputStream = new ByteArrayInputStream(allBytes);
            }

            binaryValue = UUID.randomUUID().toString();
            sessionManager.registerLob(session, inputStream, binaryValue.toString());
        }
        return binaryValue;
    }
}