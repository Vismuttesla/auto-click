# PrecisionTrigger foydalanish qo'llanmasi

## 1. Dasturni ishga tushirish

1. `PrecisionTrigger-windows-x64.zip` faylini alohida papkaga to'liq oching.
2. Ochilgan `PrecisionTrigger` papkasidagi `PrecisionTrigger.exe` faylini ishga tushiring.
3. `app` va `runtime` papkalarini o'chirmang yoki ko'chirmang. Ular `.exe` bilan birga turishi kerak.

Java o'rnatish talab qilinmaydi: paket o'z Java 21 runtime muhitini saqlaydi. Windows SmartScreen ogohlantirsa, faqat fayl ishonchli builddan olinganiga amin bo'lsangiz `More info` va `Run anyway` ni tanlang.

## 2. Tarmoq talabi

Dastur server vaqtini `http://172.16.14.19:5001/api/Cabinet/CheckTimeV2` ichki manzilidan oladi. Kompyuter tegishli lokal tarmoqqa yoki VPN'ga ulangan bo'lishi kerak. Ulanish bo'lmasa, `Sync Status` maydonida xato ko'rinadi va target'ni qurollantirish bloklanadi.

## 3. Dastlabki sozlash

1. Endpoint autentifikatsiya talab qilsa, `Bearer token` maydoniga faqat token qiymatini kiriting va `Apply Token` ni bosing.
2. `Test Authentication` orqali token va endpoint ishlashini tekshiring.
3. `Sync Now` ni bosing.
4. `Readiness` qiymati `READY` va `Confidence` kamida `0.700` bo'lishini kuting.

Token faqat operativ xotirada saqlanadi. Dastur yopilganda token yo'qoladi. `Clear Token` joriy tokenni darhol o'chiradi.

## 4. Sinxronlash intervali

`Interval` maydoniga musbat son kiriting, `SECONDS` yoki `MINUTES` ni tanlang va `Apply Interval` ni bosing.

- Minimal interval: 5 soniya
- Standart interval: 60 soniya
- Maksimal interval: 60 daqiqa

Oddiy foydalanishda 60 soniya tavsiya etiladi.

## 5. Target vaqtini belgilash

1. Kalendardan sanani tanlang.
2. Vaqtni lokal tizim vaqt zonasida `HH:mm:ss.SSS` formatida kiriting. Masalan: `14:30:00.250`.
3. `Readiness` holati `READY` ekanini tekshiring.
4. `ARM TARGET` ni bosing.
5. `App State` maydonida `ARMED`, bajarilish yaqinida `FINALIZING` ko'rinadi.

Target'ni bekor qilish uchun `CANCEL` ni bosing. Windows sana, vaqt va timezone sozlamalarini oldindan tekshiring.

## 6. Natijalar va audit

- `Timing History` server sinxronlash namunalari va kechikishlarni ko'rsatadi.
- `Execution Audit` target hodisalari va bajarilish natijasini ko'rsatadi.
- To'liq JSONL jurnallar dastur ishga tushirilgan katalogdagi `logs/` papkasiga yoziladi.
- Runtime sozlamalari `config/runtime-settings.json` faylida saqlanadi; bearer token faylga yozilmaydi.

## 7. Muhim cheklov

Joriy versiya `DryRunActionExecutor` dan foydalanadi. Belgilangan vaqtda execution oqimi va audit ishlaydi, lekin tashqi biznes API'ga real buyruq yuborilmaydi. `SENT` natijasi hozircha dry-run muvaffaqiyatini bildiradi.

Dastur umumiy Windows va JVM muhitida ishlaydi; hard real-time aniqlik kafolatlanmaydi. Serverdagi `/data` vaqtining aynan qaysi server hodisasida o'lchanishi hali tasdiqlanmagan.

## 8. Muammoni aniqlash

- `NO_CLOCK`: `Sync Now` ni bosing va tarmoq/VPN'ni tekshiring.
- `LOW_CONFIDENCE`: bir nechta sync yakunlanishini kuting va tarmoqni tekshiring.
- `STALE_CLOCK`: yangi sync bajaring.
- `UNAUTHORIZED`: tokenni yangilang va `Test Authentication` ni qayta bosing.
- Dastur ochilmasa: ZIP to'liq ochilganini va `runtime` papkasi mavjudligini tekshiring.
