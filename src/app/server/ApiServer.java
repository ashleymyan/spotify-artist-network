package app.server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import app.service.ArtistConnectionService;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class ApiServer {

    private final int port;
    private final ArtistConnectionService service;
    private HttpServer httpServer;

    public ApiServer(int port, String apiKey) {
        this.port = port;
        // Single shared service instance so the graph cache is preserved across requests
        this.service = new ArtistConnectionService(apiKey);
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);

        // Serve the frontend
        httpServer.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.equals("/index.html")) {
                serveIndexHtml(exchange);
            } else {
                sendResponse(exchange, 404, "text/plain", "Not found");
            }
        });

        // API endpoint
        httpServer.createContext("/api/path", new PathHandler(service));

        httpServer.setExecutor(Executors.newFixedThreadPool(4));
        httpServer.start();

        System.out.println("=================================================");
        System.out.println("  Artist Network server running on port " + port);
        System.out.println("  Open http://localhost:" + port + " in your browser");
        System.out.println("=================================================");
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    private void serveIndexHtml(HttpExchange exchange) throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/index.html")) {
            if (is == null) {
                String error = "<html><body><h1>index.html not found</h1>"
                        + "<p>Make sure src/resources/index.html is on the classpath.</p></body></html>";
                sendResponse(exchange, 500, "text/html; charset=utf-8", error);
                return;
            }
            byte[] bytes = is.readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        }
    }


    static void sendResponse(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType + "; charset=utf-8");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }
}