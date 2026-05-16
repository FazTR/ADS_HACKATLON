# ADS (Acil Durum Sistemi) - Tam Kapsamlı API Documentation

**Versiyon:** 1.0.0
**Base URL:** `https://<server-ip>:4030/api/v1`

**Authentication (Güvenlik):**
Sisteme (Health Check hariç) atılacak tüm isteklerde HTTP Header içerisinde aşağıdaki API Key gönderilmelidir. Gönderilmemesi durumunda `401 Unauthorized` hatası alınır.
*   **Header:** `x-api-key`
*   **Değer:** `.env` dosyasındaki `API_KEY` değeri (örn: `<YOUR_API_KEY>`)

Bu doküman, AFAD kriz merkezi yönetim paneli (Admin Dashboard) ve afetzedelerin/kurtarma görevlilerinin kullandığı mobil uygulamanın (Mobile App) backend ile haberleşeceği REST API uç noktalarını açıklar.

---

# 1. Mobil Cihaz API Uç Noktaları (Mobile & Mesh Sync)
**Hedef Kitle:** iOS (Swift) ve Android (Kotlin) Geliştiricileri.

## 1.1 Kullanıcı Kaydı (Registration)

Afetzedenin uygulamayı ilk indirdiğinde kişisel ve tıbbi bilgilerini sisteme kaydettiği uç nokta. Cihaz kimliği (`device_id`) üzerinden kayıt oluşturulur. Cihaz ID'si ile tekrar istek atılırsa var olan kayıt güncellenir.

*   **Endpoint:** `POST /mobile/register`
*   **Content-Type:** `application/json`

### Request Payload (JSON)
| Alan | Tip | Kısıtlama | Açıklama |
| :--- | :--- | :--- | :--- |
| `device_id` | string | Zorunlu | Cihazın benzersiz kimliği. |
| `full_name` | string | Zorunlu | Ad Soyad. |
| `birth_date` | string (date) | Zorunlu | YYYY-MM-DD formatında doğum tarihi. |
| `address` | string | İsteğe Bağlı | Açık adres. |
| `city` | string | İsteğe Bağlı | İl. |
| `district` | string | İsteğe Bağlı | İlçe. |
| `gender` | string | İsteğe Bağlı | Cinsiyet. |
| `blood_type` | string | İsteğe Bağlı | Kan grubu. |

**Örnek İstek:**
```json
{
  "device_id": "victims-phone-001",
  "full_name": "Ahmet Yılmaz",
  "birth_date": "1990-05-15",
  "address": "Atatürk Mah. Cumhuriyet Cad. No:1",
  "city": "İstanbul",
  "district": "Kadıköy",
  "gender": "Erkek",
  "blood_type": "A+"
}
```

### Response
*   **201 Created:** Kayıt/Güncelleme başarılı.
    ```json
    {
      "id": "generated-uuid-string",
      "message": "User successfully registered."
    }
    ```
*   **500 Internal Server Error:** Veritabanı hatası.

---

## 1.2 Profil Bilgilerini Getirme (Mevcut Kullanıcı)

Kullanıcı uygulamayı silip yüklediğinde veya farklı bir cihazdan giriş yapıp aynı `device_id` değerini kullandığında, daha önce sisteme kaydettiği profil bilgilerini (Ad, Adres, Kan grubu vb.) geri çekmek için kullanılır. 

*   **Endpoint:** `GET /mobile/profile/:device_id`
*   **Method:** `GET`

*   **Response (200 OK):**
    ```json
    {
      "id": "uuid-v4",
      "device_id": "victims-phone-001",
      "full_name": "Ahmet Yılmaz",
      "birth_date": "1990-05-15",
      "address": "Atatürk Mah. Cumhuriyet Cad. No:1",
      "city": "İstanbul",
      "district": "Kadıköy",
      "gender": "Erkek",
      "blood_type": "A+",
      "registered_at": "2023-11-20T10:15:00Z"
    }
    ```
*   **Response (404 Not Found):** Kullanıcı bulunamazsa `Profile not found` döner.

---

## 1.3 Hesabı Sil (Delete Account)

Kullanıcının sisteme kaydettiği tüm profil bilgilerini kalıcı olarak silmek için kullanılır. KVKK/GDPR uyumluluğu gereği bu işlem `device_id` üzerinden hesaba ait diğer bağlı (rapor vb.) kayıtları da `CASCADE` ile veritabanından kalıcı olarak siler.

