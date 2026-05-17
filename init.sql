CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TYPE victim_status AS ENUM ('ok', 'under_rubble', 'injured');

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    device_id VARCHAR(255) UNIQUE NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    birth_date DATE,
    address TEXT,
    city VARCHAR(100),
    district VARCHAR(100),
    gender VARCHAR(20),
    blood_type VARCHAR(10),
    registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS victim_reports (
    id UUID PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL REFERENCES users(device_id) ON DELETE CASCADE,
    status victim_status NOT NULL,
    location GEOMETRY(Point, 4326) NOT NULL,
    battery_level SMALLINT,
    reported_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_victim_reports_location ON victim_reports USING GIST(location);

CREATE TABLE IF NOT EXISTS drone_detections (
    id UUID PRIMARY KEY,
    drone_id VARCHAR(255) NOT NULL,
    label VARCHAR(100) NOT NULL,
    confidence REAL NOT NULL,
    location GEOMETRY(Point, 4326) NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_drone_detections_location ON drone_detections USING GIST(location);

CREATE TABLE IF NOT EXISTS drones (
    drone_id VARCHAR(255) PRIMARY KEY,
    status VARCHAR(50) NOT NULL DEFAULT 'active',
    battery_level SMALLINT NOT NULL,
    altitude REAL NOT NULL,
    speed REAL NOT NULL DEFAULT 0.0,
    location GEOMETRY(Point, 4326) NOT NULL,
    last_seen TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS earthquakes (
    id UUID PRIMARY KEY,
    magnitude REAL NOT NULL,
    depth_km REAL NOT NULL,
    location_name VARCHAR(255),
    epicenter GEOMETRY(Point, 4326) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_earthquakes_epicenter ON earthquakes USING GIST(epicenter);

CREATE TABLE IF NOT EXISTS earthquake_logs (
    id UUID PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    location GEOMETRY(Point, 4326) NOT NULL,
    intensity REAL DEFAULT 0.0,
    reported_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_earthquake_logs_location ON earthquake_logs USING GIST(location);
CREATE INDEX idx_earthquake_logs_time ON earthquake_logs(reported_at);

