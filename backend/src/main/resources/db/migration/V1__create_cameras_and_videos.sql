CREATE TABLE cameras (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL CHECK (length(trim(name)) > 0),
    source_type VARCHAR(16) NOT NULL CHECK (source_type IN ('RTSP', 'HLS')),
    source_url VARCHAR(4096) NOT NULL CHECK (length(trim(source_url)) > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'UNAVAILABLE', 'DISABLED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE videos (
    id UUID PRIMARY KEY,
    original_filename VARCHAR(255) NOT NULL,
    storage_path VARCHAR(255) NOT NULL UNIQUE,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    duration_seconds DOUBLE PRECISION NOT NULL CHECK (duration_seconds > 0 AND duration_seconds < 'Infinity'::float8),
    width INTEGER NOT NULL CHECK (width > 0),
    height INTEGER NOT NULL CHECK (height > 0),
    fps DOUBLE PRECISION NOT NULL CHECK (fps > 0 AND fps < 'Infinity'::float8),
    codec VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('READY', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
