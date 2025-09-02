package org.openjproxy.jdbc;

import lombok.Builder;
import lombok.Data;
import org.openjproxy.grpc.dto.OpQueryResult;

import java.sql.SQLException;

@Builder
@Data
public class FetchBlockResult {
    private OpQueryResult result;
    private SQLException exception;
}
