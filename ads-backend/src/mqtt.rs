use rumqttc::{AsyncClient, Event, Incoming, MqttOptions, QoS};
use serde::{Deserialize, Serialize};
use sqlx::PgPool;
use std::time::Duration;
use tokio::sync::broadcast;
use uuid::Uuid;

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct DroneDetection {
    pub confidence: f32,
    pub label: String,
    pub latitude: f64,
    pub longitude: f64,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct DroneTelemetry {
    pub status: String,
    pub battery_level: i16,
    pub altitude: f32,
    pub speed: f32,
    pub latitude: f64,
    pub longitude: f64,
}

#[derive(Debug, Serialize, Clone)]
#[serde(untagged)]
pub enum LiveData {
    Detection(DroneDetection),
    Telemetry(DroneTelemetry),
    Earthquake(serde_json::Value),
}

#[derive(Debug, Serialize, Clone)]
pub struct LiveEvent {
    pub event_type: String,
    pub drone_id: Option<String>,
    pub data: LiveData,
}

pub async fn start_mqtt_client(
    pool: PgPool,
    broker_host: String,
    broker_port: u16,
    tx: broadcast::Sender<LiveEvent>,
) -> AsyncClient {
    let mut mqttoptions = MqttOptions::new(
        format!("ads-backend-{}", Uuid::new_v4()),
        broker_host,
        broker_port,
    );
    mqttoptions.set_keep_alive(Duration::from_secs(5));

    let (client, mut eventloop) = AsyncClient::new(mqttoptions, 10);

    client
        .subscribe("drone/+/detection", QoS::AtLeastOnce)
        .await
        .expect("Failed to subscribe to detection topic");

    client
        .subscribe("drone/+/telemetry", QoS::AtLeastOnce)
        .await
        .expect("Failed to subscribe to telemetry topic");

    let client_clone = client.clone();

    tokio::spawn(async move {
        loop {
            match eventloop.poll().await {
                Ok(Event::Incoming(Incoming::Publish(p))) => {
                    let topic = p.topic;
                    if let Ok(payload_str) = String::from_utf8(p.payload.to_vec()) {
                        let parts: Vec<&str> = topic.split('/').collect();
                        let drone_id = if parts.len() >= 2 {
                            parts[1].to_string()
                        } else {
                            "unknown".to_string()
                        };

                        if topic.ends_with("/detection") {
                            if let Ok(detection) =
                                serde_json::from_str::<DroneDetection>(&payload_str)
                            {
                                let id = Uuid::new_v4();
                                let point_wkt = format!(
                                    "SRID=4326;POINT({} {})",
                                    detection.longitude, detection.latitude
                                );

                                let res = sqlx::query(
                                    r#"
                                    INSERT INTO drone_detections (id, drone_id, label, confidence, location)
                                    VALUES ($1, $2, $3, $4, ST_GeomFromEWKT($5))
                                    "#
                                )
                                .bind(id)
                                .bind(&drone_id)
                                .bind(&detection.label)
                                .bind(detection.confidence)
                                .bind(&point_wkt)
                                .execute(&pool)
                                .await;

                                if let Err(e) = res {
                                    tracing::error!("Failed to save drone detection: {}", e);
                                } else {
                                    let live_event = LiveEvent {
                                        event_type: "drone_detection".to_string(),
                                        drone_id: Some(drone_id),
                                        data: LiveData::Detection(detection),
                                    };
                                    let _ = tx.send(live_event);
                                }
                            }
                        } else if topic.ends_with("/telemetry")
                            && let Ok(telemetry) =
                                serde_json::from_str::<DroneTelemetry>(&payload_str)
                        {
                            let point_wkt = format!(
                                "SRID=4326;POINT({} {})",
                                telemetry.longitude, telemetry.latitude
                            );

                            let res = sqlx::query(
                                    r#"
                                    INSERT INTO drones (drone_id, status, battery_level, altitude, speed, location, last_seen)
                                    VALUES ($1, $2, $3, $4, $5, ST_GeomFromEWKT($6), NOW())
                                    ON CONFLICT (drone_id) DO UPDATE
                                    SET status = EXCLUDED.status,
                                        battery_level = EXCLUDED.battery_level,
                                        altitude = EXCLUDED.altitude,
                                        speed = EXCLUDED.speed,
                                        location = EXCLUDED.location,
                                        last_seen = NOW()
                                    "#
                                )
                                .bind(&drone_id)
                                .bind(&telemetry.status)
                                .bind(telemetry.battery_level)
                                .bind(telemetry.altitude)
                                .bind(telemetry.speed)
                                .bind(&point_wkt)
                                .execute(&pool)
                                .await;

                            if let Err(e) = res {
                                tracing::error!("Failed to update drone telemetry: {}", e);
                            } else {
                                let live_event = LiveEvent {
                                    event_type: "drone_telemetry".to_string(),
                                    drone_id: Some(drone_id),
                                    data: LiveData::Telemetry(telemetry),
                                };
                                let _ = tx.send(live_event);
                            }
                        }
                    }
                }
                Ok(Event::Incoming(Incoming::Disconnect)) => {
                    tracing::warn!("MQTT disconnected, trying to reconnect...");
                }
                Err(e) => {
                    tracing::error!("MQTT connection error: {:?}. Retrying in 5 seconds...", e);
                    tokio::time::sleep(Duration::from_secs(5)).await;
                }
                _ => {} // Ignore other events
            }
        }
    });

    client_clone
}
