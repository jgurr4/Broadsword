package com.jmgurr.broadsword;

import com.badlogic.gdx.Gdx;
import com.jmgurr.broadsword.model.SaveState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * The one V1 save file, in the platform config dir. Missing or corrupt reads
 * as empty so the title screen can hide Continue.
 */
public final class SaveFiles {
    private SaveFiles() {
    }

    /** Platform config dir: $XDG_CONFIG_HOME/broadsword, else ~/.broadsword. */
    private static Path file() {
        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path base = (xdg != null && !xdg.isBlank())
                ? Path.of(xdg)
                : Path.of(System.getProperty("user.home"), ".broadsword");
        return base.resolve("save1.txt");
    }

    public static Optional<SaveState> read() {
        try {
            Path p = file();
            if (!Files.isRegularFile(p)) {
                return Optional.empty();
            }
            return SaveState.parse(Files.readString(p, StandardCharsets.UTF_8));
        } catch (IOException | SecurityException e) {
            Gdx.app.error("SaveFiles", "could not read save", e);
            return Optional.empty();
        }
    }

    /** Atomic-ish write: temp file then move, so a crash mid-save cannot corrupt the run. */
    public static void write(SaveState save) {
        try {
            Path p = file();
            Files.createDirectories(p.getParent());
            Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
            Files.writeString(tmp, save.format(), StandardCharsets.UTF_8);
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | SecurityException e) {
            Gdx.app.error("SaveFiles", "could not write save", e);
        }
    }
}
