package com.gangs.gangsgems;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KoFiOrderProcessor {
    public record Product(String code, String variation) {}
    public record Offer(long cents, long gems) {
        public Offer {
            if (cents < 200 || gems <= 0) throw new IllegalArgumentException("Invalid shop offer");
        }
    }

    public enum Result { IGNORED, APPLIED, PENDING, DUPLICATE }

    public static final class InvalidRequest extends Exception {
        public InvalidRequest(String message) { super(message); }
    }

    private static final Pattern CODE = Pattern.compile("(?<![A-Za-z0-9])GANGS-[0-9A-F]{32}(?![A-Za-z0-9])");
    private final byte[] token;
    private final Map<Product, Offer> offers;
    private final WalletStore wallet;

    public KoFiOrderProcessor(String token, Map<Product, Offer> offers, WalletStore wallet) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Missing verification token");
        this.token = token.getBytes(StandardCharsets.UTF_8);
        this.offers = Map.copyOf(offers);
        this.wallet = wallet;
    }

    public Result process(String form, long nowMillis) throws IOException, InvalidRequest {
        return process(form, nowMillis, null);
    }

    public Result process(String form, long nowMillis, Function<String, UUID> recipientResolver)
            throws IOException, InvalidRequest {
        JsonObject event = parseForm(form);
        String suppliedToken = string(event, "verification_token");
        if (!MessageDigest.isEqual(token, suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidRequest("Invalid verification token");
        }
        if (!"Shop Order".equals(string(event, "type"))) return Result.IGNORED;
        if (!"USD".equals(string(event, "currency"))) throw new InvalidRequest("Unsupported currency");
        String transaction = string(event, "kofi_transaction_id");
        if (!transaction.matches("[A-Za-z0-9_-]{1,200}")) throw new InvalidRequest("Invalid transaction ID");

        long price = 0;
        long gems = 0;
        try {
            JsonElement items = event.get("shop_items");
            if (items == null || !items.isJsonArray() || items.getAsJsonArray().isEmpty()) {
                throw new InvalidRequest("Missing shop items");
            }
            for (JsonElement element : items.getAsJsonArray()) {
                if (!element.isJsonObject()) throw new InvalidRequest("Invalid shop item");
                JsonObject item = element.getAsJsonObject();
                String variation = optionalString(item, "variation_name");
                Offer offer = offers.get(new Product(string(item, "direct_link_code"), variation));
                if (offer == null) throw new InvalidRequest("Unrecognised shop item");
                long quantity = integer(item, "quantity");
                if (quantity < 1 || quantity > 1000) throw new InvalidRequest("Invalid shop quantity");
                price = Math.addExact(price, Math.multiplyExact(offer.cents(), quantity));
                gems = Math.addExact(gems, Math.multiplyExact(offer.gems(), quantity));
            }
            BigDecimal paid = new BigDecimal(string(event, "amount")).setScale(2, RoundingMode.UNNECESSARY);
            long paidCents = paid.movePointRight(2).longValueExact();
            if (paidCents < price || paidCents < 200) throw new InvalidRequest("Payment below configured price");
        } catch (ArithmeticException | NumberFormatException failure) {
            throw new InvalidRequest("Invalid payment amount");
        }
        String message = optionalString(event, "message");
        if (recipientResolver != null) {
            String username = message == null ? "" : message.trim();
            UUID recipient = username.matches("[A-Za-z0-9_]{3,16}") ? recipientResolver.apply(username) : null;
            return result(wallet.recordPaymentFor(transaction, recipient, gems));
        }
        String code = null;
        if (message != null) {
            Matcher matcher = CODE.matcher(message);
            if (matcher.find()) {
                code = matcher.group();
                if (matcher.find()) code = null;
            }
        }
        return result(wallet.recordPayment(transaction, code, gems, nowMillis));
    }

    private static Result result(WalletStore.CreditResult credit) {
        return switch (credit) {
            case APPLIED -> Result.APPLIED;
            case PENDING -> Result.PENDING;
            case DUPLICATE -> Result.DUPLICATE;
        };
    }

    private static JsonObject parseForm(String form) throws InvalidRequest {
        if (form == null || form.length() > 65536) throw new InvalidRequest("Invalid request size");
        String data = null;
        try {
            for (String part : form.split("&", -1)) {
                String[] pair = part.split("=", 2);
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                if ("data".equals(key)) {
                    if (data != null || pair.length != 2) throw new InvalidRequest("Invalid data field");
                    data = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                }
            }
            if (data == null) throw new InvalidRequest("Missing data field");
            JsonElement value = JsonParser.parseString(data);
            if (!value.isJsonObject()) throw new InvalidRequest("Invalid JSON object");
            return value.getAsJsonObject();
        } catch (IllegalArgumentException | JsonParseException failure) {
            throw new InvalidRequest("Invalid form or JSON");
        }
    }

    private static String string(JsonObject object, String key) throws InvalidRequest {
        String value = optionalString(object, key);
        if (value == null || value.isBlank()) throw new InvalidRequest("Missing " + key);
        return value;
    }

    private static String optionalString(JsonObject object, String key) throws InvalidRequest {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new InvalidRequest("Invalid " + key);
        }
        return value.getAsString();
    }

    private static long integer(JsonObject object, String key) throws InvalidRequest {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new InvalidRequest("Invalid " + key);
        }
        return value.getAsBigDecimal().longValueExact();
    }
}
