use axum::{
    Json,
    extract::{Query, State},
    http::StatusCode,
    response::IntoResponse,
};
use chrono::NaiveDate;
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Deserialize, Serialize, sqlx::Type)]
#[sqlx(type_name = "victim_status", rename_all = "snake_case")]
#[serde(rename_all = "snake_case")]
pub enum VictimStatus {
    Ok,
    UnderRubble,
    Injured,
}

#[derive(Debug, Deserialize)]
pub struct ReportPayload {
    pub device_id: String,
    pub status: VictimStatus,
    pub latitude: f64,
    pub longitude: f64,
    pub battery_level: Option<i16>,
}

#[derive(Serialize)]
pub struct ReportResponse {
    pub id: Uuid,
    pub message: String,
}

pub async fn submit_report(
    State(state): State<crate::AppState>,
    Json(payload): Json<ReportPayload>,
) -> Result<impl IntoResponse, (StatusCode, String)> {
    if payload.latitude < -90.0 || payload.latitude > 90.0 {
        return Err((
            StatusCode::BAD_REQUEST,
            "Invalid latitude. Must be between -90 and 90.".to_string(),
        ));
    }
    if payload.longitude < -180.0 || payload.longitude > 180.0 {
        return Err((
            StatusCode::BAD_REQUEST,
            "Invalid longitude. Must be between -180 and 180.".to_string(),
        ));
    }

    let id = Uuid::new_v4();

    let point_wkt = format!(
        "SRID=4326;POINT({} {})",
        payload.longitude, payload.latitude
    );

    let result = sqlx::query(
        r#"
        INSERT INTO victim_reports (id, device_id, status, location, battery_level)
        VALUES ($1, $2, $3, ST_GeomFromEWKT($4), $5)
        "#,
    )
    .bind(id)
    .bind(&payload.device_id)
    .bind(&payload.status)
    .bind(&point_wkt)
    .bind(payload.battery_level)
    .execute(&state.pool)
    .await;

    match result {
        Ok(_) => {
            tracing::info!("Victim report saved for device {}", payload.device_id);
            let response = ReportResponse {
                id,
                message: "Report successfully submitted.".to_string(),
            };
            Ok((StatusCode::CREATED, Json(response)))
        }
        Err(e) => {
            tracing::error!("Database insertion error: {}", e);
            Err((
                StatusCode::INTERNAL_SERVER_ERROR,
                "Failed to process report.".to_string(),
            ))
        }
    }
}

#[derive(Debug, Deserialize)]
pub struct NearbyQuery {
    pub lat: f64,
    pub lon: f64,
    pub radius_m: f64,
}

#[derive(Debug, Serialize, sqlx::FromRow)]
pub struct NearbyVictim {
    pub device_id: String,
    pub status: VictimStatus,
    pub distance_m: f64,
}

pub async fn get_nearby_victims(
    State(state): State<crate::AppState>,
    Query(query): Query<NearbyQuery>,
) -> Result<impl IntoResponse, (StatusCode, String)> {
    if query.lat < -90.0 || query.lat > 90.0 || query.lon < -180.0 || query.lon > 180.0 {
        return Err((StatusCode::BAD_REQUEST, "Invalid coordinates.".to_string()));
    }

    let results = sqlx::query_as::<_, NearbyVictim>(
        r#"
        SELECT 
            device_id, 
            status, 
            ST_DistanceSphere(location, ST_MakePoint($1, $2)) as distance_m
        FROM victim_reports
        WHERE ST_DWithin(location::geography, ST_MakePoint($1, $2)::geography, $3)
        ORDER BY distance_m ASC
        LIMIT 100
        "#,
    )
    .bind(query.lon)
    .bind(query.lat)
    .bind(query.radius_m)
    .fetch_all(&state.pool)
    .await;

    match results {
        Ok(victims) => Ok((StatusCode::OK, Json(victims))),
        Err(e) => {
            tracing::error!("Failed to fetch nearby victims: {}", e);
            Err((
                StatusCode::INTERNAL_SERVER_ERROR,
                "Could not perform spatial query.".to_string(),
            ))
        }
    }
}