*   **Endpoint:** `DELETE /mobile/profile/:device_id`
*   **Method:** `DELETE`

### Response
*   **200 OK:** Silme işlemi başarılı.
    ```json
    {
      "message": "Account deleted successfully"
    }
    ```
*   **404 Not Found:** Verilen `device_id` ile eşleşen bir kayıt bulunamadı.

---

## 1.4 Durum Bildirme (Afetzede Endpoint'i)

Afetzedenin cihazı BLE üzerinden sinyal yayarken, eğer internet bağlantısı bulursa bu endpoint'e kendi durumunu ve konumunu POST eder.

*   **Endpoint:** `POST /mobile/report`
*   **Content-Type:** `application/json`

### Request Payload (JSON)
| Alan | Tip | Kısıtlama | Açıklama |
| :--- | :--- | :--- | :--- |
| `device_id` | string | Zorunlu | Cihazın benzersiz kimliği (Örn: UUID veya Mac Adresi). |
| `status` | string (enum) | Zorunlu | `ok` (İyiyim), `under_rubble` (Enkaz Altındayım), `injured` (Yaralıyım). |
| `latitude` | float | -90.0 ile 90.0 | Enlem değeri. |
| `longitude` | float | -180.0 ile 180.0 | Boylam değeri. |
| `battery_level` | int | İsteğe Bağlı | Yüzde olarak batarya seviyesi (0-100). |

**Örnek İstek:**
```json
{
  "device_id": "victims-phone-001",
  "status": "under_rubble",
  "latitude": 38.0123,
  "longitude": 35.0456,
  "battery_level": 15
}
```

### Response
*   **201 Created:** Kayıt başarılı.
    ```json
    {
      "id": "generated-uuid-string",
      "message": "Report successfully submitted."
    }
    ```
*   **400 Bad Request:** Geçersiz koordinat (-90 ile 90 sınırları dışı vb.).
*   **500 Internal Server Error:** Veritabanı hatası.

---

## Çevrimdışı Veri Taşıma Mimarisi (Gossip Protocol & Drone Data Mule)

Afet anında tüm hücresel ağlar ve internet çöktüğünde sistem şu şekilde çalışır:
1. **Gossip Protocol:** İnterneti olmayan telefonlar (enkaz altındaki veya yüzeydeki) BLE (Bluetooth) ve Wi-Fi Direct kullanarak kendi durumlarını ve birbirlerinden duydukları diğer kişilerin durumlarını takas eder (Store-and-Forward).
2. **Flying AP (Drone Mules):** Sahada gezen arama kurtarma droneları şifresiz, yerel bir Wi-Fi ağı (`ADS-RESCUE-NET`) yayar. Arama kurtarma personelinin telefonu drone'un ağına otomatik bağlanır ve gün boyunca diğer telefonlardan topladığı yüzlerce afetzede verisini aşağıdaki **Toplu Senkronizasyon (Batch Sync)** uç noktalarını kullanarak drone'a aktarır. Drone kriz merkezine dönüp internete (Starlink) bağlandığında ise içindeki bu verileri tek bir JSON Array paketi olarak aynı uç noktalardan AFAD ana sunucusuna (`ADS Backend`) "kusar".

---

## 1.5 Kitle Kaynaklı Deprem Bildirimi (Crowdsourced Earthquake Detection)

Mobil cihaz (uygulama arkada çalışırken veya kullanıcı butona bastığında) ciddi bir sarsıntı hissettiğinde bu uç noktaya bir log atar. Sistem, aynı 3 km çapındaki bölgeden (yaklaşık 28 km2) son 5 dakika içinde en az 30 farklı cihazdan bu bildirim gelirse, bunu otomatik olarak bir "Deprem" (Earthquake) olayı olarak kaydeder ve AFAD kriz merkezindeki tüm haritalara "Kırmızı Alarm" (SSE) olarak yayınlar.

*   **Endpoint:** `POST /mobile/earthquake_report`
*   **Content-Type:** `application/json`

### Request Payload (JSON)
| Alan | Tip | Kısıtlama | Açıklama |
| :--- | :--- | :--- | :--- |
| `device_id` | string | Zorunlu | Cihazın benzersiz kimliği. |
| `latitude` | float | -90.0 ile 90.0 | Enlem değeri. |
| `longitude` | float | -180.0 ile 180.0 | Boylam değeri. |
| `intensity` | float | İsteğe Bağlı | Cihazın ivmeölçer / jiroskop sensörlerinden hesaplanan tahmini sarsıntı şiddeti (Örn: 4.5, 6.2). Sistem, 30 cihazın ortalamasını alarak gerçek deprem büyüklüğünü (magnitude) hesaplar. Gönderilmezse `0.0` kabul edilir. |

