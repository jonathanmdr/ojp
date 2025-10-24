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
  - Timeout-based blocking (60 seconds default) when limit is reached
  - Comprehensive logging and error handling
  
### Changed
- XA transaction lifecycle now includes concurrency control
  - `XAResource.start()` acquires a permit (blocks if limit reached)
  - `XAResource.commit()` and `XAResource.rollback()` release permits
  - SQLException with state "XA001" thrown when timeout occurs

### Technical Details
- Added `XaTransactionLimiter` class for thread-safe XA concurrency management
- Updated `ConnectionDetails` protobuf message with `maxXaTransactions` field
- Modified `Driver` and `OjpXAConnection` to send maxXaTransactions configuration
- Enhanced `SessionManagerImpl` to manage XA limiters per connection hash
- Instrumented XA lifecycle methods in `StatementServiceImpl`
- SlowQuerySegregationManager initialized with maxXaTransactions for XA datasources

### Documentation
- Updated ojp-jdbc-configuration.md with XA transaction limit configuration
- Added XA configuration examples with multi-datasource scenarios
- Documented monitoring and observability for XA transaction limits
- Added integration notes for slow query segregation

## [0.1.1-beta] - Previous Release
- Initial multi-datasource support
- XA transaction pass-through implementation
- Atomikos integration
- Slow query segregation feature
