use axum::{
    Json,
    extract::State,
    http::{StatusCode, header},
    response::{IntoResponse, Response},
};
use chrono::{DateTime, NaiveDate, Utc};
use serde::Serialize;
use sqlx::FromRow;
use std::env;
use tokio::process::Command;
use uuid::Uuid;

#[derive(Debug, Serialize, FromRow)]
pub struct ReportResponse {
    pub id: Uuid,
    pub device_id: String,
    pub status: crate::mobile::VictimStatus,
    pub latitude: f64,
    pub longitude: f64,
    pub battery_level: Option<i16>,
    pub reported_at: DateTime<Utc>,
    pub full_name: Option<String>,
    pub birth_date: Option<NaiveDate>,
    pub address: Option<String>,
    pub city: Option<String>,
    pub district: Option<String>,
    pub gender: Option<String>,
    pub blood_type: Option<String>,
}

#[derive(Debug, Serialize, FromRow)]
pub struct DetectionResponse {
    pub id: Uuid,
    pub drone_id: String,
    pub label: String,
    pub confidence: f32,
    pub latitude: f64,
    pub longitude: f64,
    pub detected_at: DateTime<Utc>,
}

pub async fn get_all_reports(
    State(state): State<crate::AppState>,
) -> Result<impl IntoResponse, (StatusCode, String)> {
    let results = sqlx::query_as::<_, ReportResponse>(
        r#"
        SELECT 
            vr.id, 
            vr.device_id, 
            vr.status, 
            ST_Y(vr.location::geometry) as latitude, 
            ST_X(vr.location::geometry) as longitude, 
            vr.battery_level, 
            vr.reported_at,
            u.full_name,
            u.birth_date,
            u.address,
            u.city,
            u.district,
            u.gender,
            u.blood_type
        FROM victim_reports vr
        LEFT JOIN users u ON vr.device_id = u.device_id
        ORDER BY vr.reported_at DESC
        LIMIT 1000
        "#,
    )
    .fetch_all(&state.pool)
    .await;

    match results {
        Ok(reports) => Ok((StatusCode::OK, Json(reports))),
        Err(e) => {
            tracing::error!("Failed to fetch reports: {}", e);
            Err((
                StatusCode::INTERNAL_SERVER_ERROR,
                "Failed to fetch reports".into(),
            ))
        }
    }
}

pub async fn get_all_detections(
    State(state): State<crate::AppState>,
) -> Result<impl IntoResponse, (StatusCode, String)> {
    let results = sqlx::query_as::<_, DetectionResponse>(
        r#"
        SELECT 
            id, 
            drone_id, 
            label, 
            confidence, 
            ST_Y(location::geometry) as latitude, 
            ST_X(location::geometry) as longitude, 
            detected_at
        FROM drone_detections
        ORDER BY detected_at DESC
        LIMIT 1000
        "#,
    )
    .fetch_all(&state.pool)
    .await;

    match results {
        Ok(detections) => Ok((StatusCode::OK, Json(detections))),
        Err(e) => {
            tracing::error!("Failed to fetch detections: {}", e);
            Err((
                StatusCode::INTERNAL_SERVER_ERROR,
                "Failed to fetch detections".into(),
            ))
        }
    }
}

#[derive(Debug, Serialize, FromRow)]
pub struct DroneStatusResponse {
    pub drone_id: String,
    pub status: String,
    pub battery_level: i16,
    pub altitude: f32,
    pub speed: f32,
    pub latitude: f64,
    pub longitude: f64,
    pub last_seen: DateTime<Utc>,
}

pub async fn get_all_drones(
    State(state): State<crate::AppState>,
) -> Result<impl IntoResponse, (StatusCode, String)> {
    let results = sqlx::query_as::<_, DroneStatusResponse>(
        r#"
        SELECT 
            drone_id, 
            status, 
            battery_level, 
            altitude, 
            speed, 
            ST_Y(location::geometry) as latitude, 
            ST_X(location::geometry) as longitude, 
            last_seen
        FROM drones
        ORDER BY last_seen DESC
        "#,
    )
    .fetch_all(&state.pool)
    .await;

    match results {
        Ok(drones) => Ok((StatusCode::OK, Json(drones))),
        Err(e) => {
            tracing::error!("Failed to fetch drones: {}", e);
            Err((
                StatusCode::INTERNAL_SERVER_ERROR,
                "Failed to fetch drones".into(),
            ))
        }
    }
}

#[derive(Debug, Serialize, FromRow)]
pub struct UserResponse {
    pub id: Uuid,
    pub device_id: String,
    pub full_name: String,
    pub birth_date: Option<NaiveDate>,
    pub address: Option<String>,
    pub city: Option<String>,
    pub district: Option<String>,
    pub gender: Option<String>,
    pub blood_type: Option<String>,
    pub registered_at: DateTime<Utc>,
}

pub async fn get_all_users(
    State(state): State<crate::AppState>,
) -> Result<impl IntoResponse, (StatusCode, String)> {
    let results = sqlx::query_as::<_, UserResponse>(
        r#"
        SELECT 
            id,
            device_id,
            full_name,
            birth_date,
            address,
            city,
            district,
            gender,
            blood_type,
            registered_at
        FROM users
        ORDER BY registered_at DESC
        "#,
    )
    .fetch_all(&state.pool)
    .await;

    match results {
        Ok(users) => Ok((StatusCode::OK, Json(users))),
        Err(e) => {
            tracing::error!("Failed to fetch users: {}", e);
            Err((
                StatusCode::INTERNAL_SERVER_ERROR,
                "Failed to fetch users".into(),
            ))
        }
    }
}

