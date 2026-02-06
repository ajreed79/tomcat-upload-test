# Tomcat Safari Upload Bug Reproduction

A minimal reproducible example demonstrating data corruption when uploading 5MB files to Apache Tomcat 9.0.x using Safari browser (macOS and iOS). Uses random data with SHA-256 checksums to detect corruption.

[Apache Bugzilla #69949](https://bz.apache.org/bugzilla/show_bug.cgi?id=69949)

## Bug Description
When uploading a 5MB file via Safari browser to Tomcat 9, 10, or 11, the received data is corrupted. This bug does not occur with other browsers (Chrome, Firefox, Edge).

**Root Cause:** This issue requires three conditions to be present:
1. HTTPS (SSL/TLS) connection
2. HTTP/2 protocol enabled
3. Asynchronous I/O (`useAsyncIO=true`)

**Workarounds:**
1. Disable HTTP/2 on the connector
2. Set `useAsyncIO="false"` in the connector configuration

## Environment

- **Tomcat Version:** I've tested with:
  - 9.0.111 (embedded)
  - 10.1.52 (embedded)
  - 11.0.18 (embedded)
- **Java Version:** 11, 17, 21, or 25+ (LTS versions recommended)
  - **Note:** Gradle 9.3.1 supports Java 11 through Java 25+
- **Affected Browsers:** Safari (macOS and iOS)
  - The version doesn't seem to be that important. My iPhone is 26.2.1.
- **Test Data:** 5MB of random data validated with SHA-256 checksums
  - **Note:** I tried with all zeros and was not able to reproduce the problem

## Quick Start

### Prerequisites

- **Java 11, 17, 21, or 25+ (LTS versions recommended)** - Required
- Internet connection (first run downloads Gradle and dependencies)

### Running the Test

1. **Clone or download this repository**

2. **Check Java version:**
   ```bash
   java -version
   ```
   Ensure you're using Java 11, 17, 21, or 25+. Gradle 9.3.1 supports all these versions.

3. **Start the embedded Tomcat server:**

   **Quick start (checks Java version):**
   ```bash
   ./start.sh
   ```

   **Or run directly:**
   ```bash
   ./gradlew run
   ```

   On Windows:
   ```cmd
   gradlew.bat run
   ```

3. **Open your browser:** Navigate to http://localhost:8080

4. **Run the tests:**
   - Click "Run Both Tests" to test both upload methods
   - Or test individually: "Test Multipart Upload" and "Test Raw POST"

5. **Observe the results:**
   - **With Chrome/Firefox:** Both tests should show ✓ VALID (green)
   - **With Safari:** One or both tests show ✗ CORRUPTED (red)

## Test Methodology

This application tests two different upload methods to isolate where the corruption occurs:

### 1. Multipart Upload (Traditional File Upload)
- Uses `multipart/form-data` encoding
- Tests Tomcat's multipart parser (`javax.servlet.http.Part`)
- Typical for HTML file upload forms

### 2. Raw POST (Direct Binary Upload)
- Sends binary data directly in request body
- Uses `application/octet-stream` content type
- Bypasses multipart parsing

The test generates 5MB of random data in the browser's JavaScript, computes its SHA-256 checksum, and uploads it via both methods. The server computes the checksum of the received data and compares it with the client's checksum to detect any corruption.

## Expected vs Actual Results

### Expected (Chrome, Firefox, Edge)
```
Multipart Upload: ✓ VALID (Checksums match)
  Bytes Sent: 5,242,880
  Bytes Received: 5,242,880
  Client Checksum: a1b2c3...
  Server Checksum: a1b2c3...

Raw POST: ✓ VALID (Checksums match)
  Bytes Sent: 5,242,880
  Bytes Received: 5,242,880
  Client Checksum: d4e5f6...
  Server Checksum: d4e5f6...
```

### Actual (Safari with HTTP/2 + AsyncIO)
```
Multipart Upload: ✗ CORRUPTED
  Bytes Sent: 5,242,880
  Bytes Received: 5,242,880
  Client Checksum: a1b2c3...
  Server Checksum: g7h8i9... (mismatch)

Raw POST: [varies - test to determine]
  Bytes Sent: 5,242,880
  Bytes Received: 5,242,880
  Client Checksum: d4e5f6...
  Server Checksum: [varies or matches]
```

If **both methods fail** with Safari, the bug is in Tomcat's core request body handling.
If **only multipart fails**, the bug is in Tomcat's multipart parser.

## Server Console Output

The server logs detailed information for each upload:

```
==========================================
MULTIPART UPLOAD REQUEST
==========================================
User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X ...) AppleWebKit/... Safari/...
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary...
Content-Length: 5243040
Part Name: file
Part Size: 5242880
Part Content-Type: application/octet-stream
Bytes Read: 5242880
Valid: true
SHA-256 (server): a1b2c3d4e5f6...
SHA-256 (client): a1b2c3d4e5f6...
==========================================
```

## Project Structure

```
tomcat-upload-test/
├── build.gradle                          # Gradle build configuration
├── settings.gradle                       # Project settings
├── src/
│   └── main/
│       ├── java/com/example/tomcat/
│       │   ├── EmbeddedTomcatServer.java      # Main server launcher
│       │   ├── MultipartUploadServlet.java    # Handles multipart uploads
│       │   └── RawUploadServlet.java          # Handles raw POST uploads
│       └── resources/
│           └── index.html                     # Test interface
└── README.md                             # This file
```

## Technical Details

### Embedded Tomcat Configuration
- Port: 8080 (HTTP), 8443 (HTTPS)
- Context path: /
- HTTP/2: Configurable via `ENABLE_HTTP2` in `EmbeddedTomcatServer.java` (default: true)
- Async I/O: Configurable via `ENABLE_ASYNCIO` in `EmbeddedTomcatServer.java` (default: true)
- Multipart settings:
  - Max file size: 10MB
  - Max request size: 10MB
  - File size threshold: 1MB

### Testing Configuration

To reproduce the Safari bug, edit [EmbeddedTomcatServer.java](src/main/java/com/example/tomcat/EmbeddedTomcatServer.java):

```java
public static final Boolean ENABLE_HTTP2 = true;   // Enable HTTP/2
public static final Boolean ENABLE_ASYNCIO = true;  // Enable Async I/O
```

**Important:** The bug only occurs when **both** HTTP/2 and Async I/O are enabled simultaneously on HTTPS. The web interface displays these settings in the test results.

**Workarounds:**
- Set `ENABLE_HTTP2 = false` to disable HTTP/2, OR
- Set `ENABLE_ASYNCIO = false` to disable Async I/O

Either workaround prevents the corruption in Safari.

### Servlets
- **MultipartUploadServlet** (`/upload/multipart`)
  - Configured with `@MultipartConfig`
  - Reads uploaded file via `request.getPart("file")`
  - Receives client checksum as form field
  - Computes SHA-256 checksum of uploaded data
  - Returns both checksums for comparison

- **RawUploadServlet** (`/upload/raw`)
  - Reads binary body directly from `request.getInputStream()`
  - Receives client checksum via `X-Client-Checksum` header
  - Computes SHA-256 checksum of uploaded data
  - Returns both checksums for comparison

### Validation Logic
Both servlets:
1. Read uploaded data in 8KB chunks
2. Compute SHA-256 checksum as data is read
3. Compare server checksum with client checksum
4. Return JSON with validation results including both checksums

## License

This project is licensed under the [MIT License](LICENSE).
