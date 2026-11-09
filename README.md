# HB Harcama Takip (Play Store Uyumlu Çoklu Banka & Bölüm Bazlı Harcama İzleyici)

Türkiye'deki 13 banka ve finans kuruluşunun anlık uygulama bildirimlerini (Push) ve Mesajlar (SMS / RCS) bildirimlerini yakalayıp **%100 çevrimdışı** olarak telefonun yerel SQLite (`Room`) veritabanında işleyen, kartları **Bölümlere (Klasörlere: Kişisel, Ev, İş / Şirket vb.)**, **Bankalara** ve **Kategorilere** göre analiz eden Android uygulaması.

## Öne Çıkan Özellikler

- **Sıfır Tehlikeli İzin (`INTERNET` ve `RECEIVE_SMS` Yok):** `AndroidManifest.xml` üzerinde internet ve ham SMS okuma izni bulunmaz. Hiçbir finansal veri cihaz dışına çıkamaz.
- **13 Banka / Kuruluş Desteği:** Yapı Kredi, VakıfBank, TOM Bank, Garanti BBVA, İş Bankası, Akbank, Ziraat Bankası, Enpara, QNB, Kuveyt Türk, TEB, DenizBank ve Papara.
- **Harcama Bölümleri (Kart Klasörleri):** Kartlarını `Kişisel`, `Ev`, `İş / Şirket` veya istediğin özel bölümlere ayır; hem `Tümü (Ortak)` toplamını gör hem de seçtiğin bölüm için ayrı analiz, CSV ve A4 PDF raporu al. Tek bir harcamayı kartı değiştirmeden başka bölüme de taşıyabilirsin.
- **Kullanıcı Kontrollü Kart Eşleştirme (`UnknownCardPolicy`):** Kartlarının son 4 hanesini tanımla; banka bazında yeni kartları `Otomatik Ekle`, `Bekleyenlere Düşür` veya `Kayıtlı Olmayanı Yoksay` politikalarından birini seç.
- **Parmak İzi Tabanlı Mükerrer Koruması & OTP Sıfır Kayıt:** `processed_messages` parmak izi tablosu ve paket bazlı kanal birleştirme ile çift kayıtlar önlenir; tek kullanımlık şifre (OTP) içerikleri veritabanına yazılmaz.
- **Çoklu Dışa / İçe Aktarım:** Türkçe Excel uyumlu `CSV` (formül enjeksiyon korumalı), çok sayfalı A4 `PDF` dönem raporu ve `SAF` üzerinden `JSON` tam yedekleme / geri yükleme.

## Derleme ve Test

```bash
./gradlew testDebugUnitTest
./gradlew assembleRelease bundleRelease
```

Üretilen çıktılar:
- İmzalı Release APK: `app/build/outputs/apk/release/app-release.apk`
- Google Play App Bundle (AAB): `app/build/outputs/bundle/release/app-release.aab`
- Gizlilik Politikası (Play Console için): `docs/privacy-policy.html`
