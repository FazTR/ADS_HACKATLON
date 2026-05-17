# ADS (Acil Durum Sistemi)

**ADS (Acil Durum Sistemi)**, olası afet anlarında (özellikle depremler) iletişim kopukluğunu en aza indiren, enkaz altında kalan veya acil yardıma ihtiyacı olan bireyleri tespit edip AFAD ve kriz yönetim merkezlerine asenkron olarak ulaştıran kapsamlı bir çözüm ekosistemidir.

Sistem, birbiriyle bütünleşik üç ana bileşenden oluşmaktadır:

1.  **Backend (Rust & Axum):** Yüksek hacimli eşzamanlı bağlantıları yöneten, telemetri verilerini işleyen ve kriz merkezi ile sahadaki cihazlar arasında köprü kuran yüksek performanslı sunucu uygulaması.
2.  **Mobil Uygulama (Android Kotlin):** Afetzedelerin telefonlarındaki sensörleri kullanarak otomatik sarsıntı algılayan, internetin koptuğu anlarda çevrimdışı yapay zeka (LLM & Ses tanıma) ile destek veren ve P2P mesh ağı üzerinden diğer cihazlarla haberleşen terminal uygulaması.
3.  **Admin Panel (Next.js & React):** Kriz masasının canlı olarak ihbarları görüntülediği, drone filolarını harita üzerinden yönettiği ve gelişmiş yapay zeka analizleri yapabildiği Türkçe arayüzlü web dashboard.

## 🗂️ Proje Yapısı ve Mimari Belgeler

Projelerin spesifik teknik detayları, kod yapıları ve sadece o modülü ilgilendiren geliştirici notları için alt klasörlerdeki README dosyalarına göz atabilirsiniz:
*   [🛠️ Backend Dokümantasyonu (Rust)](./ads-backend/README.md)
*   [📱 Mobil Dokümantasyonu (Android)](./ads-mobile/README.md)
*   [🌐 Admin Panel Dokümantasyonu (Next.js)](./ads-admin-panel/README.md)

---

## 🚀 Hızlı Başlangıç & Kurulum

Sistemin kurulumu, yönetimi ve ayağa kaldırılması tamamen **ana dizinden (root)** yapılmaktadır. Herhangi bir klasörün içinde ortam değişkeni (.env) tutulmasına gerek yoktur.

### 1. Ortam Değişkenlerinin (Global .env) Hazırlanması

Tüm projelerde ortak kullanılacak API anahtarları, veritabanı ayarları ve LLM (Yapay Zeka) servislerine ait değişkenler güvenlik amacıyla **ana dizindeki tek bir `.env` dosyasında** toplanmıştır.

Projeyi indirdikten sonra kök dizinde bulunan `.env.example` dosyasını kopyalayarak `.env` dosyanızı oluşturun:

```bash
cp .env.example .env
```

Ardından `.env` dosyası içerisindeki `LLM_API_KEY` ve `AUTH_SECRET` gibi alanları kendi bilgilerinizle güncelleyin.

### 2. Docker Servislerini Başlatma

Ana dizinde bulunan `docker-compose.yml` dosyası, sistemin omurgasını oluşturan veritabanlarını (PostgreSQL/PostGIS, TimescaleDB), mesajlaşma aracısını (EMQX) ve derlenmiş Rust Backend'ini barındırır.

```bash
# Servisleri arka planda başlat
docker-compose up -d --build
```
*Not: Veritabanı tabloları `init.sql` dosyası okunarak otomatik oluşturulacaktır.*

### 3. Kriz Merkezi Arayüzünü (Admin Panel) Ayağa Kaldırma

Docker üzerindeki API'ye bağlanacak arayüzü başlatmak için:
```bash
cd ads-admin-panel
npm install
npm run dev
```
Panele **`http://localhost:3000`** adresinden erişebilirsiniz. Test giriş bilgileri: `admin123` / `admin12345`.

### 4. Afetzede Cihazlarını (Mobil) Test Etme

*   `ads-mobile` klasörünü Android Studio ile açın.
*   Gradle senkronizasyonu sırasında projeniz ana dizindeki `.env` dosyasını otomatik olarak okuyacaktır.
*   Fiziksel test cihazına derleyerek sensör/sarsıntı okumalarını ve P2P mesh network iletişimini test edebilirsiniz.
