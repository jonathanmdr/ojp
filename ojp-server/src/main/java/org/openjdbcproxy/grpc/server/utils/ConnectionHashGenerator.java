package org.openjdbcproxy.grpc.server.utils;

import com.openjdbcproxy.grpc.ConnectionDetails;

import java.security.MessageDigest;

import static org.openjdbcproxy.grpc.server.Constants.SHA_256;

/**
 * Utility class for generating connection hashes.
 * Extracted from StatementServiceImpl to improve modularity.
 */
public class ConnectionHashGenerator {

    /**
     * Generates a hash for connection details using SHA-256.
     *
     * @param connectionDetails The connection details to hash
     * @return Hash string for the connection details
     * @throws RuntimeException if hashing fails
     */
    public static String hashConnectionDetails(ConnectionDetails connectionDetails) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(SHA_256);
            messageDigest.update((connectionDetails.getUrl() + connectionDetails.getUser() + connectionDetails.getPassword())
                    .getBytes());
            return new String(messageDigest.digest());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate connection hash", e);
        }
    }
}