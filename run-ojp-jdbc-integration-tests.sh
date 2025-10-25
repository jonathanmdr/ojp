#!/bin/bash
#
# run-ojp-jdbc-integration-tests.sh
#
# This script runs ojp-jdbc integration tests twice:
# 1. Once with tests that use XA mode (via OjpXADataSource)
# 2. Once with tests that use pure JDBC mode (regular JDBC connections)
#
# The script generates separate test reports for each mode and produces a summary.
#
# Usage:
#   ./run-ojp-jdbc-integration-tests.sh
#
# Requirements:
#   - Maven installed and available in PATH
#   - OJP server running on localhost:1059 (REQUIRED for integration tests to pass)
#     To start the server: docker run --rm -d --network host rrobetti/ojp:latest
#     Or build and run from source: cd ojp-server && mvn exec:java
#   - Database(s) configured and accessible (PostgreSQL, H2, etc.)
#     Most tests use H2 (embedded), but some require external databases
#
# Missing Dependencies Detection:
#   If tests fail with "UNAVAILABLE: io exception", it means the OJP server is not running.
#   Start the server and re-run this script.
#
# Output:
#   - Test reports saved to ./test-reports/ojp-jdbc/xa and ./test-reports/ojp-jdbc/jdbc
#   - Summary saved to ./test-reports/ojp-jdbc/summary.json
#

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Configuration
MODULE="ojp-jdbc-driver"
REPORT_BASE_DIR="test-reports/ojp-jdbc"
XA_REPORT_DIR="$REPORT_BASE_DIR/xa"
JDBC_REPORT_DIR="$REPORT_BASE_DIR/jdbc"
SUMMARY_FILE="$REPORT_BASE_DIR/summary.json"

echo "=================================================="
echo "OJP JDBC Integration Tests Runner"
echo "=================================================="
echo ""

# Detect build tool
if [ ! -f "pom.xml" ]; then
    echo -e "${RED}ERROR: pom.xml not found. This script requires Maven.${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Detected build tool: Maven${NC}"
echo ""

# Check if module exists
if [ ! -d "$MODULE" ]; then
    echo -e "${RED}ERROR: Module '$MODULE' not found${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Found module: $MODULE${NC}"
echo ""

# Create report directories
mkdir -p "$XA_REPORT_DIR"
mkdir -p "$JDBC_REPORT_DIR"

echo "Report directories:"
echo "  - XA mode:   $XA_REPORT_DIR"
echo "  - JDBC mode: $JDBC_REPORT_DIR"
echo ""

# Check if OJP server is running
echo "Checking for OJP server on localhost:1059..."
if command -v nc &> /dev/null; then
    if nc -z localhost 1059 2>/dev/null; then
        echo -e "${GREEN}✓ OJP server appears to be running${NC}"
    else
        echo -e "${YELLOW}⚠ WARNING: OJP server does not appear to be running on localhost:1059${NC}"
        echo "  Many integration tests require a running server and will fail without it."
        echo "  To start the server:"
        echo "    - Docker: docker run --rm -d --network host rrobetti/ojp:latest"
        echo "    - From source: cd ojp-server && mvn exec:java"
        echo ""
        read -p "Continue anyway? (y/N) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            echo "Aborting."
            exit 1
        fi
    fi
else
    echo -e "${YELLOW}⚠ 'nc' command not found, skipping server check${NC}"
fi
echo ""