#[derive(serde::Deserialize)]
pub struct CameraCommand {
    pub action: String, // "start" or "stop"
}

pub async fn toggle_camera(
    State(state): State<crate::AppState>,
    axum::extract::Path(drone_id): axum::extract::Path<String>,
    Json(payload): Json<CameraCommand>,
) -> Result<impl IntoResponse, (StatusCode, String)> {
    if payload.action != "start" && payload.action != "stop" {
        return Err((
            StatusCode::BAD_REQUEST,
            "Invalid action. Use 'start' or 'stop'".into(),
        ));
    }

    let payload_str = serde_json::json!({
        "action": payload.action
    })
    .to_string();

    let res = state
        .mqtt_client
        .publish(
            format!("drone/{}/command", drone_id),
            rumqttc::QoS::AtLeastOnce,
            false,
            payload_str,
        )
        .await;

    match res {
        Ok(_) => Ok((
            StatusCode::OK,
            Json(serde_json::json!({"message": "Command sent successfully"})),
        )),
        Err(e) => {
            tracing::error!("Failed to send MQTT command: {}", e);
            Err((
                StatusCode::INTERNAL_SERVER_ERROR,
                "Failed to send command to drone".into(),
            ))
        }
    }
}

pub async fn video_feed(
    axum::extract::Path(_drone_id): axum::extract::Path<String>,
) -> impl IntoResponse {
    let file_path = "assets/sample.mp4";
    match tokio::fs::read(file_path).await {
        Ok(file) => axum::response::Response::builder()
            .header("Content-Type", "video/mp4")
            .body(axum::body::Body::from(file))
            .unwrap(),
        Err(_) => axum::response::Response::builder()
            .status(StatusCode::NOT_FOUND)
            .body(axum::body::Body::from("Video not found"))
            .unwrap(),
    }
}

#[derive(serde::Deserialize)]
pub struct NavigateCommand {
    pub target_latitude: f64,
    pub target_longitude: f64,
}

pub async fn navigate_drone(
    State(state): State<crate::AppState>,
    axum::extract::Path(drone_id): axum::extract::Path<String>,
    Json(payload): Json<NavigateCommand>,
) -> Result<impl IntoResponse, (StatusCode, String)> {
    if payload.target_latitude < -90.0
        || payload.target_latitude > 90.0
        || payload.target_longitude < -180.0
        || payload.target_longitude > 180.0
    {
        return Err((StatusCode::BAD_REQUEST, "Invalid coordinates".into()));
    }

    let payload_str = serde_json::json!({
        "action": "navigate",
        "target": {
            "latitude": payload.target_latitude,
            "longitude": payload.target_longitude
        }
    })
    .to_string();

    let res = state
        .mqtt_client
        .publish(
            format!("drone/{}/command", drone_id),
            rumqttc::QoS::AtLeastOnce,
            false,
            payload_str,
        )
        .await;

    match res {
        Ok(_) => Ok((
            StatusCode::OK,
            Json(serde_json::json!({"message": "Navigation command sent successfully"})),
        )),
        Err(e) => {
            tracing::error!("Failed to send MQTT navigation command: {}", e);
            Err((
                StatusCode::INTERNAL_SERVER_ERROR,
                "Failed to send command to drone".into(),
            ))
        }
    }
}

#[derive(serde::Serialize, sqlx::FromRow)]
pub struct EarthquakeLogRecord {
    pub id: uuid::Uuid,
    pub device_id: String,
    pub latitude: f64,
    pub longitude: f64,
    pub reported_at: chrono::DateTime<chrono::Utc>,
}

pub async fn get_all_earthquake_logs(
    State(state): State<crate::AppState>,
) -> Result<impl IntoResponse, (StatusCode, String)> {
    let results = sqlx::query_as::<_, EarthquakeLogRecord>(
        r#"
        SELECT 
            id, device_id, 
            ST_Y(location::geometry) as latitude, 
            ST_X(location::geometry) as longitude, 
            reported_at
        FROM earthquake_logs
        ORDER BY reported_at DESC
        LIMIT 500
        "#,
    )
    .fetch_all(&state.pool)
    .await;

    match results {
        Ok(logs) => Ok((StatusCode::OK, Json(logs))),
        Err(e) => {
            tracing::error!("Failed to fetch earthquake logs: {}", e);
            Err((
                StatusCode::INTERNAL_SERVER_ERROR,
                "Failed to fetch logs".into(),
            ))
        }
    }
}

pub async fn export_database_sql() -> Result<Response, (StatusCode, String)> {
    let database_url = env::var("DATABASE_URL")
        .unwrap_or_else(|_| "postgres://user:password@localhost:4031/ads_db".to_string());
    let file_name = format!("ads-backup-{}.sql", Utc::now().format("%Y%m%d-%H%M%S"));

    let output = Command::new("pg_dump")
        .arg("--dbname")
        .arg(database_url)
        .arg("--no-owner")
        .arg("--no-privileges")
        .output()
        .await
        .map_err(|e| {
            tracing::error!("Failed to run pg_dump: {}", e);
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                "Backup export failed".to_string(),
            )
        })?;

    if !output.status.success() {
        tracing::error!(
            "pg_dump failed with status {:?}: {}",
            output.status.code(),
            String::from_utf8_lossy(&output.stderr)
        );
        return Err((
            StatusCode::INTERNAL_SERVER_ERROR,
            "Backup export failed".to_string(),
        ));
    }

    let headers = [
        (header::CONTENT_TYPE, "application/sql; charset=utf-8"),
        (
            header::CONTENT_DISPOSITION,
            &format!("attachment; filename=\"{}\"", file_name),
        ),
    ];

    Ok((headers, output.stdout).into_response())
}
