# CroakVPN Android 🐸

Android-порт клиента CroakVPN для Windows. Полный порт логики на Kotlin + Jetpack Compose.

## Архитектура

```
app/src/main/java/com/croakvpn/
├── CroakApp.kt                   # Application class, извлечение sing-box из assets
├── model/
│   └── Models.kt                 # ConnectionStatus, ServerConfig, TrafficStats (≈ Models.cs)
├── data/
│   ├── VLESSParser.kt            # Парсер vless:// URI (≈ VLESSParser.cs)
│   ├── ConfigGenerator.kt        # Генератор sing-box JSON конфига (≈ ConfigGenerator.cs)
│   ├── SubscriptionRepo.kt       # Загрузка и парсинг подписок Marzban (≈ SubscriptionRepo.cs)
│   └── PrefsManager.kt           # Хранение настроек через DataStore (≈ PrefsManager.cs)
├── service/
│   ├── CroakVpnService.kt        # Android VpnService + запуск sing-box (≈ SingBoxService.cs)
│   ├── BootReceiver.kt           # Автозапуск при старте устройства
│   └── UpdateService.kt          # Проверка обновлений GitHub (≈ UpdateService.cs)
└── ui/
    ├── MainActivity.kt           # Activity, VPN permission flow
    ├── MainViewModel.kt          # ViewModel, бизнес-логика (≈ MainViewModel.cs)
    ├── CroakVpnApp.kt            # Весь Compose UI: главный экран + настройки + диалоги
    └── theme/
        └── Theme.kt              # Material3 тёмная тема
```

## Соответствие Windows ↔ Android

| Windows (WPF/C#)         | Android (Kotlin)              |
|--------------------------|-------------------------------|
| `SingBoxService.cs`      | `CroakVpnService.kt`          |
| `MainViewModel.cs`       | `MainViewModel.kt`            |
| `PrefsManager.cs`        | `PrefsManager.kt` (DataStore) |
| `SubscriptionRepo.cs`    | `SubscriptionRepo.kt`         |
| `VLESSParser.cs`         | `VLESSParser.kt`              |
| `ConfigGenerator.cs`     | `ConfigGenerator.kt`          |
| `UpdateService.cs`       | `UpdateService.kt`            |
| `App.xaml.cs` (tray)     | `CroakVpnService` (foreground notification) |
| Scheduled Task autostart | `BootReceiver.kt`             |
| Windows TUN adapter      | Android `VpnService` + sing-box TUN |

## Подготовка sing-box

Android-версия, как и Windows, использует **sing-box** как ядро VPN.

### Вариант 1 — Поместить в assets (рекомендуется для сборки)

1. Скачай бинарник sing-box для Android с https://github.com/SagerNet/sing-box/releases
   - Нужен файл вида: `sing-box-*-android-arm64.tar.gz`
2. Распакуй и переименуй:
   - `sing-box` → `app/src/main/assets/libs/sing-box-arm64`  (для большинства телефонов)
   - `sing-box` → `app/src/main/assets/libs/sing-box-x86_64` (для эмулятора x86_64)
3. `CroakApp.kt` автоматически извлечёт бинарник в `filesDir/libs/sing-box` при первом запуске

```bash
mkdir -p app/src/main/assets/libs
# ARM64 (Pixel, Samsung, OnePlus и т.д.)
tar xzf sing-box-*-android-arm64.tar.gz
cp sing-box-*/sing-box app/src/main/assets/libs/sing-box-arm64
chmod +x app/src/main/assets/libs/sing-box-arm64
```

### Вариант 2 — Вручную залить на устройство (для разработки)

```bash
adb shell mkdir -p /data/data/com.croakvpn/files/libs
adb push ./sing-box /data/data/com.croakvpn/files/libs/sing-box
adb shell chmod +x /data/data/com.croakvpn/files/libs/sing-box
```

### Вариант 3 — libcore/SagerNet подход (продакшн)

Для готового продакшн-приложения рассмотри интеграцию через [libcore](https://github.com/SagerNet/sing-box/tree/main/experimental/libbox) — Go-библиотека sing-box, скомпилированная как `.aar` для Android.

## Сборка

```bash
# Клонируй и открой в Android Studio
git clone <repo>
cd CroakVPN-Android

# Или через командную строку
./gradlew assembleDebug
./gradlew installDebug
```

Требования:
- Android Studio Hedgehog или новее
- JDK 17+
- Android SDK 35 (targetSdk)
- minSdk 26 (Android 8.0 Oreo)

## Deep Links

Приложение обрабатывает `croakvpn://` deep links для импорта подписок:
```
croakvpn://https://your-marzban-server.com/sub/your-token
```

## VPN Permission Flow

Android требует явного согласия пользователя на создание VPN-туннеля. Схема:

```
Нажать "Подключить"
  → VpnService.prepare() проверяет разрешение
  → Если нет — система показывает диалог "Разрешить CroakVPN?"
  → Пользователь разрешает → onVpnPermissionGranted() → запуск сервиса
  → CroakVpnService.startVpn() → ProcessBuilder sing-box → Clash API polling
  → Status = Connected → UI обновляется
```

## Уведомления

Сервис работает в foreground с persistent-уведомлением (обязательно для Android VPN).
Уведомление содержит кнопку "Отключить" — работает даже при свёрнутом приложении.

## Трафик

Скорость загрузки/выгрузки получается через WebSocket `ws://127.0.0.1:9090/traffic` —
тот же Clash API, что и в Windows-версии.

## Особенности Android vs Windows

| Аспект | Windows | Android |
|--------|---------|---------|
| TUN адаптер | WinTUN через sing-box | Android VpnService builder |
| Автозапуск | Scheduled Task (schtasks) | BroadcastReceiver BOOT_COMPLETED |
| Системный трей | NotifyIcon (WinForms) | Foreground notification |
| Хранение настроек | Файл prefs.txt | DataStore Preferences |
| Права администратора | UAC elevation | VpnService permission dialog |
| Cleanup TUN при старте | netsh/pnputil | Не требуется (Android управляет) |
