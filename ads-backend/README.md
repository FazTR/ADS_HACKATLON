# ⚙️ ADS Backend

Bu modül, AFAD Kriz Merkezi Dashboard'u ve Sahadaki Cihazlar arasındaki haberleşmeyi sağlayan asenkron, yüksek performanslı sunucu yazılımıdır. Ana projenin bir parçasıdır.

## 🏗️ Mimari & Geliştirici Notları

*   **Dil ve Çatı:** Sunucu **Rust** dilinde yazılmış olup, web altyapısı olarak **Axum** ve asenkron işlemler için **Tokio** kullanılmıştır.
*   **Veritabanı (PostgreSQL & TimescaleDB):** Coğrafi veriler (PostGIS) için PostgreSQL, dronelardan gelen zaman damgalı yoğun telemetri verileri içinse TimescaleDB yapılandırılmıştır.
*   **İletişim (MQTT):** Mobil uygulamalar ve drone simülasyonları, sunucuyla MQTT (EMQX broker) üzerinden konuşur. Bu, TCP bağlantısının kopup geldiği durumlarda paket kaybını minimuma indirir.
*   **SSE (Server-Sent Events):** Admin paneldeki web arayüzünün anında güncellenmesi için Axum üzerinden asenkron SSE yayınları yapılır.
*   **Ortam Değişkenleri:** Bu proje tek başına bağımsız bir `.env` dosyası barındırmaz. `dotenvy` kütüphanesi yapılandırma için ağaç hiyerarşisinde yukarı çıkarak ana dizindeki (root) `.env` dosyasını otomatik olarak okur.

## 🛠️ Yerel Geliştirme (Local Development)

Sadece Rust API sunucusunu geliştiriyorsanız (Docker konteynerlarının ana dizin üzerinden ayaklandırıldığını varsayıyoruz):

```bash
# Bağımlılıkları kontrol et ve derle
cargo build

# Sunucuyu başlat
cargo run
```

### Simülasyon
Drone'ları ve rastgele deprem ihbarlarını üretmek isterseniz aynı klasörde ayrı bir uçbirim (terminal) açarak simülasyonu tetikleyebilirsiniz:
```bash
cargo run --bin simulate
```

API endpoint detayları ve payload formatları için bu klasörde yer alan **`API.md`** dosyasına başvurabilirsiniz.

Sistemin bütüncül çalışma mantığı ve Docker (docker-compose) kurulumları için lütfen projenin **[Ana Dizindeki README.md](../README.md)** dosyasına göz atın.
