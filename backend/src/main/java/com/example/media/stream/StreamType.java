package com.example.media.stream;

public enum StreamType {
    CAMERA("cameras"), VIDEO_SIMULATION("videos");
    private final String directory;
    StreamType(String directory) { this.directory = directory; }
    public String directory() { return directory; }
}