**Örnek İstek:**
```json
{
  "device_id": "victims-phone-001",
  "latitude": 38.0123,
  "longitude": 35.0456,
  "intensity": 5.4
}
```

### Response
*   **201 Created:** Log başarıyla alındı. Eğer bu log 30. log ise ve deprem alarmı tetiklendiyse `triggered_alert: true` döner.
    ```json
    {
      "id": "uuid-v4",
      "message": "Log received",
      "triggered_alert": false 
    }
    ```

---

## 1.6 Toplu Kullanıcı Senkronizasyonu (Batch Sync Registers)
Yüzlerce kullanıcının kayıt bilgisini tek seferde sisteme işler. Eğer kayıt varsa günceller, yoksa ekler.

*   **Endpoint:** `POST /mobile/sync/registers`
*   **Content-Type:** `application/json`

**Örnek İstek Payload'u (JSON Array):**
```json
[
  {
    "device_id": "victims-phone-001",
    "full_name": "Ahmet Yılmaz",
    "birth_date": "1990-05-15",
    "blood_type": "A+"
  },
  {
    "device_id": "victims-phone-002",
    "full_name": "Ayşe Demir",
    "birth_date": "1985-11-20",
    "blood_type": "0-"
  }
]
```

## 1.7 Toplu Durum Senkronizasyonu (Batch Sync Reports)
Yüzlerce afetzede lokasyon/durum raporunu tek seferde sisteme işler. (Önce kullanıcı kayıtlarının (Registers) senkronize edilmesi önerilir).

*   **Endpoint:** `POST /mobile/sync/reports`
*   **Content-Type:** `application/json`

**Örnek İstek Payload'u (JSON Array):**
```json
[
  {
    "device_id": "victims-phone-001",
    "status": "under_rubble",
    "latitude": 38.0123,
    "longitude": 35.0456,
    "battery_level": 10
  },
  {
    "device_id": "victims-phone-002",
    "status": "injured",
    "latitude": 38.0120,
    "longitude": 35.0460,
    "battery_level": 45
  }
]
```

---

## 1.8 Yakındakileri Bulma (Kurtarma Ekibi / Radar)

Sahadaki bir kurtarma görevlisinin (veya uygulamanın), bulunduğu noktanın belirli bir yarıçapı (metre cinsinden) içindeki diğer vakaları uzaklıklarına göre sıralı olarak çekmesini sağlar.

*   **Endpoint:** `GET /mobile/nearby`
*   **Method:** `GET`

### Query Parametreleri (URL Üzerinde)
| Parametre | Tip | Açıklama |
| :--- | :--- | :--- |
| `lat` | float | Merkeze alınacak enlem. |
| `lon` | float | Merkeze alınacak boylam. |
| `radius_m` | float | Metre cinsinden arama yarıçapı (Örn: 500 = 500 metre çapında ara). |

**Örnek İstek:**
`GET /mobile/nearby?lat=38.0123&lon=35.0456&radius_m=500`

### Response
*   **200 OK:**
    ```json
    [
      {
        "device_id": "victims-phone-001",
        "status": "under_rubble",
        "distance_m": 124.5
      },
      {
        "device_id": "victims-phone-002",
        "status": "injured",
        "distance_m": 450.2
      }
    ]
    ```

---

# 2. Yönetim ve Kriz Merkezi API Uç Noktaları (Admin Dashboard)
**Hedef Kitle:** Frontend (Next.js / React / Vue) Geliştiricileri ve Kriz Merkezi Operatörleri.

## 2.1 Kayıtlı Vatandaş (Kullanıcı) Listesi

Sisteme kayıtlı tüm vatandaşların (afetzedeler, gönüllüler vb.) kişisel ve tıbbi bilgilerini listelemek için kullanılır. Rapor atmasa bile sisteme kayıtlı olan herkes bu listede görünür. En yeni kayıttan eskiye doğru sıralanır.

