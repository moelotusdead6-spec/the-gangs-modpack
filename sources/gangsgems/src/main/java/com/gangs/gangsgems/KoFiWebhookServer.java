package com.gangs.gangsgems;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;

public final class KoFiWebhookServer implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger("GangGems/KoFi");
    private final HttpsServer server;
    private final ExecutorService executor;

    public KoFiWebhookServer(InetSocketAddress address, SSLContext tls, KoFiOrderProcessor processor, Runnable onCredit)
            throws IOException {
        if (tls == null) throw new IllegalArgumentException("TLS is required");
        server = HttpsServer.create(address, 32);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
        executor = Executors.newFixedThreadPool(2);
        server.setExecutor(executor);
        server.createContext("/kofi", handler(processor, onCredit));
    }

    public void start() {
        server.start();
    }

    @Override
    public void close() {
        server.stop(1);
        executor.shutdown();
    }

    static HttpHandler handler(KoFiOrderProcessor processor, Runnable onCredit) {
        return exchange -> {
            try (exchange) {
                if (!"POST".equals(exchange.getRequestMethod())
                        || !"/kofi".equals(exchange.getRequestURI().getPath())) {
                    reply(exchange, 404, "Not found");
                    return;
                }
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                if (contentType == null
                        || !contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/x-www-form-urlencoded")) {
                    reply(exchange, 415, "Unsupported content type");
                    return;
                }
                byte[] body = exchange.getRequestBody().readNBytes(65537);
                if (body.length > 65536) {
                    reply(exchange, 413, "Request too large");
                    return;
                }
                String form;
                try {
                    form = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(body)).toString();
                } catch (CharacterCodingException failure) {
                    reply(exchange, 400, "Invalid request");
                    return;
                }
                try {
                    KoFiOrderProcessor.Result result = processor.process(form, System.currentTimeMillis());
                    if (result == KoFiOrderProcessor.Result.PENDING) {
                        LOGGER.warning("A Ko-fi order needs manual player assignment.");
                    } else if (result == KoFiOrderProcessor.Result.APPLIED) {
                        onCredit.run();
                    }
                    reply(exchange, 200, "OK");
                } catch (KoFiOrderProcessor.InvalidRequest failure) {
                    LOGGER.warning("Rejected a Ko-fi request: " + failure.getMessage());
                    reply(exchange, 400, "Invalid request");
                } catch (IllegalStateException | ArithmeticException failure) {
                    LOGGER.log(Level.SEVERE, "Conflicting Ko-fi payment or wallet overflow; investigate.", failure);
                    reply(exchange, 409, "Payment needs review");
                } catch (IOException failure) {
                    LOGGER.log(Level.SEVERE, "Could not persist a Ko-fi payment.", failure);
                    reply(exchange, 503, "Storage unavailable");
                }
            }
        };
    }

    private static void reply(HttpExchange exchange, int status, String text) throws IOException {
        byte[] response = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
    }
}
