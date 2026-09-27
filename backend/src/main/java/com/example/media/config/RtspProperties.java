package com.example.media.config;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("media.rtsp")
public record RtspProperties(@NotNull @Pattern(regexp = "tcp|udp") @DefaultValue("tcp") String transport) {}