# Function to run tests and capture results
run_tests() {
    local mode=$1
    local test_pattern=$2
    local report_dir=$3
    local system_props=$4
    
    echo "=================================================="
    echo "Running tests in $mode mode"
    echo "=================================================="
    echo "Test pattern: $test_pattern"
    echo "System properties: $system_props"
    echo ""
    
    # Clean previous build artifacts for clean test run
    echo "Cleaning previous build artifacts..."
    mvn clean -pl "$MODULE" -q
    
    # Run tests
    set +e  # Don't exit on test failures
    
    local mvn_command="mvn -pl $MODULE -am test $system_props -DskipTests=false -DfailIfNoTests=false -Dmaven.test.failure.ignore=false -Dtest=$test_pattern"
    echo "Executing: $mvn_command"
    echo ""
    
    $mvn_command 2>&1 | tee "$report_dir/test-output.log"
    local exit_code=$?
    
    set -e
    
    # Copy surefire reports
    if [ -d "$MODULE/target/surefire-reports" ]; then
        echo "Copying test reports to $report_dir..."
        cp -r "$MODULE/target/surefire-reports"/* "$report_dir/" 2>/dev/null || true
    else
        echo -e "${YELLOW}Warning: No surefire reports found${NC}"
    fi
    
    echo ""
    echo "Test execution completed with exit code: $exit_code"
    echo ""
    
    return $exit_code
}

# Function to parse test results
parse_test_results() {
    local report_dir=$1
    local mode=$2
    
    local total=0
    local passed=0
    local failed=0
    local errors=0
    local skipped=0
    
    # Parse XML test reports
    if [ -d "$report_dir" ]; then
        for xml_file in "$report_dir"/TEST-*.xml; do
            if [ -f "$xml_file" ]; then
                # Extract test counts from XML (cross-platform compatible)
                local file_tests=$(grep -o 'tests="[0-9]*"' "$xml_file" | head -1 | grep -o '[0-9]*' || echo "0")
                local file_failures=$(grep -o 'failures="[0-9]*"' "$xml_file" | head -1 | grep -o '[0-9]*' || echo "0")
                local file_errors=$(grep -o 'errors="[0-9]*"' "$xml_file" | head -1 | grep -o '[0-9]*' || echo "0")
                local file_skipped=$(grep -o 'skipped="[0-9]*"' "$xml_file" | head -1 | grep -o '[0-9]*' || echo "0")
                
                total=$((total + file_tests))
                failed=$((failed + file_failures))
                errors=$((errors + file_errors))
                skipped=$((skipped + file_skipped))
            fi
        done
    fi
    
    passed=$((total - failed - errors - skipped))
    
    echo "$mode|$total|$passed|$failed|$errors|$skipped"
}

# Function to extract failure details
extract_failures() {
    local report_dir=$1
    local output_file=$2
    
    echo "[" > "$output_file"
    local first=true
    
    if [ -d "$report_dir" ]; then
        for xml_file in "$report_dir"/TEST-*.xml; do
            if [ -f "$xml_file" ]; then
                # Extract test class name
                local test_class=$(basename "$xml_file" .xml | sed 's/TEST-//')
                
                # Find failed or error test cases
                grep -A 20 '<failure\|<error' "$xml_file" | while IFS= read -r line; do
                    if [[ "$line" =~ \<testcase.*name=\"([^\"]+)\" ]]; then
                        local test_name="${BASH_REMATCH[1]}"
                        if [ "$first" = false ]; then
                            echo "," >> "$output_file"
                        fi
                        first=false
                        echo "  {" >> "$output_file"
                        echo "    \"class\": \"$test_class\"," >> "$output_file"
                        echo "    \"test\": \"$test_name\"," >> "$output_file"
                        echo "    \"message\": \"See full report for details\"" >> "$output_file"
                        echo "  }" >> "$output_file"
                    fi
                done
            fi
        done
    fi
    
    echo "]" >> "$output_file"
}

# Run XA tests
echo "=================================================="
echo "PHASE 1: Running XA Mode Tests"
echo "=================================================="
echo ""

xa_exit_code=0
run_tests "XA" "*XA*Test" "$XA_REPORT_DIR" "-DdisablePostgresXATests=false -Dsurefire.failIfNoSpecifiedTests=false" || xa_exit_code=$?

# Run JDBC tests (all other integration tests - not XA specific)
echo ""
echo "=================================================="
echo "PHASE 2: Running JDBC Mode Tests"  
echo "=================================================="
echo ""

jdbc_exit_code=0
# Run all tests except XA tests
run_tests "JDBC" "!*XA*Test" "$JDBC_REPORT_DIR" "-Dsurefire.failIfNoSpecifiedTests=false" || jdbc_exit_code=$?

# Parse results
echo ""
echo "=================================================="
echo "Parsing Test Results"
echo "=================================================="
echo ""

xa_results=$(parse_test_results "$XA_REPORT_DIR" "XA")
jdbc_results=$(parse_test_results "$JDBC_REPORT_DIR" "JDBC")

IFS='|' read -r xa_mode xa_total xa_passed xa_failed xa_errors xa_skipped <<< "$xa_results"
IFS='|' read -r jdbc_mode jdbc_total jdbc_passed jdbc_failed jdbc_errors jdbc_skipped <<< "$jdbc_results"

# Extract failure details
extract_failures "$XA_REPORT_DIR" "$XA_REPORT_DIR/failures.json"
extract_failures "$JDBC_REPORT_DIR" "$JDBC_REPORT_DIR/failures.json"

# Generate summary JSON
cat > "$SUMMARY_FILE" <<EOF
{
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "module": "$MODULE",
  "modes": {
    "xa": {
      "total": $xa_total,
      "passed": $xa_passed,
      "failed": $xa_failed,
      "errors": $xa_errors,
      "skipped": $xa_skipped,
      "status": "$( [ $xa_exit_code -eq 0 ] && echo "SUCCESS" || echo "FAILURE" )",
      "report_path": "$XA_REPORT_DIR",
      "failures_path": "$XA_REPORT_DIR/failures.json"
    },
    "jdbc": {
      "total": $jdbc_total,
      "passed": $jdbc_passed,
      "failed": $jdbc_failed,
      "errors": $jdbc_errors,
      "skipped": $jdbc_skipped,
      "status": "$( [ $jdbc_exit_code -eq 0 ] && echo "SUCCESS" || echo "FAILURE" )",
      "report_path": "$JDBC_REPORT_DIR",
      "failures_path": "$JDBC_REPORT_DIR/failures.json"
    }
  }
}
EOF

# Print summary
echo ""
echo "=================================================="
echo "TEST EXECUTION SUMMARY"
echo "=================================================="
echo ""
echo "XA Mode Tests:"
echo "  Total:   $xa_total"
echo "  Passed:  $xa_passed"
echo "  Failed:  $xa_failed"
echo "  Errors:  $xa_errors"
echo "  Skipped: $xa_skipped"
if [ $xa_exit_code -eq 0 ]; then
    echo -e "  Status:  ${GREEN}SUCCESS${NC}"
else
    echo -e "  Status:  ${RED}FAILURE${NC}"
fi
echo ""
echo "JDBC Mode Tests:"
echo "  Total:   $jdbc_total"
echo "  Passed:  $jdbc_passed"
echo "  Failed:  $jdbc_failed"
echo "  Errors:  $jdbc_errors"
echo "  Skipped: $jdbc_skipped"
if [ $jdbc_exit_code -eq 0 ]; then
    echo -e "  Status:  ${GREEN}SUCCESS${NC}"
else
    echo -e "  Status:  ${RED}FAILURE${NC}"
fi
echo ""
echo "Report Locations:"
echo "  XA Reports:   $XA_REPORT_DIR"
echo "  JDBC Reports: $JDBC_REPORT_DIR"
echo "  Summary:      $SUMMARY_FILE"
echo ""

# Final exit code
if [ $xa_exit_code -ne 0 ] || [ $jdbc_exit_code -ne 0 ]; then
    echo -e "${RED}OVERALL STATUS: FAILURE${NC}"
    echo "One or more test modes failed. See reports for details."
    exit 1
else
    echo -e "${GREEN}OVERALL STATUS: SUCCESS${NC}"
    echo "All tests passed!"
    exit 0
fi
