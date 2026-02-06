package com.example.tomcat;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@MultipartConfig(
    maxFileSize = 10 * 1024 * 1024,      // 10MB
    maxRequestSize = 10 * 1024 * 1024,   // 10MB
    fileSizeThreshold = 1024 * 1024       // 1MB
)
public class MultipartUploadServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Log request details
        System.out.println("\n==========================================");
        System.out.println("MULTIPART UPLOAD REQUEST");
        System.out.println("==========================================");
        System.out.println("User-Agent: " + request.getHeader("User-Agent"));
        System.out.println("Content-Type: " + request.getContentType());
        System.out.println("Content-Length: " + request.getContentLength());

        try {
            // Get the uploaded file part
            Part filePart = request.getPart("file");

            if (filePart == null) {
                sendError(response, "No file part found in multipart request");
                return;
            }

            System.out.println("Part Name: " + filePart.getName());
            System.out.println("Part Size: " + filePart.getSize());
            System.out.println("Part Content-Type: " + filePart.getContentType());


            // Read client checksum from form field
            String clientChecksum = request.getParameter("clientChecksum");
            if (clientChecksum == null) clientChecksum = "";

            // Read and validate the uploaded data, and compute SHA-256
            ValidationResult result = validateAndChecksum(filePart.getInputStream(), filePart.getSize());

            // Log results
            System.out.println("Bytes Read: " + result.bytesRead);
            System.out.println("Valid: " + result.isValid);
            System.out.println("SHA-256 (server): " + result.serverChecksum);
            System.out.println("SHA-256 (client): " + clientChecksum);
            System.out.println("==========================================\n");

            // Send JSON response
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            PrintWriter out = response.getWriter();
            out.println("{");
            out.println("  \"method\": \"multipart\",");
            out.println("  \"size\": " + result.bytesRead + ",");
            out.println("  \"expectedSize\": " + filePart.getSize() + ",");
            out.println("  \"valid\": " + result.isValid + ",");
            out.println("  \"contentType\": \"" + request.getContentType() + "\",");
            out.println("  \"serverChecksum\": \"" + result.serverChecksum + "\",");
            out.println("  \"clientChecksum\": \"" + clientChecksum + "\",");
            out.println("  \"ENABLE_HTTP2\": " + EmbeddedTomcatServer.ENABLE_HTTP2 + ",");
            out.println("  \"ENABLE_ASYNCIO\": " + EmbeddedTomcatServer.ENABLE_ASYNCIO);
            out.println("}");

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage());
        }
    }

    private ValidationResult validateAndChecksum(InputStream input, long expectedSize) throws IOException, NoSuchAlgorithmException {
        ValidationResult result = new ValidationResult();
        byte[] buffer = new byte[8192];
        int bytesRead;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        while ((bytesRead = input.read(buffer)) != -1) {
            result.bytesRead += bytesRead;
            digest.update(buffer, 0, bytesRead);
        }

        result.isValid = result.bytesRead == expectedSize;
        result.serverChecksum = bytesToHex(digest.digest());
        return result;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

        PrintWriter out = response.getWriter();
        out.println("{");
        out.println("  \"method\": \"multipart\",");
        out.println("  \"error\": \"" + message.replace("\"", "\\\"") + "\"");
        out.println("}");
    }

    private static class ValidationResult {
        long bytesRead = 0;
        boolean isValid = false;
        String serverChecksum = null;
    }
}
