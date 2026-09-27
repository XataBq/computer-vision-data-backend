package com.example.media.camera;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface CameraRepository extends JpaRepository<Camera, UUID> {}
