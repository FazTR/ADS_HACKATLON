mod admin;
mod auth;
mod mobile;
mod mqtt;
mod stream;
mod system;

use axum::{
    Router,
    routing::{get, post},
};
use mqtt::LiveEvent;
use sqlx::postgres::{PgPool, PgPoolOptions};
use std::env;
use std::net::SocketAddr;
use tokio::sync::broadcast;

#[derive(Clone)]
pub struct AppState {
    pub pool: PgPool,
    pub tx: broadcast::Sender<LiveEvent>,
    pub mqtt_client: rumqttc::AsyncClient,
}

pub fn create_app(state: AppState) -> Router {
    let api_routes = Router::new()
        .route("/mobile/register", post(mobile::register_user))
        .route(
            "/mobile/profile/:device_id",
            get(mobile::get_profile).delete(mobile::delete_profile),
        )
        .route("/mobile/report", post(mobile::submit_report))
        .route(
            "/mobile/earthquake_report",
            post(mobile::report_earthquake_log),
        )
        .route("/mobile/sync/registers", post(mobile::sync_registers))
        .route("/mobile/sync/reports", post(mobile::sync_reports))
        .route("/mobile/nearby", get(mobile::get_nearby_victims))
        .route("/admin/users", get(admin::get_all_users))
        .route("/admin/reports", get(admin::get_all_reports))
        .route("/admin/detections", get(admin::get_all_detections))
        .route("/admin/drones", get(admin::get_all_drones))
        .route(
            "/admin/earthquake_logs",
            get(admin::get_all_earthquake_logs),
        )
        .route("/admin/export", get(admin::export_database_sql))
        .route("/admin/drones/:drone_id/camera", post(admin::toggle_camera))
        .route("/admin/drones/:drone_id/video_feed", get(admin::video_feed))
        .route(
            "/admin/drones/:drone_id/navigate",
            post(admin::navigate_drone),
        )
        .route("/system/earthquakes", get(system::get_earthquakes))
        .route("/system/earthquakes", post(system::report_earthquake))
        .route("/stream", get(stream::sse_handler))
        .route_layer(axum::middleware::from_fn(auth::require_api_key));

    Router::new()
        .route("/health", get(health_check))
        .nest("/api/v1", api_routes)
        .with_state(state)
}

#[tokio::main]
async fn main() {
    dotenvy::dotenv().ok();
    tracing_subscriber::fmt::init();
    tracing::info!("Starting ADS Backend...");

    let database_url = env::var("DATABASE_URL")
        .unwrap_or_else(|_| "postgres://user:password@localhost:4031/ads_db".to_string());

    let mqtt_host = env::var("MQTT_HOST").unwrap_or_else(|_| "localhost".to_string());

    let pool = PgPoolOptions::new()
        .max_connections(5)
        .connect(&database_url)
        .await
        .expect("Failed to connect to the database");

    // Initialize broadcast channel for SSE (capacity 100)
    let (tx, _rx) = broadcast::channel::<LiveEvent>(100);

    // Start background MQTT subscriber for drone telemetry
    let mqtt_client = mqtt::start_mqtt_client(pool.clone(), mqtt_host, 4033, tx.clone()).await;

    // Create shared app state
    let state = AppState {
        pool,
        tx: tx.clone(),
        mqtt_client,
    };

    let app = create_app(state);

    let addr = SocketAddr::from(([0, 0, 0, 0], 4030));
    tracing::info!("Server is listening on {}", addr);

    let listener = tokio::net::TcpListener::bind(&addr).await.unwrap();
    axum::serve(listener, app).await.unwrap();
}

async fn health_check() -> &'static str {
    "ADS Backend is running"
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::{
        body::Body,
        http::{Request, StatusCode},
    };
    use serde_json::json;
    use tower::ServiceExt; // for `call`

    // Helper function to setup a mock AppState
    async fn setup_state() -> AppState {
        let database_url = env::var("DATABASE_URL")
            .unwrap_or_else(|_| "postgres://user:password@localhost:4031/ads_db".to_string());

        let pool = PgPoolOptions::new()
            .max_connections(2)
            .connect(&database_url)
            .await
            .expect("Failed to connect to the test database");

        let (tx, _rx) = broadcast::channel::<LiveEvent>(10);
        let mqtt_host = env::var("MQTT_HOST").unwrap_or_else(|_| "localhost".to_string());
        let mqtt_client = mqtt::start_mqtt_client(pool.clone(), mqtt_host, 4033, tx.clone()).await;

        AppState {
            pool,
            tx,
            mqtt_client,
        }
    }

    #[tokio::test]
    async fn test_health_check() {
        let state = setup_state().await;
        let app = create_app(state);

        let response = app
            .oneshot(
                Request::builder()
                    .uri("/health")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::OK);
    }

    #[tokio::test]
    async fn test_unauthorized_access() {
        let state = setup_state().await;
        let app = create_app(state);

        // Trying to access an admin endpoint without API Key
        let response = app
            .oneshot(
                Request::builder()
                    .uri("/api/v1/admin/users")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
    }

    #[tokio::test]
    async fn test_valid_registration() {
        let state = setup_state().await;
        let app = create_app(state);

        let payload = json!({
            "device_id": "test-device-uuid-1234",
            "full_name": "Test User",
            "birth_date": "1990-01-01",
            "blood_type": "0+"
        });

        let response = app
            .oneshot(
                Request::builder()
                    .method("POST")
                    .uri("/api/v1/mobile/register")
                    .header(
                        "x-api-key",
                        env::var("API_KEY").unwrap_or_else(|_| "".to_string()),
                    )
                    .header("content-type", "application/json")
                    .body(Body::from(payload.to_string()))
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::CREATED);
    }

    #[tokio::test]
    async fn test_invalid_registration_missing_fields() {
        let state = setup_state().await;
        let app = create_app(state);

        let payload = json!({
            "device_id": "test-device-uuid-1234",
            // missing full_name and birth_date
        });

        let response = app
            .oneshot(
                Request::builder()
                    .method("POST")
                    .uri("/api/v1/mobile/register")
                    .header(
                        "x-api-key",
                        env::var("API_KEY").unwrap_or_else(|_| "".to_string()),
                    )
                    .header("content-type", "application/json")
                    .body(Body::from(payload.to_string()))
                    .unwrap(),
            )
            .await
            .unwrap();

        // Should be 400 Bad Request or 422 Unprocessable Entity due to JSON deserialization failure
        assert!(response.status().is_client_error());
    }

    #[tokio::test]
    async fn test_earthquake_log_submission() {
        let state = setup_state().await;
        let app = create_app(state);

        let payload = json!({
            "device_id": "test-automated-device-eq",
            "latitude": 39.5,
            "longitude": 32.5
        });

        let response = app
            .oneshot(
                Request::builder()
                    .method("POST")
                    .uri("/api/v1/mobile/earthquake_report")
                    .header(
                        "x-api-key",
                        env::var("API_KEY").unwrap_or_else(|_| "".to_string()),
                    )
                    .header("content-type", "application/json")
                    .body(Body::from(payload.to_string()))
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::CREATED);
    }
}
