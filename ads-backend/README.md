# ADS (Acil Durum Sistemi) Backend

Bu proje, afetzedelerin mobil cihazları, drone'lar (İHA) ve AFAD Kriz Merkezi Dashboard'u arasında köprü kuran asenkron, yüksek performanslı **Rust tabanlı (Axum)** sunucu yazılımıdır. 

## 🚀 Kurulum ve Çalıştırma

Projeyi ayağa kaldırmak için bilgisayarınızda **Docker**, **Docker Compose** ve **Rust (Cargo)** kurulu olmalıdır.

### 1. Çevresel Değişkenlerin (.env) Ayarlanması
Projeyi çalıştırmadan önce `ads-backend` dizininde bulunan `.env.example` dosyasının adını `.env` olarak değiştirin veya kopyalayın. İçerisindeki API anahtarlarını (özellikle güvenli bir `API_KEY` ve `LLM_API_KEY`) belirleyin.

```bash
cp .env.example .env
```

### 2. Veritabanı ve Mesaj Aracısını Başlatma

Sistem PostGIS eklentili bir **PostgreSQL**, telemetri için **TimescaleDB** ve dronelar ile haberleşmek için **EMQX (MQTT Broker)** kullanır.

Backend klasörü (ads-backend) içerisinde terminal açın ve gerekli servisleri Docker üzerinden ayağa kaldırın:

```bash
docker-compose up -d
```

*Not: Veritabanı tabloları `init.sql` üzerinden otomatik olarak oluşturulacaktır.*

### 3. Backend Sunucusunu Başlatma

Rust bağımlılıklarını indirip sunucuyu çalıştırmak için:

```bash
cargo run
```

Sunucu başarıyla başladığında `http://127.0.0.1:4030` adresinden hizmet vermeye başlayacaktır.

### 4. API Dokümantasyonu

Backend uç noktaları, request/response payloadları ve SSE (Server-Sent Events) bağlantıları hakkında detaylı bilgi için projedeki **[API.md](./API.md)** dosyasını inceleyebilirsiniz.

### 5. Simülasyonu Çalıştırma (Opsiyonel)

Hackathon sunumu sırasında haritada canlı hareket eden droneları, deprem bildirimlerini ve afetzede verilerini simüle etmek için hazır bir script bulunmaktadır. Sunucu ayaktayken yeni bir terminal sekmesinde şunu çalıştırabilirsiniz:

```bash
cargo run --bin simulate
```

---

## 🛠️ Geliştirme Standartları

- **Gerçekçilik:** Üretilen kodlar %100 gerçekçi, production'a hazır ve hatasız olmalıdır. "Placeholder" veya "Burada iş mantığı olacak" gibi sahte kod bloklarından kaçınılacaktır.
- **Yorum Satırları:** "Az ve öz" kuralı geçerlidir. Kodun zaten açıkça anlattığı kısımlara yorum yazılmayacak; yalnızca mimari olarak karmaşık kısımlar (algoritmalar, gRPC/Axum entegrasyonu vb.) açıklanacaktır.
- **Modülerlik:** Sistem, mobil (REST/WebSocket), drone (MQTT/gRPC) ve web (SSE) istemcilerine aynı anda asenkron olarak hizmet verebilecek modülerlikte tasarlanacaktır.
