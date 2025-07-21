package org.openjdbcproxy.jdbc;

import lombok.Getter;
import lombok.Setter;

public class DbInfo {
    @Getter @Setter
    private static boolean H2DB = false;
    @Getter @Setter
    private static boolean MySqlDB = false;
}
