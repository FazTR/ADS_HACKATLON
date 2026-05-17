use reqwest::Client;
use rumqttc::{AsyncClient, MqttOptions, QoS};
use serde_json::json;
use std::time::Duration;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    dotenvy::dotenv().ok();
    let api_key = std::env::var("API_KEY").unwrap_or_else(|_| "".to_string());

    println!("=== ADS (Acil Durum Sistemi) Simulation Started ===");

    println!("\n[1/3] Simulating Mobile App Registration...");
    let client = Client::new();
    let register_payload = json!({
        "device_id": "victims-phone-001",
        "full_name": "Ahmet Yılmaz",
        "birth_date": "1990-05-15",
        "address": "Atatürk Mah. Cumhuriyet Cad. No:1",
        "city": "İstanbul",
        "district": "Kadıköy",
        "gender": "Erkek",
        "blood_type": "A+"
    });

    let res = client
        .post("http://localhost:4030/api/v1/mobile/register")
        .header("x-api-key", &api_key)
        .json(&register_payload)
        .send()
        .await;

    match res {
        Ok(response) => {
            let status = response.status();
            let body = response.text().await?;
            println!("REST API Registration Response: {} - {}", status, body);
        }
        Err(e) => eprintln!("Failed to connect to REST API (Register): {}", e),
    }

    // 2. Simulating a Victim Mobile App reporting status
    println!("\n[2/3] Simulating Mobile App (Victim under rubble)...");
    let mobile_payload = json!({
        "device_id": "victims-phone-001",
        "status": "under_rubble",
        "latitude": 38.0123,
        "longitude": 35.0456,
        "battery_level": 15
    });

    let res = client
        .post("http://localhost:4030/api/v1/mobile/report")
        .header("x-api-key", &api_key)
        .json(&mobile_payload)
        .send()
        .await;

    match res {
        Ok(response) => {
            let status = response.status();
            let body = response.text().await?;
            println!("REST API Response: {} - {}", status, body);
        }
        Err(e) => eprintln!("Failed to connect to REST API: {}", e),
    }

    // 3. Simulating a Drone AI Detection
    println!("\n[3/5] Simulating Drone AI Detection (Edge AI)...");
    let mut mqttoptions = MqttOptions::new("drone-simulator", "localhost", 4033);
    mqttoptions.set_keep_alive(Duration::from_secs(5));

    let (mqtt_client, mut eventloop) = AsyncClient::new(mqttoptions, 10);

    // Run the event loop in the background to process the publish
    tokio::spawn(async move { while eventloop.poll().await.is_ok() {} });

    let drone_payload = json!({
        "confidence": 0.92,
        "label": "person",
        "latitude": 38.0125,
        "longitude": 35.0450
    });

    mqtt_client
        .publish(
            "drone/IHA-001/detection",
            QoS::AtLeastOnce,
            false,
            drone_payload.to_string(),
        )
        .await?;

    println!("Published AI Detection to MQTT Topic 'drone/IHA-001/detection'");
    println!("Payload: {}", drone_payload);

    // Wait a brief moment to ensure MQTT message is sent
    tokio::time::sleep(Duration::from_secs(1)).await;

    // 4. Simulating Drone Telemetry
    println!("\n[4/5] Simulating Drone Telemetry (Live Tracking)...");
    let telemetry_payload = json!({
        "status": "active",
        "battery_level": 88,
        "altitude": 120.5,
        "speed": 15.2,
        "latitude": 38.0130,
        "longitude": 35.0460
    });

    mqtt_client
        .publish(
            "drone/IHA-001/telemetry",
            QoS::AtLeastOnce,
            false,
            telemetry_payload.to_string(),
        )
        .await?;

    println!("Published Telemetry to MQTT Topic 'drone/IHA-001/telemetry'");
    println!("Payload: {}", telemetry_payload);

    tokio::time::sleep(Duration::from_secs(2)).await;

    // 5. Simulating Batch Sync (Flying AP / Data Mule)
    println!("\n[5/5] Simulating Offline Data Sync (Gossip Protocol via Drone)...");

    let batch_registers = json!([
        {
            "device_id": "victims-phone-002",
            "full_name": "Ayşe Demir",
            "birth_date": "1985-11-20",
            "blood_type": "0-"
        },
        {
            "device_id": "victims-phone-003",
            "full_name": "Mehmet Kaya",
            "birth_date": "1975-02-10",
            "blood_type": "B+"
        }
    ]);

    let res_sync_reg = client
        .post("http://localhost:4030/api/v1/mobile/sync/registers")
        .header("x-api-key", &api_key)
        .json(&batch_registers)
        .send()
        .await;

    match res_sync_reg {
        Ok(response) => {
            let status = response.status();
            let body = response.text().await?;
            println!("Batch Registers Sync Response: {} - {}", status, body);
        }
        Err(e) => eprintln!("Failed to sync registers: {}", e),
    }

    let batch_reports = json!([
        {
            "device_id": "victims-phone-002",
            "status": "injured",
            "latitude": 38.0120,
            "longitude": 35.0460,
            "battery_level": 45
        },
        {
            "device_id": "victims-phone-003",
            "status": "ok",
            "latitude": 38.0130,
            "longitude": 35.0440,
            "battery_level": 80
        }
    ]);

    let res_sync_rep = client
        .post("http://localhost:4030/api/v1/mobile/sync/reports")
        .header("x-api-key", &api_key)
        .json(&batch_reports)
        .send()
        .await;

    match res_sync_rep {
        Ok(response) => {
            let status = response.status();
            let body = response.text().await?;
            println!("Batch Reports Sync Response: {} - {}", status, body);
        }
        Err(e) => eprintln!("Failed to sync reports: {}", e),
    }

    // 6. Simulating Drone Navigation
    println!("\n[6/6] Simulating Drone Navigation Command Reception...");
    let mut mqttoptions2 = MqttOptions::new("drone-listener-sim", "localhost", 4033);
    mqttoptions2.set_keep_alive(Duration::from_secs(5));
    let (listener_client, mut listener_loop) = AsyncClient::new(mqttoptions2, 10);

    listener_client
        .subscribe("drone/IHA-001/command", QoS::AtLeastOnce)
        .await?;

    // Fire a REST POST to navigate
    let nav_payload = json!({
        "target_latitude": 39.8450,
        "target_longitude": 33.5100
    });
    let res_nav = client
        .post("http://localhost:4030/api/v1/admin/drones/IHA-001/navigate")
        .header("x-api-key", &api_key)
        .json(&nav_payload)
        .send()
        .await;

    match res_nav {
        Ok(r) => println!(
            "REST API Navigate Response: {} - {}",
            r.status(),
            r.text().await?
        ),
        Err(e) => eprintln!("Failed to send navigate command: {}", e),
    }

    // Wait for MQTT message
    if let Ok(rumqttc::Event::Incoming(rumqttc::Incoming::Publish(p))) = listener_loop.poll().await
        && let Ok(msg) = String::from_utf8(p.payload.to_vec())
    {
        println!("Drone IHA-001 Received Command via MQTT: {}", msg);
    }

    // 7. Simulating Live Drone Flight (Linear Interpolation)
    println!("\n[7/7] Simulating Live Drone Flight (Linear Interpolation)...");
    let start_lat = 39.8450;
    let start_lon = 33.5100;
    let end_lat = 39.8550;
    let end_lon = 33.4500;
    let steps = 15;

    for i in 0..=steps {
        let t = i as f64 / steps as f64;
        let current_lat = start_lat + (end_lat - start_lat) * t;
        let current_lon = start_lon + (end_lon - start_lon) * t;

        let telemetry = json!({
            "status": "flying",
            "battery_level": 88 - (i as i16 / 2),
            "altitude": 120.5,
            "speed": 15.2,
            "latitude": current_lat,
            "longitude": current_lon
        });

        mqtt_client
            .publish(
                "drone/IHA-001/telemetry",
                QoS::AtLeastOnce,
                false,
                telemetry.to_string(),
            )
            .await?;

        println!(
            "Flight step {}/{} -> Lat: {:.5}, Lon: {:.5}",
            i, steps, current_lat, current_lon
        );
        tokio::time::sleep(Duration::from_secs(1)).await;
    }

    println!("\n=== Simulation Complete ===");
    println!(
        "You can check the database or connect to http://localhost:4030/api/v1/stream to see the SSE event."
    );

    Ok(())
}
