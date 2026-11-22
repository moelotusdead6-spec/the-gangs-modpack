package com.gangs.gangshop.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class CatalogEdits {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private State state;

    public CatalogEdits(Path path) throws IOException {
        this.path = path;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                this.state = GSON.fromJson(reader, State.class);
            } catch (JsonParseException exception) {
                throw new IOException("Invalid catalog editor data: " + path, exception);
            }
            validate(this.state);
        } else {
            this.state = new State();
        }
    }

    public State snapshot() {
        return GSON.fromJson(GSON.toJson(this.state), State.class);
    }

    public void update(Consumer<State> change) throws IOException {
        State next = snapshot();
        change.accept(next);
        validate(next);
        Files.createDirectories(this.path.getParent());
        Path temporary = Files.createTempFile(this.path.getParent(), "gangshop-editor-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(next, writer);
            }
            try {
                Files.move(temporary, this.path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, this.path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        this.state = next;
    }

    private static void validate(State data) throws IOException {
        if (data == null || data.version != 1 || data.categories == null || data.assignments == null
                || data.hidden == null || data.prices == null) {
            throw new IOException("Invalid Gang Shop catalog editor data");
        }
        for (Map.Entry<String, CategoryEdit> entry : data.categories.entrySet()) {
            CategoryEdit category = entry.getValue();
            if (entry.getKey() == null || category == null || category.name == null || category.icon == null) {
                throw new IOException("Invalid Gang Shop category editor data");
            }
        }
        for (Map.Entry<String, Price> entry : data.prices.entrySet()) {
            Price price = entry.getValue();
            if (entry.getKey() == null || price == null || price.buy < 0 || price.sell < 0) {
                throw new IOException("Invalid Gang Shop editor price");
            }
        }
        if (data.assignments.containsKey(null) || data.assignments.containsValue(null) || data.hidden.contains(null)) {
            throw new IOException("Invalid Gang Shop catalog item data");
        }
    }

    public static final class State {
        public int version = 1;
        public Map<String, CategoryEdit> categories = new LinkedHashMap<>();
        public Map<String, String> assignments = new LinkedHashMap<>();
        public Set<String> hidden = new LinkedHashSet<>();
        public Map<String, Price> prices = new LinkedHashMap<>();
        public boolean emptiedChippedCategory;
    }

    public static final class CategoryEdit {
        public String name;
        public String icon;
        public boolean deleted;

        public CategoryEdit(String name, String icon, boolean deleted) {
            this.name = name;
            this.icon = icon;
            this.deleted = deleted;
        }
    }

    public static final class Price {
        public long buy;
        public long sell;

        public Price(long buy, long sell) {
            this.buy = buy;
            this.sell = sell;
        }
    }
}
