# AppJail

Seçtiğin uygulamaların günlük süresini takip eder; limit dolduğunda o uygulama **gün sonuna kadar** açılmaz.

## Kurulum
1. Android Studio → Open → `AppJail`
2. `settings.gradle.kts` yoksa yeni bir "Empty Activity" projesi açıp `app/` içeriğini bu dosyalarla değiştir.
3. Çalıştır, sonra uygulamada:
   - **Kullanım izni** → AppJail'i aç
   - **Erişilebilirlik** → AppJail'i aç

## Nasıl çalışıyor
- `UsageTracker`: `UsageStatsManager` olaylarından gece yarısından beri geçen süreyi hesaplar.
- `BlockerService`: erişilebilirlik servisi; ön plandaki uygulamayı hem olay bazlı hem 3 sn'de bir kontrol eder.
- `BlockActivity`: limit dolunca açılan, geri tuşuyla kapanmayan, çıkış butonu olmayan bariyer.
- `LimitStore`: limitler + "bugün kilitlendi" listesi. Gün değişince kilitler sıfırlanır.

## Kaçamak kapatan kurallar
- Limit **kısaltma** anında, **uzatma** ertesi gün geçerli.
- Takipten çıkarma da ertesi gün geçerli; bugünkü kilit kalkmaz.
- Kilitlenen paket tekrar açılırsa süre bile sorgulanmadan doğrudan bariyer gelir.

## Daha da sağlamlaştırmak istersen
- `DeviceAdminReceiver` ekle → AppJail'in kaldırılması kilitlenir.
- Ayarlar uygulamasının "Erişilebilirlik" ekranını da izleyip servis kapatılmaya çalışılırsa geri at.
- Kilit durumunu yerelde değil sunucuda tut → veri temizleme ile sıfırlanmaz.