#[derive(Debug, Deserialize)]
pub struct RegisterPayload {
    pub device_id: String,
    pub full_name: String,
    pub birth_date: NaiveDate,
    pub address: Option<String>,
    pub city: Option<String>,
    pub district: Option<String>,
    pub gender: Option<String>,
    pub blood_type: Option<String>,
}

#[derive(Serialize)]
pub struct RegisterResponse {
    pub id: Uuid,
    pub message: String,
}

pub async fn register_user(
    State(state): State<crate::AppState>,
    Json(payload): Json<RegisterPayload>,
) -> Result<impl IntoResponse, (StatusCode, String)> {
    let id = Uuid::new_v4();

    let result = sqlx::query(
        r#"
        INSERT INTO users (id, device_id, full_name, birth_date, address, city, district, gender, blood_type)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
        ON CONFLICT (device_id) DO UPDATE 
        SET full_name = EXCLUDED.full_name,
            birth_date = EXCLUDED.birth_date,
            address = EXCLUDED.address,
            city = EXCLUDED.city,
            district = EXCLUDED.district,
            gender = EXCLUDED.gender,
            blood_type = EXCLUDED.blood_type
        "#
    )
    .bind(id)
    .bind(&payload.device_id)
    .bind(&payload.full_name)
    .bind(payload.birth_date)
    .bind(&payload.address)
    .bind(&payload.city)
    .bind(&payload.district)
    .bind(&payload.gender)
    .bind(&payload.blood_type)
    .execute(&state.pool)
    .await;

    match result {
        Ok(_) => {
            tracing::info!("User registered with device {}", payload.device_id);
            let response = RegisterResponse {
                id,
                message: "User successfully registered.".to_string(),
            };
            Ok((StatusCode::CREATED, Json(response)))
        }
        Err(e) => {
            tracing::error!("Database user insertion error: {}", e);
            Err((
                StatusCode::INTERNAL_SERVER_ERROR,
                "Failed to register user.".to_string(),
            ))
        }
    }
}

#[derive(Serialize)]
pub struct SyncResponse {
    pub processed: usize,
    pub message: String,
}

pub async fn sync_registers(
    State(state): State<crate::AppState>,
    Json(payloads): Json<Vec<RegisterPayload>>,
) -> Result<impl IntoResponse, (StatusCode, String)> {
    let mut processed = 0;

    for payload in payloads {
        let id = Uuid::new_v4();

        let result = sqlx::query(
            r#"
            INSERT INTO users (id, device_id, full_name, birth_date, address, city, district, gender, blood_type)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
            ON CONFLICT (device_id) DO UPDATE 
            SET full_name = EXCLUDED.full_name,
                birth_date = EXCLUDED.birth_date,
                address = EXCLUDED.address,
                city = EXCLUDED.city,
                district = EXCLUDED.district,
                gender = EXCLUDED.gender,
                blood_type = EXCLUDED.blood_type
            "#
        )
        .bind(id)
        .bind(&payload.device_id)
        .bind(&payload.full_name)
        .bind(payload.birth_date)
        .bind(&payload.address)
        .bind(&payload.city)
        .bind(&payload.district)
        .bind(&payload.gender)
        .bind(&payload.blood_type)
        .execute(&state.pool)
        .await;

        if result.is_ok() {
            processed += 1;
        } else if let Err(e) = result {
            tracing::warn!("Failed to sync user {}: {}", payload.device_id, e);
        }
    }

    Ok((
        StatusCode::CREATED,
        Json(SyncResponse {
            processed,
            message: "Users synced successfully.".to_string(),
        }),
    ))
}

