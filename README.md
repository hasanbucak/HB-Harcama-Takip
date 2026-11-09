# HB Harcama Takip (Yapı Kredi Otomatik Harcama İzleyici)

Yapı Kredi Bankası'ndaki tüm kartların (Worldcard kredi kartları, ek kartlar, Dijital/Sanal kartlar ve TLcard banka kartları) anlık harcama bildirimlerini ve SMS mesajlarını yakalayıp telefon üzerinde yerel SQLite (`Room`) veritabanına kaydeden kişisel Android uygulaması.

## Mimari ve Gizlilik

- **Sıfır Ağ Erişimi:** `AndroidManifest.xml` içerisinde `android.permission.INTERNET` izni tanımlı değildir. İşletim sistemi seviyesinde hiçbir kart veya harcama verisi telefondan dışarı çıkamaz.
- **Çift Kanallı Dinleme:**
  - `YkbNotificationListenerService`: Yalnızca **Yapı Kredi Mobil (`com.ykb.android`)** ve **World Mobil (`com.ykb.avm`)** paketlerinden gelen bildirimleri okur.
  - `YkbSmsReceiver`: İnternet bağlantısı olmadığında düşen **`YAPIKREDI`** SMS mesajlarını yakalar.
- **Mükerrer Kayıt Engelleme:** Aynı harcama için hem Push hem SMS geldiğinde 3 dakikalık pencerede `(kart_son4 + tutar + para_birimi)` eşleşmesiyle çift kayıt önlenir.
- **OTP / Şifre Maskeleme:** 3D Secure, Akıllı Şifre ve giriş şifresi mesajları harcama olarak kaydedilmez; ham günlükte tek kullanımlık kodlar otomatik maskelenir.
- **Otomatik Kart Ayırma & Kategori Hafızası:** Bildirimdeki son 4 haneden kartı otomatik tanımlar; bir işyerinin kategorisi değiştirildiğinde sonraki harcamalarda aynı kategoriyi otomatik uygular.

## Derleme

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Üretilen APK konumu:
`app/build/outputs/apk/debug/app-debug.apk`

## Telefonda İlk Kurulum

1. APK'yı kurduktan sonra uygulamayı açın (`Kurulum & Log` sekmesi otomatik açılır).
2. **Bildirim Erişimi**, **SMS Okuma İzni** ve **Pil Kısıtlaması Muafiyeti** adımlarını aktif edin.
   - Android 13/14/15'te APK dosya yöneticisinden elle kurulduysa izin anahtarı kilitli görünebilir: `Ayarlar -> Uygulamalar -> HB Harcama Takip -> Sağ üst (⋮) -> Kısıtlı ayarlara izin ver` seçeneğini açın (`adb install` ile kurulumda gerekmez).
3. Yapı Kredi Mobil / World Mobil içinde `Profilim / Ayarlarım -> Bildirim Ayarları` menüsünden kart harcama bildirimlerinin açık olduğundan emin olun.
