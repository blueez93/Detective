package fr.apocalypsebleu.moddetective.client.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalFolderOpenerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void opensAnExistingDirectory() {
        AtomicReference<java.io.File> opened = new AtomicReference<>();

        LocalFolderOpener.Result result = LocalFolderOpener.open(temporaryDirectory, opened::set);

        assertEquals(LocalFolderOpener.Result.OPENED, result);
        assertEquals(temporaryDirectory.toAbsolutePath().normalize().toFile(), opened.get());
    }

    @Test
    void reportsMissingFolderWithoutCallingThePlatform() {
        LocalFolderOpener.Result result = LocalFolderOpener.open(
                temporaryDirectory.resolve("missing"), ignored -> {
                    throw new AssertionError("platform opener must not be called");
                });

        assertEquals(LocalFolderOpener.Result.MISSING, result);
        assertEquals(LocalFolderOpener.Result.MISSING,
                LocalFolderOpener.open(null, ignored -> {}));
    }

    @Test
    void containsPlatformAndPermissionFailures() {
        LocalFolderOpener.Result result = LocalFolderOpener.open(
                temporaryDirectory, ignored -> {
                    throw new SecurityException("denied");
                });

        assertEquals(LocalFolderOpener.Result.FAILED, result);
    }
}