pub async fn sync_reports(
    State(state): State<crate::AppState>,
    Json(payloads): Json<Vec<ReportPayload>>,
) -> Result<impl IntoResponse, (StatusCode, String)> {
    let mut processed = 0;

    for payload in payloads {
        if payload.latitude < -90.0
            || payload.latitude > 90.0
            || payload.longitude < -180.0
            || payload.longitude > 180.0
        {
            continue; // Skip invalid coordinates in batch
        }

        let id = Uuid::new_v4();
        let point_wkt = format!(
            "SRID=4326;POINT({} {})",
            payload.longitude, payload.latitude
        );

        let result = sqlx::query(
            r#"
            INSERT INTO victim_reports (id, device_id, status, location, battery_level)
            VALUES ($1, $2, $3, ST_GeomFromEWKT($4), $5)
            "#,
        )
        .bind(id)
        .bind(&payload.device_id)
        .bind(&payload.status)
        .bind(&point_wkt)
        .bind(payload.battery_level)
        .execute(&state.pool)
        .await;

        if result.is_ok() {
            processed += 1;
        } else if let Err(e) = result {
            tracing::warn!(
                "Failed to sync report for device {}: {}",
                payload.device_id,
                e
            );
        }
    }

    Ok((
        StatusCode::CREATED,
        Json(SyncResponse {
            processed,
            message: "Reports synced successfully.".to_string(),
        }),
    ))
}

#[derive(Debug, Deserialize)]
pub struct EarthquakeLogPayload {
    pub device_id: String,
    pub latitude: f64,
    pub longitude: f64,
    pub intensity: Option<f32>,
}

#[derive(Serialize)]
pub struct EarthquakeLogResponse {
    pub id: Uuid,
    pub message: String,
    pub triggered_alert: bool,
}

pub async fn report_earthquake_log(
    State(state): State<crate::AppState>,
    Json(payload): Json<EarthquakeLogPayload>,
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

    // 1. Insert the log
    let res = sqlx::query(
        "INSERT INTO earthquake_logs (id, device_id, location, intensity) VALUES ($1, $2, ST_GeomFromEWKT($3), $4)"
    )
    .bind(id)
    .bind(&payload.device_id)
    .bind(&point_wkt)
    .bind(payload.intensity.unwrap_or(0.0))
    .execute(&state.pool)
    .await;

    if let Err(e) = res {
        tracing::error!("Failed to save earthquake log: {}", e);
        return Err((
            StatusCode::INTERNAL_SERVER_ERROR,
            "Database error".to_string(),
        ));
    }

    // 2. Count distinct devices in the last 5 minutes within 3km (approx 28km^2)
    let count_record: (i64, Option<f64>) = sqlx::query_as(
        r#"
        SELECT COUNT(DISTINCT device_id), AVG(intensity)
        FROM earthquake_logs
        WHERE ST_DWithin(location::geography, ST_MakePoint($1, $2)::geography, 3000)
        AND reported_at >= NOW() - INTERVAL '5 minutes'
        "#,
    )
    .bind(payload.longitude)
    .bind(payload.latitude)
    .fetch_one(&state.pool)
    .await
    .unwrap_or((0, Some(0.0)));

    let count = count_record.0;
    let avg_intensity = count_record.1.unwrap_or(0.0) as f32;
    let mut triggered_alert = false;

    if count >= 30 {
        // 3. Check if an official earthquake was already triggered in this 3km radius within the last 5 mins
        let already_triggered: (i64,) = sqlx::query_as(
            r#"
            SELECT COUNT(*)
            FROM earthquakes
            WHERE ST_DWithin(epicenter::geography, ST_MakePoint($1, $2)::geography, 3000)
            AND occurred_at >= NOW() - INTERVAL '5 minutes'
            "#,
        )
        .bind(payload.longitude)
        .bind(payload.latitude)
        .fetch_one(&state.pool)
        .await
        .unwrap_or((0,));

        if already_triggered.0 == 0 {
            // Promote to an official earthquake
            let eq_id = Uuid::new_v4();
            let _ = sqlx::query(
                r#"
                INSERT INTO earthquakes (id, magnitude, depth_km, location_name, epicenter)
                VALUES ($1, $2, $3, $4, ST_GeomFromEWKT($5))
                "#,
            )
            .bind(eq_id)
            .bind((avg_intensity * 10.0).round() / 10.0) // Calculated magnitude from devices
            .bind(5.0) // Default depth
            .bind(format!("Crowdsourced Alert ({} devices)", count))
            .bind(&point_wkt)
            .bind(payload.intensity.unwrap_or(0.0))
            .execute(&state.pool)
            .await;

            tracing::warn!(
                "Crowdsourced Earthquake TRIGGERED at {}, {} by {} devices!",
                payload.latitude,
                payload.longitude,
                count
            );

            let event = crate::mqtt::LiveEvent {
                event_type: "earthquake_alert".to_string(),
                drone_id: Some("CROWDSOURCE".to_string()),
                data: crate::mqtt::LiveData::Earthquake(serde_json::json!({
                    "magnitude": (avg_intensity * 10.0).round() / 10.0,
                    "depth_km": 5.0,
                    "location_name": format!("Crowdsourced Alert ({} devices)", count),
                    "latitude": payload.latitude,
                    "longitude": payload.longitude,
                    "occurred_at": chrono::Utc::now()
                })),
            };
            let _ = state.tx.send(event);
            triggered_alert = true;
        }
    }

    Ok((
        StatusCode::CREATED,
        Json(EarthquakeLogResponse {
            id,
            message: "Log received".to_string(),
            triggered_alert,
        }),
    ))
}

