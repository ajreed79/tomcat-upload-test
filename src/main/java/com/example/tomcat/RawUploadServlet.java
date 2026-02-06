package com.example.tomcat;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class RawUploadServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Log request details
        System.out.println("\n==========================================");
        System.out.println("RAW POST REQUEST");
        System.out.println("==========================================");
        System.out.println("User-Agent: " + request.getHeader("User-Agent"));
        System.out.println("Content-Type: " + request.getContentType());
        System.out.println("Content-Length: " + request.getContentLength());

        try {

            int contentLength = request.getContentLength();

            // Read client checksum from header
            String clientChecksum = request.getHeader("X-Client-Checksum");
            if (clientChecksum == null) clientChecksum = "";

            // Read and validate the uploaded data, and compute SHA-256
            ValidationResult result = validateAndChecksum(request.getInputStream(), contentLength);

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
            out.println("  \"method\": \"raw\",");
            out.println("  \"size\": " + result.bytesRead + ",");
            out.println("  \"expectedSize\": " + contentLength + ",");
            out.println("  \"valid\": " + result.isValid + ",");
            out.println("  \"contentLength\": " + contentLength + ",");
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
        out.println("  \"method\": \"raw\",");
        out.println("  \"error\": \"" + message.replace("\"", "\\\"") + "\"");
        out.println("}");
    }

    private static class ValidationResult {
        long bytesRead = 0;
        boolean isValid = false;
        String serverChecksum = null;
    }
}
