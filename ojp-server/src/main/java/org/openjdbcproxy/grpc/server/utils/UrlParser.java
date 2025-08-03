package org.openjdbcproxy.grpc.server.utils;

import org.openjdbcproxy.constants.CommonConstants;

import static org.openjdbcproxy.grpc.server.Constants.EMPTY_STRING;

/**
 * Utility class for parsing and manipulating URLs.
 * Extracted from StatementServiceImpl to improve modularity.
 */
public class UrlParser {

    /**
     * Parses a URL by removing OJP-specific patterns.
     *
     * @param url The URL to parse
     * @return The parsed URL with OJP patterns removed
     */
    public static String parseUrl(String url) {
        if (url == null) {
            return url;
        }
        return url.replaceAll(CommonConstants.OJP_REGEX_PATTERN + "_", EMPTY_STRING);
    }
}