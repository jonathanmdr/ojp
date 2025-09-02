package org.openjproxy.grpc.server;

import com.openjproxy.grpc.DbName;
import com.openjproxy.grpc.SessionInfo;
import lombok.Builder;
import lombok.Getter;

import java.sql.Connection;

@Getter
@Builder
public class ConnectionSessionDTO {
    private Connection connection;
    private SessionInfo session;
    private DbName dbName;
}
