package com.example.media.storage;

import com.example.media.config.StorageProperties;
import com.example.media.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class LocalStorageServiceTest {
    @TempDir Path root;
    @Test void usesInternalNameAndDeletesFile() throws Exception {
        var storage = new LocalStorageService(new StorageProperties(root));
        UUID id = UUID.randomUUID();
        var saved = storage.saveVideo(id, new MockMultipartFile("file", "../../evil.mp4", "video/mp4", new byte[]{1, 2, 3}));
        assertThat(saved.key()).isEqualTo(id + ".mp4");
        assertThat(saved.sizeBytes()).isEqualTo(3);
        assertThat(Files.readAllBytes(storage.resolveVideo(saved.key()))).containsExactly(1, 2, 3);
        storage.deleteVideo(saved.key());
        assertThat(storage.resolveVideo(saved.key())).doesNotExist();
    }
    @Test void rejectsTraversal() {
        var storage = new LocalStorageService(new StorageProperties(root));
        assertThatThrownBy(() -> storage.resolveVideo("../../evil.mp4")).isInstanceOf(ApiException.class);
    }
}
