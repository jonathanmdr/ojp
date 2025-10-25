# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Configurable maximum concurrent XA transactions via `ojp.xa.maxTransactions` property (default: 50)
  - Client-side configuration through ojp.properties file
  - Server-side enforcement using Semaphore-based concurrency limiter
  - Per-datasource XA transaction limits (supports multi-datasource configuration)
  - Integration with slow query segregation using maxXaTransactions as pool size
  - Automatic permit release on XA commit/rollback
  - Comprehensive logging and error handling
- Configurable XA transaction start timeout via `ojp.xa.startTimeoutMillis` property (default: 60000)
  - Allows customization of how long to wait for an XA transaction slot
  - Supports per-datasource timeout configuration
  - Lower timeouts for fail-fast behavior, higher for patient applications
  
### Changed
- XA transaction lifecycle now includes concurrency control
  - `XAResource.start()` acquires a permit (blocks up to configured timeout if limit reached)
  - `XAResource.commit()` and `XAResource.rollback()` release permits
  - SQLException with state "XA001" thrown when timeout occurs

### Technical Details
- Added `XaTransactionLimiter` class for thread-safe XA concurrency management
- XA configuration now sent via properties bytes field instead of separate protobuf field
- Modified `Driver` and `OjpXAConnection` to send XA configuration as properties
- Enhanced `SessionManagerImpl` to manage XA limiters per connection hash with configurable timeout
- Instrumented XA lifecycle methods in `StatementServiceImpl`
- SlowQuerySegregationManager initialized with maxXaTransactions for XA datasources

### Documentation
- Updated ojp-jdbc-configuration.md with XA transaction limit configuration
- Added XA configuration examples with multi-datasource scenarios
- Documented monitoring and observability for XA transaction limits
- Added integration notes for slow query segregation

## [0.1.1-beta] - Previous Release
- Slow query segregation feature
