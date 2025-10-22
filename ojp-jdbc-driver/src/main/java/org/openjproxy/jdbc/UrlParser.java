package org.openjproxy.jdbc;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for parsing OJP JDBC URLs.
 * Shared by Driver and XA components.
 */
@Slf4j
public class UrlParser {
    
    /**
     * Result class for URL parsing.
     */
    public static class UrlParseResult {
        public final String cleanUrl;
        public final String dataSourceName;
        
        public UrlParseResult(String cleanUrl, String dataSourceName) {
            this.cleanUrl = cleanUrl;
            this.dataSourceName = dataSourceName;
        }
    }
    
    /**
     * Parses the URL to extract dataSource parameter from the OJP section and return clean URL.
     * 
     * <p>Example transformations:
     * <ul>
     *   <li>Input: {@code jdbc:ojp[localhost:1059(webApp)]_postgresql://localhost/mydb}</li>
     *   <li>Output: {@code jdbc:ojp[localhost:1059]_postgresql://localhost/mydb}, dataSource: "webApp"</li>
     * </ul>
     * <ul>
     *   <li>Input: {@code jdbc:ojp[localhost:1059]_h2:mem:test}</li>
     *   <li>Output: {@code jdbc:ojp[localhost:1059]_h2:mem:test}, dataSource: "default"</li>
     * </ul>
     * 
     * @param url the original JDBC URL
     * @return UrlParseResult containing the cleaned URL (with dataSource removed) and the extracted dataSource name
     */
    public static UrlParseResult parseUrlWithDataSource(String url) {
        if (url == null) {
            return new UrlParseResult(url, "default");
        }
        
        // Look for the OJP section: jdbc:ojp[host:port(dataSource)]_
        if (!url.startsWith("jdbc:ojp[")) {
            return new UrlParseResult(url, "default");
        }
        
        int bracketStart = url.indexOf('[');
        int bracketEnd = url.indexOf(']');
        
        if (bracketStart == -1 || bracketEnd == -1) {
            return new UrlParseResult(url, "default");
        }
        
        String ojpSection = url.substring(bracketStart + 1, bracketEnd);
        
        // Look for dataSource in parentheses: host:port(dataSource)
        int parenStart = ojpSection.indexOf('(');
        int parenEnd = ojpSection.lastIndexOf(')');
        
        String dataSourceName = "default";
        String cleanOjpSection = ojpSection;
        
        if (parenStart != -1 && parenEnd != -1 && parenEnd > parenStart) {
            // Extract dataSource name from parentheses and trim whitespace
            dataSourceName = ojpSection.substring(parenStart + 1, parenEnd).trim();
            // Remove the dataSource part from OJP section
            cleanOjpSection = ojpSection.substring(0, parenStart);
        }
        
        // Reconstruct the URL without the dataSource part
        String cleanUrl = "jdbc:ojp[" + cleanOjpSection + "]" + url.substring(bracketEnd + 1);
        
        log.debug("Parsed URL - input: {}, clean: {}, dataSource: {}", url, cleanUrl, dataSourceName);
        
        return new UrlParseResult(cleanUrl, dataSourceName);
    }
}