#[derive(Serialize, sqlx::FromRow)]
pub struct ProfileResponse {
    pub id: Uuid,
    pub device_id: String,
    pub full_name: String,
    pub birth_date: Option<NaiveDate>,
    pub address: Option<String>,
    pub city: Option<String>,
    pub district: Option<String>,
    pub gender: Option<String>,
    pub blood_type: Option<String>,
    pub registered_at: chrono::DateTime<chrono::Utc>,
}

#[derive(Serialize)]

pub struct DeleteProfileResponse {
    pub message: String,
}

pub async fn delete_profile(
    State(state): State<crate::AppState>,

    axum::extract::Path(device_id): axum::extract::Path<String>,
) -> Result<impl IntoResponse, (StatusCode, String)> {
    let result = sqlx::query("DELETE FROM users WHERE device_id = $1")
        .bind(&device_id)
        .execute(&state.pool)
        .await;

    match result {
        Ok(res) if res.rows_affected() > 0 => Ok((
            StatusCode::OK,
            Json(DeleteProfileResponse {
                message: "Account deleted successfully".to_string(),
            }),
        )),

        Ok(_) => Err((StatusCode::NOT_FOUND, "Profile not found".to_string())),

        Err(e) => {
            tracing::error!("Failed to delete profile: {}", e);

            Err((
                StatusCode::INTERNAL_SERVER_ERROR,
                "Database error".to_string(),
            ))
        }
    }
}

pub async fn get_profile(
    State(state): State<crate::AppState>,
    axum::extract::Path(device_id): axum::extract::Path<String>,
) -> Result<impl IntoResponse, (StatusCode, String)> {
    let result = sqlx::query_as::<_, ProfileResponse>(
        r#"
        SELECT 
            id, device_id, full_name, birth_date, address, city, district, gender, blood_type, registered_at
        FROM users
        WHERE device_id = $1
        "#
    )
    .bind(&device_id)
    .fetch_optional(&state.pool)
    .await;

    match result {
        Ok(Some(profile)) => Ok((StatusCode::OK, Json(profile))),
        Ok(None) => Err((StatusCode::NOT_FOUND, "Profile not found".to_string())),
        Err(e) => {
            tracing::error!("Failed to fetch profile: {}", e);
            Err((
                StatusCode::INTERNAL_SERVER_ERROR,
                "Database error".to_string(),
            ))
        }
    }
}
