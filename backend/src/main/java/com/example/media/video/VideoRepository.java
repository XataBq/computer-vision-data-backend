package com.example.media.video;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface VideoRepository extends JpaRepository<Video, UUID> {}
