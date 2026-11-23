package com.gangs.gangsgems;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import net.fabricmc.loader.api.FabricLoader;

final class WebhookConfiguration {
    private WebhookConfiguration() {}

    static KoFiWebhookServer start(WalletStore wallet, Runnable onCredit) throws IOException {
        Path config = FabricLoader.getInstance().getConfigDir().resolve("gangsgems.json");
        if (!Files.exists(config)) return null;
        try (var reader = Files.newBufferedReader(config)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("enabled") || !root.get("enabled").isJsonPrimitive()
                    || !root.get("enabled").getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException("Gang Gems webhook requires an explicit enabled flag");
            }
            if (!root.get("enabled").getAsBoolean()) return null;
            int port = Math.toIntExact(integer(root, "port"));
            if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid webhook port");
            Path keystore = Path.of(string(root, "keyStore"));
            String token = System.getenv("GANGS_KOFI_TOKEN");
            String password = System.getenv("GANGS_KOFI_KEYSTORE_PASSWORD");
            if (token == null || token.isBlank() || password == null || password.isEmpty()) {
                throw new IllegalArgumentException("Set GANGS_KOFI_TOKEN and GANGS_KOFI_KEYSTORE_PASSWORD privately");
            }
            KoFiOrderProcessor processor = new KoFiOrderProcessor(token, offers(root), wallet);
            KoFiWebhookServer server = new KoFiWebhookServer(new InetSocketAddress(port), tls(keystore, password),
                processor, onCredit);
            server.start();
            return server;
        } catch (GeneralSecurityException | JsonParseException | ArithmeticException | IllegalStateException failure) {
            throw new IOException("Invalid Gang Gems webhook configuration or TLS keystore", failure);
        }
    }

    private static SSLContext tls(Path file, String password) throws IOException, GeneralSecurityException {
        KeyStore keystore = KeyStore.getInstance("PKCS12");
        char[] chars = password.toCharArray();
        try {
            try (var input = Files.newInputStream(file)) {
                keystore.load(input, chars);
            }
            KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            managers.init(keystore, chars);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(managers.getKeyManagers(), null, null);
            return context;
        } finally {
            java.util.Arrays.fill(chars, '\0');
        }
    }

    static Map<KoFiOrderProcessor.Product, KoFiOrderProcessor.Offer> offers(JsonObject root) {
        Map<KoFiOrderProcessor.Product, KoFiOrderProcessor.Offer> offers = new HashMap<>();
        JsonElement products = root.get("offers");
        if (products == null || !products.isJsonArray()) throw new IllegalArgumentException("Missing offers array");
        for (JsonElement element : products.getAsJsonArray()) {
            JsonObject item = element.getAsJsonObject();
            String code = string(item, "code");
            if (!code.matches("[A-Za-z0-9_-]{1,200}")) throw new IllegalArgumentException("Invalid product code");
            String variation = item.has("variation") && !item.get("variation").isJsonNull()
                ? string(item, "variation") : null;
            var product = new KoFiOrderProcessor.Product(code, variation);
            var offer = new KoFiOrderProcessor.Offer(integer(item, "cents"), integer(item, "gems"));
            if (offers.putIfAbsent(product, offer) != null) throw new IllegalArgumentException("Duplicate product");
        }
        if (offers.isEmpty()) throw new IllegalArgumentException("No Gang Gems offers configured");
        return Map.copyOf(offers);
    }

    static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()
                || element.getAsString().isBlank()) {
            throw new IllegalArgumentException("Invalid " + key);
        }
        return element.getAsString();
    }

    private static long integer(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Invalid " + key);
        }
        return element.getAsBigDecimal().longValueExact();
    }
}
