# 📱 ADS Mobil Uygulaması

Bu modül, sistemin afetzede tarafındaki sensör ağını oluşturan Android terminal yazılımıdır. Ana projenin bir parçasıdır.

## 🏗️ Mimari & Geliştirici Notları

*   **Dil ve Araçlar:** Uygulama tamamen Kotlin ile ve MVVM (Kısmi olarak Repository pattern) mimarisiyle Android Studio üzerinde geliştirilmiştir.
*   **Arka Plan Servisleri (Foreground Services):** Cihazın ivmeölçerinden STA/LTA (Kısa Vade Ortalama / Uzun Vade Ortalama) algoritmasıyla sürekli ve kilit ekranda dahi sarsıntı verisi okunur. Servislerin Android 14+ uyumluluğu gözetilmiştir.
*   **Edge AI (Çevrimdışı Yapay Zeka):** Uygulama içerisinde cihazda (on-device) çalışan bir Gemma LLM modeli ve Vosk ses tanıma kütüphanesi yer alır. İnternet yoksa bile afetzedeyle iletişim kurulabilir.
*   **P2P Mesh Network:** İnternetin koptuğu durumlarda afetzede cihazları kendi aralarında Google Nearby Connections API üzerinden haberleşir.
*   **Ortam Değişkenleri:** Bu proje kendi içinde `.env` barındırmaz. Build sırasında `build.gradle.kts` üzerinden ana dizindeki (root) `.env` dosyası okunarak BuildConfig sınıfları üretilir.

## 🛠️ Yerel Geliştirme (Local Development)

1.  Projeyi doğrudan **Android Studio** ile `ads-mobile/` klasöründen açın.
2.  Ana dizinde `.env` dosyasının olduğundan emin olduktan sonra Gradle Sync yapın.
3.  Uygulamayı **fiziksel bir cihazda** (sensörlerin doğru çalışabilmesi için) test edin.

Sistemin bütüncül çalışma mantığı, sunucu kurulumu ve gereksinimleri için lütfen projenin **[Ana Dizindeki README.md](../README.md)** dosyasına göz atın.
