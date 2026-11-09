# HB Harcama Takip (Çoklu Banka Otomatik Harcama İzleyici)

Yapı Kredi, VakıfBank (6251 kart filtresiyle) ve TOM Bank kartlarının anlık uygulama bildirimlerini (Push) ve SMS / RCS mesajlarını yakalayıp telefon üzerinde yerel SQLite (`Room`) veritabanına kaydeden kişisel Android uygulaması.

## Desteklenen Bankalar ve Kurallar

- **Yapı Kredi:** Yapı Kredi Mobil (`com.ykb.android`), World Mobil (`com.ykb.avm`) ve `YAPIKREDI` SMS/RCS -> Tüm kartlar (Worldcard, ek kart, Dijital/Sanal Kart, TLcard) kaydedilir.
- **VakıfBank:** VakıfBank Mobil (`com.vakifbank.mobile`) ve `VAKIFBANK` SMS/RCS -> Yalnızca sonu **`6251`** ile biten kartın işlemleri kaydedilir; diğer VakıfBank kart/hesap bildirimleri elenir.
- **TOM Bank:** TOM Bank Hadi (`com.tom.hadi`) ve `TOMBank` / `HADI` SMS/RCS -> Tüm kartlar kaydedilir.

## Mimari ve Gizlilik

- **Sıfır Ağ Erişimi:** `AndroidManifest.xml` içerisinde `android.permission.INTERNET` izni tanımlı değildir. İşletim sistemi seviyesinde hiçbir kart veya harcama verisi telefondan dışarı çıkamaz.
- **Kart ve Kart Türü Gösterimi:** Harcama listesinde her işlem için hem banka/kart etiketi hem de kart türü (`Kredi Kartı`, `Dijital / Sanal Kart`, `Banka Kartı`, `Nakit`) rozeti gösterilir.
- **Mükerrer Kayıt Engelleme:** Aynı harcama için hem Push hem SMS geldiğinde 3 dakikalık pencerede `(banka + kart_son4 + tutar + para_birimi)` eşleşmesiyle çift kayıt önlenir.
- **OTP / Şifre Maskeleme:** 3D Secure, Akıllı Şifre ve doğrulama kodu mesajları harcama olarak kaydedilmez; ham günlükte tek kullanımlık kodlar otomatik maskelenir.

## Derleme

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Üretilen APK konumu:
`app/build/outputs/apk/debug/app-debug.apk`
