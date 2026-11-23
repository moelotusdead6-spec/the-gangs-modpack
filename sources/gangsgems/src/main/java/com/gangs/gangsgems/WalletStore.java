package com.gangs.gangsgems;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class WalletStore {
    public enum CreditResult { APPLIED, PENDING, DUPLICATE }

    private record Payment(UUID player, long gems) {}
    private record Account(long balance, long unannounced) {}
    private record Link(UUID player, long expiresAt) {}
    public record Craft(UUID id, UUID player, String itemNbt) {}
    public record PaymentView(UUID player, long gems) {}
    public record RecordedPayment(String transaction, long gems) {}
    public record Audit(UUID id, String actor, UUID player, String action, long gems, String reference,
            String reason, long time) {}
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Path path;
    private Map<UUID, Account> accounts;
    private Map<String, Payment> payments;
    private Map<String, Link> links;
    private Map<UUID, Craft> crafts;
    private Map<UUID, Audit> audits;

    public WalletStore(Path path) throws IOException {
        this.path = path;
        this.accounts = new HashMap<>();
        this.payments = new HashMap<>();
        this.links = new HashMap<>();
        this.crafts = new HashMap<>();
        this.audits = new HashMap<>();
        if (Files.exists(path)) read();
    }

    public synchronized long balance(UUID player) {
        return accounts.getOrDefault(player, new Account(0, 0)).balance();
    }

    public synchronized long unannounced(UUID player) {
        return accounts.getOrDefault(player, new Account(0, 0)).unannounced();
    }

    public synchronized Map<String, Long> pendingPayments() {
        Map<String, Long> pending = new HashMap<>();
        payments.forEach((id, payment) -> {
            if (payment.player() == null) pending.put(id, payment.gems());
        });
        return Map.copyOf(pending);
    }

    public synchronized PaymentView payment(String transaction) {
        Payment payment = payments.get(transaction);
        return payment == null ? null : new PaymentView(payment.player(), payment.gems());
    }

    public synchronized java.util.List<RecordedPayment> paymentsFor(UUID player) {
        return payments.entrySet().stream().filter(entry -> player.equals(entry.getValue().player()))
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new RecordedPayment(entry.getKey(), entry.getValue().gems())).toList();
    }

    public synchronized java.util.List<Audit> audit(UUID player) {
        return audits.values().stream().filter(entry -> player == null || entry.player().equals(player))
            .sorted(java.util.Comparator.comparingLong(Audit::time).reversed().thenComparing(Audit::id))
            .toList();
    }

    public synchronized CreditResult creditPayment(String transaction, UUID player, long gems) throws IOException {
        return creditPayment(transaction, player, gems, null);
    }

    public synchronized CreditResult staffCreditPayment(String transaction, UUID player, long gems, String actor)
            throws IOException {
        return creditPayment(transaction, player, gems, newAudit(actor, player, "credit", gems, transaction,
            "Verified purchase", System.currentTimeMillis()));
    }

    private CreditResult creditPayment(String transaction, UUID player, long gems, Audit audit) throws IOException {
        if (transaction == null || transaction.isBlank() || transaction.length() > 200 || player == null || gems <= 0) {
            throw new IllegalArgumentException("Invalid payment");
        }
        Payment payment = new Payment(player, gems);
        Payment existing = payments.get(transaction);
        if (existing != null) {
            if (!existing.equals(payment)) throw new IllegalStateException("Conflicting payment transaction");
            return CreditResult.DUPLICATE;
        }
        Account account = accounts.getOrDefault(player, new Account(0, 0));
        Account updated = new Account(Math.addExact(account.balance(), gems), Math.addExact(account.unannounced(), gems));
        Map<UUID, Account> nextAccounts = new HashMap<>(accounts);
        Map<String, Payment> nextPayments = new HashMap<>(payments);
        nextAccounts.put(player, updated);
        nextPayments.put(transaction, payment);
        Map<UUID, Audit> nextAudits = withAudit(audit);
        save(nextAccounts, nextPayments, links, crafts, nextAudits);
        accounts = nextAccounts;
        payments = nextPayments;
        audits = nextAudits;
        return CreditResult.APPLIED;
    }

    public synchronized String createLink(UUID player, long nowMillis) throws IOException {
        Objects.requireNonNull(player);
        byte[] random = new byte[16];
        RANDOM.nextBytes(random);
        String code = "GANGS-" + HexFormat.of().withUpperCase().formatHex(random);
        Map<String, Link> next = new HashMap<>(links);
        next.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= nowMillis);
        next.put(hashCode(code), new Link(player, Math.addExact(nowMillis, 24 * 60 * 60 * 1000L)));
        save(accounts, payments, next, crafts);
        links = next;
        return code;
    }

    public synchronized CreditResult recordPayment(String transaction, String code, long gems, long nowMillis)
            throws IOException {
        if (transaction == null || transaction.isBlank() || transaction.length() > 200 || gems <= 0) {
            throw new IllegalArgumentException("Invalid payment");
        }
        Payment existing = payments.get(transaction);
        if (existing != null) {
            if (existing.gems() != gems) throw new IllegalStateException("Conflicting payment transaction");
            return CreditResult.DUPLICATE;
        }
        String codeHash = code == null ? null : hashCode(code);
        Link link = codeHash == null ? null : links.get(codeHash);
        if (link != null && link.expiresAt() <= nowMillis) link = null;
        Map<String, Payment> nextPayments = new HashMap<>(payments);
        Map<UUID, Account> nextAccounts = new HashMap<>(accounts);
        Map<String, Link> nextLinks = new HashMap<>(links);
        if (link == null) {
            nextPayments.put(transaction, new Payment(null, gems));
        } else {
            Account account = nextAccounts.getOrDefault(link.player(), new Account(0, 0));
            nextAccounts.put(link.player(), new Account(Math.addExact(account.balance(), gems),
                Math.addExact(account.unannounced(), gems)));
            nextPayments.put(transaction, new Payment(link.player(), gems));
            nextLinks.remove(codeHash);
        }
        save(nextAccounts, nextPayments, nextLinks, crafts);
        accounts = nextAccounts;
        payments = nextPayments;
        links = nextLinks;
        return link == null ? CreditResult.PENDING : CreditResult.APPLIED;
    }

    public synchronized CreditResult recordPaymentFor(String transaction, UUID player, long gems) throws IOException {
        if (transaction == null || transaction.isBlank() || transaction.length() > 200 || gems <= 0) {
            throw new IllegalArgumentException("Invalid payment");
        }
        Payment existing = payments.get(transaction);
        if (existing != null) {
            if (existing.gems() != gems || (player != null && existing.player() != null
                    && !existing.player().equals(player))) {
                throw new IllegalStateException("Conflicting payment transaction");
            }
            return CreditResult.DUPLICATE;
        }
        if (player == null) return recordPayment(transaction, null, gems, 0);
        return creditPayment(transaction, player, gems);
    }

    public synchronized void assignPending(String transaction, UUID player) throws IOException {
        assignPending(transaction, player, null);
    }

    public synchronized void staffAssignPending(String transaction, UUID player, String actor) throws IOException {
        Payment payment = payments.get(transaction);
        if (payment == null || payment.player() != null) throw new IllegalArgumentException("Payment is not pending");
        assignPending(transaction, player, newAudit(actor, player, "assign", payment.gems(), transaction,
            "Pending purchase", System.currentTimeMillis()));
    }

    private void assignPending(String transaction, UUID player, Audit audit) throws IOException {
        Objects.requireNonNull(player);
        Payment payment = payments.get(transaction);
        if (payment == null || payment.player() != null) throw new IllegalArgumentException("Payment is not pending");
        Account account = accounts.getOrDefault(player, new Account(0, 0));
        Map<UUID, Account> nextAccounts = new HashMap<>(accounts);
        Map<String, Payment> nextPayments = new HashMap<>(payments);
        nextAccounts.put(player, new Account(Math.addExact(account.balance(), payment.gems()),
            Math.addExact(account.unannounced(), payment.gems())));
        nextPayments.put(transaction, new Payment(player, payment.gems()));
        Map<UUID, Audit> nextAudits = withAudit(audit);
        save(nextAccounts, nextPayments, links, crafts, nextAudits);
        accounts = nextAccounts;
        payments = nextPayments;
        audits = nextAudits;
    }

    public synchronized Audit adjust(UUID player, long gems, boolean remove, String actor, String reason)
            throws IOException {
        Audit audit = newAudit(actor, player, remove ? "remove" : "grant", gems, "", reason, System.currentTimeMillis());
        Account account = accounts.getOrDefault(player, new Account(0, 0));
        if (remove && account.balance() < gems) throw new IllegalStateException("Insufficient Gems");
        long balance = remove ? account.balance() - gems : Math.addExact(account.balance(), gems);
        Map<UUID, Account> nextAccounts = new HashMap<>(accounts);
        nextAccounts.put(player, new Account(balance, account.unannounced()));
        Map<UUID, Audit> nextAudits = withAudit(audit);
        save(nextAccounts, payments, links, crafts, nextAudits);
        accounts = nextAccounts;
        audits = nextAudits;
        return audit;
    }

    private static Audit newAudit(String actor, UUID player, String action, long gems, String reference,
            String reason, long time) {
        if (actor == null || actor.isBlank() || actor.length() > 80 || player == null || gems <= 0
                || reference == null || reference.length() > 200 || reason == null || reason.isBlank()
                || reason.length() > 160 || reason.chars().anyMatch(Character::isISOControl) || time <= 0) {
            throw new IllegalArgumentException("Invalid staff audit entry");
        }
        return new Audit(UUID.randomUUID(), actor, player, action, gems, reference, reason, time);
    }

    private Map<UUID, Audit> withAudit(Audit audit) {
        Map<UUID, Audit> next = new HashMap<>(audits);
        if (audit != null && next.putIfAbsent(audit.id(), audit) != null) {
            throw new IllegalStateException("Duplicate staff audit entry");
        }
        return next;
    }

    public synchronized boolean spend(UUID player, long gems) throws IOException {
        Objects.requireNonNull(player);
        if (gems <= 0) throw new IllegalArgumentException("Gem amount must be positive");
        Account account = accounts.getOrDefault(player, new Account(0, 0));
        if (account.balance() < gems) return false;
        Map<UUID, Account> next = new HashMap<>(accounts);
        next.put(player, new Account(account.balance() - gems, account.unannounced()));
        save(next, payments, links, crafts);
        accounts = next;
        return true;
    }

    public synchronized void acknowledgeNotification(UUID player, long gems) throws IOException {
        if (gems <= 0) throw new IllegalArgumentException("Invalid notification amount");
        Account account = accounts.get(player);
        if (account == null || account.unannounced() < gems) throw new IllegalStateException("Invalid notification receipt");
        Map<UUID, Account> next = new HashMap<>(accounts);
        next.put(player, new Account(account.balance(), account.unannounced() - gems));
        save(next, payments, links, crafts);
        accounts = next;
    }

    public synchronized Craft reserveCraft(UUID player, long cost, String itemNbt) throws IOException {
        Objects.requireNonNull(player);
        if (cost <= 0 || itemNbt == null || itemNbt.isBlank() || itemNbt.length() > 1_000_000) {
            throw new IllegalArgumentException("Invalid furniture reservation");
        }
        Account account = accounts.getOrDefault(player, new Account(0, 0));
        if (account.balance() < cost) throw new IllegalStateException("Insufficient Gems");
        Craft craft = new Craft(UUID.randomUUID(), player, itemNbt);
        Map<UUID, Account> nextAccounts = new HashMap<>(accounts);
        Map<UUID, Craft> nextCrafts = new HashMap<>(crafts);
        nextAccounts.put(player, new Account(account.balance() - cost, account.unannounced()));
        nextCrafts.put(craft.id(), craft);
        save(nextAccounts, payments, links, nextCrafts);
        accounts = nextAccounts;
        crafts = nextCrafts;
        return craft;
    }

    public synchronized java.util.List<Craft> pendingCrafts(UUID player) {
        return crafts.values().stream().filter(craft -> craft.player().equals(player)).toList();
    }

    public synchronized void completeCraft(UUID id, UUID player) throws IOException {
        Craft craft = crafts.get(id);
        if (craft == null || !craft.player().equals(player)) throw new IllegalArgumentException("Unknown furniture reservation");
        Map<UUID, Craft> next = new HashMap<>(crafts);
        next.remove(id);
        save(accounts, payments, links, next);
        crafts = next;
    }

    private void read() throws IOException {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unsupported wallet version");
            for (var entry : root.getAsJsonObject("accounts").entrySet()) {
                UUID player = UUID.fromString(entry.getKey());
                JsonObject account = entry.getValue().getAsJsonObject();
                long balance = exactLong(account.get("balance"));
                long unannounced = exactLong(account.get("unannounced"));
                if (balance < 0 || unannounced < 0) throw new IllegalArgumentException("Negative wallet value");
                accounts.put(player, new Account(balance, unannounced));
            }
            for (var entry : root.getAsJsonObject("payments").entrySet()) {
                if (entry.getKey().isBlank() || entry.getKey().length() > 200) throw new IllegalArgumentException("Invalid transaction");
                JsonObject payment = entry.getValue().getAsJsonObject();
                UUID player = payment.get("player").isJsonNull() ? null : UUID.fromString(payment.get("player").getAsString());
                long gems = exactLong(payment.get("gems"));
                if (gems <= 0 || (player != null && !accounts.containsKey(player))) throw new IllegalArgumentException("Invalid payment");
                payments.put(entry.getKey(), new Payment(player, gems));
            }
            for (var entry : root.getAsJsonObject("links").entrySet()) {
                if (!entry.getKey().matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid link");
                JsonObject link = entry.getValue().getAsJsonObject();
                UUID player = UUID.fromString(link.get("player").getAsString());
                long expiresAt = exactLong(link.get("expiresAt"));
                if (expiresAt <= 0) throw new IllegalArgumentException("Invalid link expiry");
                links.put(entry.getKey(), new Link(player, expiresAt));
            }
            for (var entry : root.getAsJsonObject("crafts").entrySet()) {
                UUID id = UUID.fromString(entry.getKey());
                JsonObject craft = entry.getValue().getAsJsonObject();
                UUID player = UUID.fromString(craft.get("player").getAsString());
                String itemNbt = craft.get("itemNbt").getAsString();
                if (!accounts.containsKey(player) || itemNbt.isBlank() || itemNbt.length() > 1_000_000) {
                    throw new IllegalArgumentException("Invalid furniture reservation");
                }
                crafts.put(id, new Craft(id, player, itemNbt));
            }
            if (root.has("audits")) {
                for (var entry : root.getAsJsonObject("audits").entrySet()) {
                    UUID id = UUID.fromString(entry.getKey());
                    JsonObject value = entry.getValue().getAsJsonObject();
                    String action = value.get("action").getAsString();
                    if (!java.util.Set.of("grant", "remove", "credit", "assign").contains(action)) {
                        throw new IllegalArgumentException("Invalid audit action");
                    }
                    Audit checked = newAudit(value.get("actor").getAsString(), UUID.fromString(value.get("player").getAsString()),
                        action, exactLong(value.get("gems")), value.get("reference").getAsString(),
                        value.get("reason").getAsString(), exactLong(value.get("time")));
                    if (!accounts.containsKey(checked.player())) throw new IllegalArgumentException("Audit account missing");
                    audits.put(id, new Audit(id, checked.actor(), checked.player(), checked.action(), checked.gems(),
                        checked.reference(), checked.reason(), checked.time()));
                }
            }
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException | ArithmeticException | JsonParseException failure) {
            throw new IOException("Invalid Gang Gems wallet data: " + path, failure);
        }
    }

    private static String hashCode(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static long exactLong(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected integer");
        }
        return value.getAsBigDecimal().longValueExact();
    }

    private void save(Map<UUID, Account> nextAccounts, Map<String, Payment> nextPayments, Map<String, Link> nextLinks,
            Map<UUID, Craft> nextCrafts) throws IOException {
        save(nextAccounts, nextPayments, nextLinks, nextCrafts, audits);
    }

    private void save(Map<UUID, Account> nextAccounts, Map<String, Payment> nextPayments, Map<String, Link> nextLinks,
            Map<UUID, Craft> nextCrafts, Map<UUID, Audit> nextAudits) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject accountData = new JsonObject();
        nextAccounts.forEach((id, account) -> {
            JsonObject value = new JsonObject();
            value.addProperty("balance", account.balance());
            value.addProperty("unannounced", account.unannounced());
            accountData.add(id.toString(), value);
        });
        root.add("accounts", accountData);
        JsonObject paymentData = new JsonObject();
        nextPayments.forEach((id, payment) -> {
            JsonObject value = new JsonObject();
            value.addProperty("player", payment.player() == null ? null : payment.player().toString());
            value.addProperty("gems", payment.gems());
            paymentData.add(id, value);
        });
        root.add("payments", paymentData);
        JsonObject linkData = new JsonObject();
        nextLinks.forEach((hash, link) -> {
            JsonObject value = new JsonObject();
            value.addProperty("player", link.player().toString());
            value.addProperty("expiresAt", link.expiresAt());
            linkData.add(hash, value);
        });
        root.add("links", linkData);
        JsonObject craftData = new JsonObject();
        nextCrafts.forEach((id, craft) -> {
            JsonObject value = new JsonObject();
            value.addProperty("player", craft.player().toString());
            value.addProperty("itemNbt", craft.itemNbt());
            craftData.add(id.toString(), value);
        });
        root.add("crafts", craftData);
        JsonObject auditData = new JsonObject();
        nextAudits.forEach((id, audit) -> {
            JsonObject value = new JsonObject();
            value.addProperty("actor", audit.actor());
            value.addProperty("player", audit.player().toString());
            value.addProperty("action", audit.action());
            value.addProperty("gems", audit.gems());
            value.addProperty("reference", audit.reference());
            value.addProperty("reason", audit.reason());
            value.addProperty("time", audit.time());
            auditData.add(id.toString(), value);
        });
        root.add("audits", auditData);
        Path parent = path.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "gangsgems-", ".tmp");
        try {
            Files.writeString(temp, root.toString(), StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
