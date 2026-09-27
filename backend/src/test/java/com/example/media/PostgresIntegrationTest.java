package com.example.media;

import com.example.media.camera.*;
import com.example.media.media.VideoMetadata;
import com.example.media.media.MediaAvailability;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.example.media.video.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class PostgresIntegrationTest {
    // This suite validates PostgreSQL mappings and Spring wiring without requiring local media binaries.
    @MockitoBean MediaAvailability mediaAvailability;
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("storage.root", () -> "target/integration-data");
    }
    @Autowired CameraRepository cameras;
    @Autowired VideoRepository videos;
    @Autowired Flyway flyway;

    @Test void migrationAndEntityMappingsWorkWithPostgres() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        Camera camera = cameras.saveAndFlush(new Camera("Camera", CameraSourceType.RTSP, "rtsp://host/live"));
        assertThat(cameras.findById(camera.getId())).isPresent();
        assertThat(camera.getCreatedAt()).isNotNull();
        UUID id = UUID.randomUUID();
        Video video = videos.saveAndFlush(new Video(id, "clip.mp4", id + ".mp4", 100, new VideoMetadata(10, 1920, 1080, 30, "h264")));
        assertThat(videos.findById(video.getId()).orElseThrow().getStatus()).isEqualTo(VideoStatus.READY);
        videos.delete(video);
        cameras.delete(camera);
    }
}
