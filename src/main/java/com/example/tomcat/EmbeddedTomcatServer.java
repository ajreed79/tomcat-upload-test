package com.example.tomcat;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.coyote.http2.Http2Protocol;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;

import jakarta.servlet.MultipartConfigElement;
import java.io.File;

public class EmbeddedTomcatServer {

    public static final Boolean ENABLE_HTTP2 = true; // Set to false to disable HTTP/2 to avoid Safari upload issues
    public static final Boolean ENABLE_ASYNCIO = true; // Set to false to disable Async I/O to avoid Safari upload issues

    private static final int HTTP_PORT = 8080;
    private static final int HTTPS_PORT = 8443;
    private static final String CONTEXT_PATH = "";

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================");
        System.out.println("  Tomcat Safari Upload Bug Reproduction Server");
        System.out.println("=================================================");
        System.out.println("Starting embedded Tomcat 11.0.18 with HTTPS and HTTP/2...");

        Tomcat tomcat = new Tomcat();

        // Configure HTTPS connector with HTTP/2
        tomcat.setPort(HTTPS_PORT);
        Connector httpsConnector = tomcat.getConnector();
        httpsConnector.setScheme("https");
        httpsConnector.setSecure(true);
        httpsConnector.setProperty("SSLEnabled", "true");
        httpsConnector.setProperty("defaultSSLHostConfigName", "_default_");

        // Set keystore location
        File keystoreFile = new File("keystore.jks").getAbsoluteFile();
        SSLHostConfig sslHostConfig = new SSLHostConfig();
        sslHostConfig.setHostName("_default_");

        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(sslHostConfig, SSLHostConfigCertificate.Type.RSA);
        certificate.setCertificateKeystoreFile(keystoreFile.getAbsolutePath());
        certificate.setCertificateKeystorePassword("changeit");
        certificate.setCertificateKeyPassword("changeit");
        certificate.setCertificateKeystoreType("JKS");
        certificate.setCertificateKeyAlias("tomcat");
        sslHostConfig.addCertificate(certificate);
        httpsConnector.addSslHostConfig(sslHostConfig);

        if (ENABLE_ASYNCIO) {
            System.out.println("✓ Async I/O is enabled");
        }
        else {
            System.out.println("✗ Async I/O is disabled to avoid Safari upload issues");
            httpsConnector.setProperty("useAsyncIO", "false");
        }

        if (ENABLE_HTTP2) {
            System.out.println("✓ HTTP/2 is enabled");
            final Http2Protocol http2Protocol = new Http2Protocol();
            httpsConnector.addUpgradeProtocol(http2Protocol);
        }
        else {
            System.out.println("✗ HTTP/2 is disabled to avoid Safari upload issues");
        }

        // Add HTTP connector that redirects to HTTPS
        Connector httpConnector = new Connector(Http11NioProtocol.class.getName());
        httpConnector.setPort(HTTP_PORT);
        httpConnector.setScheme("http");
        httpConnector.setRedirectPort(HTTPS_PORT);
        tomcat.getService().addConnector(httpConnector);

        // Create context
        String webappDir = new File("src/main/resources").getAbsolutePath();
        Context context = tomcat.addContext(CONTEXT_PATH, webappDir);

        // Register MultipartUploadServlet
        String multipartServletName = "MultipartUploadServlet";
        Tomcat.addServlet(context, multipartServletName, new MultipartUploadServlet());
        context.addServletMappingDecoded("/upload/multipart", multipartServletName);

        // Configure multipart for the servlet
        MultipartConfigElement multipartConfig = new MultipartConfigElement(
            System.getProperty("java.io.tmpdir"),  // location
            10 * 1024 * 1024,                       // maxFileSize (10MB)
            10 * 1024 * 1024,                       // maxRequestSize (10MB)
            1024 * 1024                             // fileSizeThreshold (1MB)
        );
        ((Wrapper) context.findChild(multipartServletName)).setMultipartConfigElement(multipartConfig);

        // Register RawUploadServlet
        Tomcat.addServlet(context, "RawUploadServlet", new RawUploadServlet());
        context.addServletMappingDecoded("/upload/raw", "RawUploadServlet");

        // Add default servlet for static content
        Tomcat.addServlet(context, "default", new DefaultServlet());
        context.addServletMappingDecoded("/", "default");

        // Configure welcome file
        context.addWelcomeFile("index.html");

        // Add security constraint to redirect HTTP to HTTPS
        SecurityConstraint securityConstraint = new SecurityConstraint();
        securityConstraint.setUserConstraint("CONFIDENTIAL");
        SecurityCollection collection = new SecurityCollection();
        collection.addPattern("/*");
        securityConstraint.addCollection(collection);
        context.addConstraint(securityConstraint);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down Tomcat...");
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (LifecycleException e) {
                System.err.println("Error during shutdown: " + e.getMessage());
            }
        }));

        // Start server
        tomcat.start();
        System.out.println("\n✓ Server started successfully!");
        System.out.println("✓ HTTPS: https://localhost:" + HTTPS_PORT);
        System.out.println("✓ HTTP:  http://localhost:" + HTTP_PORT + " (redirects to HTTPS)");
        System.out.println("✓ Press Ctrl+C to stop\n");

        tomcat.getServer().await();
    }
}
