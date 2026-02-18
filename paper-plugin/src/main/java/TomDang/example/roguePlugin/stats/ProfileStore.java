package TomDang.example.roguePlugin.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;

public final class ProfileStore {
    private final Path dir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ProfileStore(Path dir) {
        this.dir = dir;
    }

    public PlayerProfile loadOrCreate(UUID uuid) throws IOException {
        Files.createDirectories(dir);

        Path file = dir.resolve(uuid.toString() + ".json");
        if (!Files.exists(file)) {
            PlayerProfile fresh = new PlayerProfile();
            save(uuid, fresh);
            return fresh;
        }

        String json = Files.readString(file, StandardCharsets.UTF_8);
        PlayerProfile p = gson.fromJson(json, PlayerProfile.class);
        if (p == null) p = new PlayerProfile();
        p.ensureDefaults(); // important for forward compatibility
        return p;
    }

    public void save(UUID uuid, PlayerProfile profile) throws IOException {
        Files.createDirectories(dir);
        profile.ensureDefaults();

        Path file = dir.resolve(uuid.toString() + ".json");
        Path tmp  = dir.resolve(uuid.toString() + ".json.tmp");

        String json = gson.toJson(profile);
        Files.writeString(tmp, json, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
