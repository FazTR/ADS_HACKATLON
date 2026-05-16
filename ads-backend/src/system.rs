use axum::{
    extract::{Json, State},
    http::StatusCode,
    response::IntoResponse,
};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Deserialize)]
pub struct EarthquakePayload {
    pub magnitude: f32,
    pub depth_km: f32,
    pub location_name: Option<String>,
    pub latitude: f64,
    pub longitude: f64,
    pub occurred_at: Option<chrono::DateTime<chrono::Utc>>,
}

#[derive(Serialize)]
pub struct EarthquakeResponse {
    pub id: Uuid,
    pub message: String,
}

pub async fn report_earthquake(
    State(state): State<crate::AppState>,
    Json(payload): Json<EarthquakePayload>,
) -> Result<impl IntoResponse, (StatusCode, String)> {
    if payload.latitude < -90.0
        || payload.latitude > 90.0
        || payload.longitude < -180.0
        || payload.longitude > 180.0
    {
        return Err((StatusCode::BAD_REQUEST, "Invalid coordinates".to_string()));
    }

    let id = Uuid::new_v4();
    let point_wkt = format!(
        "SRID=4326;POINT({} {})",
        payload.longitude, payload.latitude
    );
    let occurred = payload.occurred_at.unwrap_or_else(chrono::Utc::now);

    let result = sqlx::query(
        r#"
        INSERT INTO earthquakes (id, magnitude, depth_km, location_name, epicenter, occurred_at)
        VALUES ($1, $2, $3, $4, ST_GeomFromEWKT($5), $6)
        "#,
    )
    .bind(id)
    .bind(payload.magnitude)
    .bind(payload.depth_km)
    .bind(&payload.location_name)
    .bind(&point_wkt)
    .bind(occurred)
    .execute(&state.pool)
    .await;

    match result {
        Ok(_) => {
            tracing::info!(
                "Earthquake reported: Mag {} at {}, {}",
                payload.magnitude,
                payload.latitude,
                payload.longitude
            );

            // Broadcast to SSE
            let event = crate::mqtt::LiveEvent {
                event_type: "earthquake_alert".to_string(),
                drone_id: Some("SYSTEM".to_string()),
                data: crate::mqtt::LiveData::Earthquake(serde_json::json!({
                    "magnitude": payload.magnitude,
                    "depth_km": payload.depth_km,
                    "location_name": payload.location_name,
                    "latitude": payload.latitude,
                    "longitude": payload.longitude,
                    "occurred_at": occurred
                })),
            };
            let _ = state.tx.send(event); // Ignore error if no listeners

            let response = EarthquakeResponse {
                id,
                message: "Earthquake successfully recorded and broadcasted.".to_string(),
            };
            Ok((StatusCode::CREATED, Json(response)))
        }
        Err(e) => {
            tracing::error!("Failed to save earthquake: {}", e);
            Err((
                StatusCode::INTERNAL_SERVER_ERROR,
                "Database error".to_string(),
            ))
        }
    }
}

#[derive(Serialize, sqlx::FromRow)]
pub struct EarthquakeRecord {
    pub id: Uuid,
    pub magnitude: f32,
    pub depth_km: f32,
    pub location_name: Option<String>,
    pub latitude: f64,
    pub longitude: f64,
    pub occurred_at: chrono::DateTime<chrono::Utc>,
}

pub async fn get_earthquakes(
    State(state): State<crate::AppState>,
) -> Result<impl IntoResponse, (StatusCode, String)> {
    let results = sqlx::query_as::<_, EarthquakeRecord>(
        r#"
        SELECT 
            id, magnitude, depth_km, location_name, 
            ST_Y(epicenter::geometry) as latitude, 
            ST_X(epicenter::geometry) as longitude, 
            occurred_at
        FROM earthquakes
        ORDER BY occurred_at DESC
        LIMIT 100
        "#,
    )
    .fetch_all(&state.pool)
    .await;

    match results {
        Ok(eqs) => Ok((StatusCode::OK, Json(eqs))),
        Err(e) => {
            tracing::error!("Failed to fetch earthquakes: {}", e);
            Err((
                StatusCode::INTERNAL_SERVER_ERROR,
                "Failed to fetch data".to_string(),
            ))
        }
    }
}
