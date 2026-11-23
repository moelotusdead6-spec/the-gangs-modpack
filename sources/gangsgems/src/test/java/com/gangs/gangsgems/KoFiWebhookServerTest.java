package com.gangs.gangsgems;

import static org.junit.Assert.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class KoFiWebhookServerTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void rejectsInvalidRequestsAndAcknowledgesOnlySavedOrders() throws Exception {
        var path = folder.getRoot().toPath().resolve("wallet.json");
        WalletStore wallet = new WalletStore(path);
        UUID player = UUID.randomUUID();
        String code = wallet.createLink(player, System.currentTimeMillis());
        var processor = new KoFiOrderProcessor("secret", Map.of(
            new KoFiOrderProcessor.Product("sku", null), new KoFiOrderProcessor.Offer(200, 100)), wallet);
        AtomicInteger notifications = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/kofi", KoFiWebhookServer.handler(processor, notifications::incrementAndGet));
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/kofi");
            String json = "{\"verification_token\":\"secret\",\"type\":\"Shop Order\",\"currency\":\"USD\","
                + "\"kofi_transaction_id\":\"txn-1\",\"amount\":\"2.00\",\"message\":\"" + code + "\","
                + "\"shop_items\":[{\"direct_link_code\":\"sku\",\"quantity\":1}]}";
            String form = "data=" + java.net.URLEncoder.encode(json, java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(415, send(client, uri, "application/json", form));
            assertEquals(400, send(client, uri, "application/x-www-form-urlencoded", form.replace("secret", "bad")));
            assertEquals(200, send(client, uri, "application/x-www-form-urlencoded", form));
            assertEquals(200, send(client, uri, "application/x-www-form-urlencoded", form));
            assertEquals(1, notifications.get());
            assertEquals(100, new WalletStore(path).balance(player));
            assertEquals(413, send(client, uri, "application/x-www-form-urlencoded", "x".repeat(65537)));
        } finally {
            server.stop(0);
        }
    }

    private static int send(HttpClient client, URI uri, String contentType, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .header("Content-Type", contentType)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
