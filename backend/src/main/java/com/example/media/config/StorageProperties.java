package com.example.media.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import java.nio.file.Path;

@Validated
@ConfigurationProperties("storage")
public record StorageProperties(@NotNull @DefaultValue("./data") Path root) {}
