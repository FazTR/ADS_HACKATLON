# ADS (Acil Durum Sistemi) - Mobile API Documentation

**Versiyon:** 1.0.0
**Hedef Kitle:** iOS (Swift) ve Android (Kotlin) Geliştiricileri.
**Base URL:** `http://<server-ip>:4030/api/v1`

**Authentication (Güvenlik):**
Sisteme (Health Check hariç) atılacak tüm isteklerde HTTP Header içerisinde aşağıdaki API Key gönderilmelidir. Gönderilmemesi durumunda `401 Unauthorized` hatası alınır.
*   **Header:** `x-api-key`
*   **Değer:** `ADS_HACKATHON_2026`

Bu doküman, afetzedelerin ve sahadaki arama kurtarma görevlilerinin kullandığı çevrimdışı öncelikli (offline-first) mobil uygulamanın, internet veya mesh ağı bulduğunda backend ile konuşacağı REST API uç noktalarını açıklar.

---

## 1. Kullanıcı Kaydı (Registration)

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

## 1.5 Profil Bilgilerini Getirme (Mevcut Kullanıcı)

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

## 2. Durum Bildirme (Afetzede Endpoint'i)

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

## 2.5 Kitle Kaynaklı Deprem Bildirimi (Crowdsourced Earthquake Detection)

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

### 3. Toplu Kullanıcı Senkronizasyonu (Batch Sync Registers)
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

### 4. Toplu Durum Senkronizasyonu (Batch Sync Reports)
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

## 5. Yakındakileri Bulma (Kurtarma Ekibi / Radar)

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

## 3. Sistem Sağlığı (Ping)
Uygulama açılışında sunucunun aktif olup olmadığını kontrol etmek için kullanılır. `api/v1` takısı içermez.

*   **Endpoint:** `GET /health`
*   **Response (200 OK):** `ADS Backend is running`