*   **Endpoint:** `GET /admin/users`
*   **Response (200 OK):**
    ```json
    [
      {
        "id": "uuid-v4",
        "device_id": "victims-phone-001",
        "full_name": "Ahmet Yılmaz",
        "birth_date": "1990-05-15",
        "address": "Atatürk Mah. Cumhuriyet Cad. No:1",
        "city": "İstanbul",
        "district": "Kadıköy",
        "gender": "Erkek",
        "blood_type": "A+",
        "registered_at": "2023-11-20T10:15:00Z"
      }
    ]
    ```

---

## 2.2 Geçmiş Afetzede Verileri (Initial Load)

Harita ilk açıldığında, o ana kadar mobil uygulamalardan gelmiş tüm afetzede (İyiyim/Enkaz Altındayım) verilerini haritaya basmak için kullanılır. Yeniden eskiye doğru 1000 kayıt getirir.

*   **Endpoint:** `GET /admin/reports`
*   **Response (200 OK):**
    ```json
    [
      {
        "id": "uuid-v4",
        "device_id": "victims-phone-001",
        "status": "under_rubble", // ok, injured, under_rubble
        "latitude": 38.0123,
        "longitude": 35.0456,
        "battery_level": 15,
        "reported_at": "2023-11-20T10:20:30Z",
        "full_name": "Ahmet Yılmaz",
        "birth_date": "1990-05-15",
        "address": "Atatürk Mah. Cumhuriyet Cad. No:1",
        "city": "İstanbul",
        "district": "Kadıköy",
        "gender": "Erkek",
        "blood_type": "A+"
      }
    ]
    ```

---

## 2.3 Geçmiş Drone Yapay Zeka Bulguları

Canlı yayın (SSE) başlamadan önce tespit edilmiş ve veri tabanına kaydedilmiş tüm geçmiş drone bildirimlerini (insan/enkaz tespitleri) getirir.

*   **Endpoint:** `GET /admin/detections`
*   **Response (200 OK):**
    ```json
    [
      {
        "id": "uuid-v4",
        "drone_id": "IHA-001",
        "label": "person", // person, vehicle, rubble
        "confidence": 0.92,
        "latitude": 38.0125,
        "longitude": 35.0450,
        "detected_at": "2023-11-20T10:25:30Z"
      }
    ]
    ```

---

## 2.4 Canlı Drone Telemetri Verileri (Harita İzleme)

Gökyüzündeki tüm droneların o anki son konumlarını (irtifa, hız, batarya vb.) getirir. Her drone için sadece 1 tane en güncel kayıt döner. Canlı haritada droneların yerini göstermek için kullanılır. Dronelar bu verilerini saniyede bir MQTT `drone/+/telemetry` üzerinden merkeze fırlatırlar.

*   **Endpoint:** `GET /admin/drones`
*   **Response (200 OK):**
    ```json
    [
      {
        "drone_id": "IHA-ALPHA",
        "status": "active", // active, returning, offline vb.
        "battery_level": 85,
        "altitude": 120.5,
        "speed": 15.2,
        "latitude": 38.0125,
        "longitude": 35.0450,
        "last_seen": "2023-11-20T10:25:30Z"
      }
    ]
    ```

---

## 2.5 Drone Kamera Kontrolü (On-Demand Video)

Drone'un kamerasını (canlı akışını) açıp kapatmak için drone'a komut gönderir. Bu komut MQTT `drone/:drone_id/command` kanalı üzerinden fırlatılır. Kameralar batarya tasarrufu için varsayılan olarak kapalıdır.

*   **Endpoint:** `POST /admin/drones/:drone_id/camera`
*   **Request Payload (JSON):**
    ```json
    {
      "action": "start" // "start" veya "stop"
    }
    ```
*   **Response (200 OK):**
    ```json
    {
      "message": "Command sent successfully"
    }
    ```

