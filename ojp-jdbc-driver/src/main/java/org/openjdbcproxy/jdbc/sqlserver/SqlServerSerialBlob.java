package org.openjdbcproxy.jdbc.sqlserver;

import javax.sql.rowset.serial.SerialBlob;
import javax.sql.rowset.serial.SerialException;
import java.sql.Blob;
import java.sql.SQLException;

/**
 * Handle SqlServer BLOB in memory.
 * Issue open: https://github.com/Open-JDBC-Proxy/ojp/issues/19.
 */
public class SqlServerSerialBlob extends SerialBlob {
    public SqlServerSerialBlob(byte[] b) throws SerialException, SQLException {
        super(b);
    }

    public SqlServerSerialBlob(Blob blob) throws SerialException, SQLException {
        super(blob);
    }

    @Override
    public byte[] getBytes(long pos, int length) throws SerialException {
        if (length == 0) {
            return new byte[0];
        } else {
            return super.getBytes(pos, length);
        }
    }
}
