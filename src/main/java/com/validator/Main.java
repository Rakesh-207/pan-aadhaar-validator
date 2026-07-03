package com.validator;

import com.validator.server.ValidationServer;

import java.io.IOException;

public final class Main {

    private Main() {}

    public static void main(String[] args) throws IOException {
        int port = resolvePort(args);
        ValidationServer server = ValidationServer.start(port);
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