**Frontend Kullanım Örneği (Kamerayı Açma):**
```javascript
// Kullanıcı haritada drone'a tıklayıp "Kamerayı Aç" dediğinde çalışır
async function startCamera(droneId) {
  try {
    const res = await fetch(`http://<server-ip>:4030/api/v1/admin/drones/${droneId}/camera`, {
      method: "POST",
      headers: {
        "x-api-key": "<YOUR_API_KEY>",
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ action: "start" })
    });
    
    if (res.ok) {
      console.log("Kamera açılma emri verildi. Video yükleniyor...");
      // State güncellenerek <video> elementi render edilebilir.
      setCameraActive(true);
    }
  } catch (error) {
    console.error("Kamera açılamadı", error);
  }
}
```

---

## 2.6 Drone Canlı Video Akışı (Mock/Test Feed)

Geliştirme ve test aşaması için sahte (mock) bir statik video döner. Video (MP4) formatında stream edilir. Kameranın açık olduğu (`cameraActive === true`) durumlarda ekranda gösterilir.

*   **Endpoint:** `GET /admin/drones/:drone_id/video_feed`
*   **Response:** `video/mp4` formatında binary dosya.

**Frontend Kullanım Örneği (Video Render):**
```jsx
// React / Next.js Component Örneği
function DroneVideoPlayer({ droneId, cameraActive }) {
  if (!cameraActive) {
    return <div className="video-placeholder">Kamera Kapalı</div>;
  }

  // Kamera aktifse videoyu render et ve src olarak doğrudan endpoint'i ver.
  // Video etiketinin API Key (Header) gönderemediğini unutmayın. 
  // Gerçek senaryoda bu uç nokta stream sunucusu (HLS/WebRTC) üzerinden açık olur.
  return (
    <div className="video-container">
      <video 
        src={`http://<server-ip>:4030/api/v1/admin/drones/${droneId}/video_feed`} 
        autoPlay 
        muted 
        controls 
        style={{ width: '100%', borderRadius: '8px' }}
      />
    </div>
  );
}
```

---

## 2.7 Harita Üzerinden Drone Kontrolü (Waypoint Navigasyonu)

Harita üzerinden (Google Maps tarzı) belirli bir noktaya tıklandığında, drone'un o koordinata doğru otonom olarak uçmasını sağlayan emri fırlatır. Komut MQTT `drone/:drone_id/command` kanalı üzerinden iletilir.

*   **Endpoint:** `POST /admin/drones/:drone_id/navigate`
*   **Request Payload (JSON):**
    ```json
    {
      "target_latitude": 39.8450,
      "target_longitude": 33.5100
    }
    ```
*   **Response (200 OK):**
    ```json
    {
      "message": "Navigation command sent successfully"
    }
    ```

**Frontend Kullanım Örneği (Haritaya Tıklama):**
```javascript
// Google Maps veya Leaflet onClick event'i tetiklendiğinde
async function handleMapClick(event, activeDroneId) {
  const lat = event.latlng.lat;
  const lng = event.latlng.lng;

  try {
    const res = await fetch(`http://<server-ip>:4030/api/v1/admin/drones/${activeDroneId}/navigate`, {
      method: "POST",
      headers: {
        "x-api-key": "<YOUR_API_KEY>",
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ 
        target_latitude: lat,
        target_longitude: lng
      })
    });
    
    if (res.ok) {
      console.log(`Drone ${activeDroneId}, ${lat}, ${lng} hedefine yönlendiriliyor.`);
      // Haritada hedef noktaya bir "Hedef (Target)" pini koyabilirsiniz.
    }
  } catch (error) {
    console.error("Yönlendirme başarısız", error);
  }
}
```

---

## 2.8 Canlı Harita Akışı (Server-Sent Events) - ÇOK KRİTİK

Dashboard açık olduğu sürece çalışan kalıcı bağlantıdır (WebSockets'e alternatiftir). Dronelar sahada yeni bir kişi tespit ettiğinde MQTT üzerinden gelen veri, anında (milisaniyeler içinde) tarayıcıya bu stream üzerinden "Push" edilir.

*   **Endpoint:** `GET /stream`
*   **Connection Type:** `text/event-stream`
*   **Kullanım Önerisi (Next.js / Frontend):**
    ```javascript
    const eventSource = new EventSource("http://<server-ip>:4030/api/v1/stream");
    
    eventSource.addEventListener("drone_detection", (event) => {
        const data = JSON.parse(event.data);
        console.log("Yeni drone tespiti!", data.drone_id, data.data.latitude);
        // Haritaya anında kırmızı bir marker (nokta) ekleyin.
    });
    ```

### SSE Event Formatı (Yapay Zeka Tespiti)
Her event bir JSON stringi olarak akar. Örnek bir `event.data` içeriği:
```json
{
  "event_type": "drone_detection",
  "drone_id": "IHA-001",
  "data": {
    "confidence": 0.92,
    "label": "person",
    "latitude": 38.0125,
    "longitude": 35.0450
  }
}
```

### SSE Event Formatı (Canlı Uçuş Telemetrisi ve Harita Animasyonu)
Drone uçarken saniyede bir merkeze konumunu gönderir. Frontend ekibi bu veriyi dinleyip Google Maps veya Leaflet üzerindeki Drone Marker'ını (ikonunu) animasyonlu (smooth) bir şekilde kaydırabilir.

**Örnek `event.data` (drone_telemetry):**
```json
{
  "event_type": "drone_telemetry",
  "drone_id": "IHA-001",
  "data": {
    "status": "flying",
    "battery_level": 88,
    "altitude": 120.5,
    "speed": 15.2,
    "latitude": 39.8455,
    "longitude": 33.5080
  }
}
```

**Frontend Kullanım Örneği (Google Maps Animasyonu):**
```javascript
// droneMarkers, haritadaki drone ikonlarını (Marker) tutan bir objedir.
eventSource.addEventListener("drone_telemetry", (event) => {
    const payload = JSON.parse(event.data);
    const { drone_id, data } = payload;
    const { latitude, longitude } = data;

    const newPosition = new google.maps.LatLng(latitude, longitude);

    if (droneMarkers[drone_id]) {
        // Drone haritada zaten var, yavaşça yeni konuma kaydır (Smooth transition)
        // (Gerçek projede requestAnimationFrame veya animasyon kütüphaneleri kullanılabilir)
        droneMarkers[drone_id].setPosition(newPosition);
    } else {
        // Drone ilk kez görüldü, haritaya yeni ikon ekle
        droneMarkers[drone_id] = new google.maps.Marker({
            position: newPosition,
            map: map,
            icon: '/icons/drone.png',
            title: drone_id
        });
    }
});
```

---

## 2.9 Deprem Olayı (Earthquake Webhook) Entegrasyonu

AFAD veya Kandilli Rasathanesi gibi dış kaynaklardan deprem bildirimlerini (Webhook) içeri almak için kullanılır. Veri eklendiği an `earthquake_alert` event_type ile `/stream` (SSE) üzerinden frontend'e fırlatılır (Harita sarsıntısı animasyonu vb. için kullanılabilir).

*   **Endpoint (Ekleme):** `POST /system/earthquakes`
*   **Request Payload (JSON):**
    ```json
    {
      "magnitude": 7.4,
      "depth_km": 15.2,
      "location_name": "Pazarcık, Kahramanmaraş",
      "latitude": 37.4912,
      "longitude": 37.2845
    }
    ```
*   **Response (201 Created):**
    ```json
    {
      "id": "uuid",
      "message": "Earthquake successfully recorded and broadcasted."
    }
    ```

*   **Endpoint (Listeleme):** `GET /system/earthquakes`
*   **Response:** Son 100 depremi en yeniden eskiye sıralı olarak dizi (Array) şeklinde döner.

---

## 2.10 Kitle Kaynaklı Deprem Logları (Crowdsourced Logs)

Mobil cihazlardan (ivmeölçer aracılığıyla) gelen ancak henüz 30 sayısına ulaşmadığı için resmi bir "Deprem" (Earthquake) olarak onaylanmamış olan ham sarsıntı loglarını listelemek için kullanılır.

*   **Endpoint:** `GET /admin/earthquake_logs`
*   **Response:** Son 500 ham logu döner.
    ```json
    [
      {
        "id": "uuid",
        "device_id": "victims-phone-001",
        "latitude": 38.0123,
        "longitude": 35.0456,
        "reported_at": "2023-11-20T10:15:00Z"
      }
    ]
    ```

---

## 2.11 Tüm Veritabanı SQL Yedeği (Admin Export)

Sistemdeki tüm PostgreSQL veritabanını SQL dump (`pg_dump`) olarak dışarı verir. Kriz merkezi operatörleri veya sistem yöneticileri bu endpoint üzerinden anlık tam yedek alabilir.

*   **Endpoint:** `GET /admin/export`
*   **Response (200 OK):**
    *   `Content-Type: application/sql; charset=utf-8`
    *   `Content-Disposition: attachment; filename="ads-backup-YYYYMMDD-HHMMSS.sql"`
    *   Body: Tüm veritabanının SQL yedeği (`.sql`)

**Önemli Not:** Bu endpoint sunucuda `pg_dump` komutunun kurulu olmasını gerektirir.

---

# 3. Sistem Sağlığı (Ping / Health)

Uygulamanın (Frontend veya Mobil) açılışında ya da Load Balancer seviyesinde backend'in çalışıp çalışmadığını kontrol etmek için kullanılır. `api/v1` takısı içermez. Sunucu ayakta ise `200 OK` döner.

*   **Endpoint:** `GET /health`
*   **Response (200 OK):** `ADS Backend is running`
