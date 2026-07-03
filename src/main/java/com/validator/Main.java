package com.validator;

import com.validator.server.ValidationServer;

import java.io.IOException;

public final class Main {

    private Main() {}

    public static void main(String[] args) throws IOException {
        int port = resolvePort(args);
        ValidationServer server;
        try {
            server = ValidationServer.start(port);
        } catch (IllegalStateException e) {
            // Missing/invalid auth configuration — fail fast with a clear message.
            System.err.println("FATAL: could not start server — auth configuration is invalid.");
            System.err.println("  " + e.getMessage());
            System.err.println("  Set GOOGLE_CLIENT_ID + SESSION_SECRET, "
                    + "or DEV_BYPASS_AUTH=true (and COOKIE_SECURE=false) for local dev.");
            System.exit(1);
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down...");
            server.stop();
        }));
        System.out.println("PAN / Aadhaar Format Validator");
        System.out.println("  App:    http://localhost:" + port);
        System.out.println("  Health: http://localhost:" + port + "/api/health");
        System.out.println("Press Ctrl+C to stop.");
    }

    private static int resolvePort(String[] args) {
        String env = System.getenv("PORT");
        if (env != null && !env.isBlank()) {
            try {
                return Integer.parseInt(env.trim());
            } catch (NumberFormatException ignore) {
            }
        }
        if (args.length > 0) {
            try {
                return Integer.parseInt(args[0]);
            } catch (NumberFormatException ignore) {
            }
        }
        return 8080;
    }
}
