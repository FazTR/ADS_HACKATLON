# 🌐 ADS Admin Panel

Bu modül, AFAD Kriz Merkezi Dashboard'u olarak tasarlanmış web uygulamasıdır. Ana projenin bir parçasıdır.

## 🏗️ Mimari & Geliştirici Notları

*   **Klasör Yapısı:** Uygulama Next.js App Router (`src/app`) kullanılarak geliştirilmiştir.
*   **Yetkilendirme:** Next-Auth v5 ile JWT tabanlı kimlik doğrulama yapılmaktadır.
*   **Haritalar:** Leaflet.js `window` nesnesine ihtiyaç duyduğu için dinamik olarak ("use client" ve `dynamic import`) yüklenmektedir.
*   **Gerçek Zamanlı Veri:** Backend'den SSE (Server-Sent Events) veya periyodik çekim (polling) yöntemleri ile asenkron olarak veriler haritaya yansıtılır.
*   **Ortam Değişkenleri:** Bu proje tek başına bağımsız bir `.env` dosyası barındırmaz. Ortam değişkenlerini ana dizindeki (root) `.env` dosyasından okur.

## 🛠️ Yerel Geliştirme (Local Development)

Projeyi sadece UI/UX geliştirmesi için ayağa kaldırmak isterseniz (kök dizindeki `.env` dosyasını oluşturduğunuzdan emin olun):

```bash
# Bağımlılıkları yükleyin
npm install

# Geliştirme sunucusunu başlatın
npm run dev
```

Ana kurulum talimatları, Docker gereksinimleri ve tam sistem yapılandırması için lütfen projenin **[Ana Dizindeki README.md](../README.md)** dosyasına göz atın.
