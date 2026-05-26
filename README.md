# ⚔️ BladeX — Fear Craft Özel Kılıç Sistemi

<p align="center">
  <img src="https://img.shields.io/badge/Paper-1.21.x-blue?style=for-the-badge&logo=minecraft" />
  <img src="https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk" />
  <img src="https://img.shields.io/badge/Maven-3.8%2B-red?style=for-the-badge&logo=apache-maven" />
  <img src="https://img.shields.io/github/actions/workflow/status/KULLANICIN/BladeX/build.yml?style=for-the-badge&label=Build" />
</p>

> **BladeX**, Fear Craft sunucusu için özel olarak geliştirilmiş, tam config destekli bir Minecraft Paper eklentisidir.  
> 9 farklı kılıç yeteneği, 3 özel menü itemi ve iade sistemi içerir.

---

## 📋 Özellikler

### ⚔️ Kılıçlar (9 Adet)

| Kılıç | Yetenek | Bekleme |
|-------|---------|---------|
| 🔴 **Shulker Kılıcı** | Yakındaki düşmanlara Levitation uygular | 30s |
| 🟢 **Creeper Kılıcı** | Patlama dalgası + 6 hasar | 25s |
| 🕷️ **Örümcek Kılıcı** | Düşmanların üstüne örümcek ağı atar | 60s |
| 🔵 **Phantom Kılıcı** | Düşmanların Elytra'sını 3s bozar | 65s |
| ⚡ **Yıldırım Kılıcı** | Düşmanlara şimşek çarptırır | 30s |
| 🐉 **Ejderha Kılıcı** | Ateş + Wither II etkisi | 30s |
| 🔱 **Gardiyan Kılıcı** | Mining Fatigue III uygular | 60s |
| 💀 **Wither Kılıcı** | Wither II etkisi | 30s |
| 👁️ **Enderman Kılıcı** | Düşmanları 5 blok yukarı ışınlar | 30s |

### 🎛️ Alt Menü Özel Itemler (3 Adet — Simetrik)

| Item | Yetenek | Bekleme |
|------|---------|---------|
| 👁️ **Elytra Kilidi** | Kendi Elytra'nı 80 saniye kilitler | 80s |
| ⭐ **Güç Yıldızı** | 30s Hız II + Güç I | 60s |
| 🛡️ **Yenilmezlik Totemi** | 10s hasar almama | 90s |

### 📦 Diğer Özellikler
- **İade Sistemi** — Ölümleri kaydeder, admin istediği oyuncunun eşyasını iade edebilir
- **Tam Config Desteği** — Kılıç ismi, rengi, lore'u, bekleme süresi, etki alanı hepsi config.yml'den değiştirilebilir
- **Animasyonlu Başlık (Flop)** — Menü başlıkları ve action bar renk geçişli animasyonlu
- **Hex Renk Desteği** — `&#RRGGBB` formatı tam desteklenir

---

## 🚀 Kurulum

1. **JAR'ı indir** → Releases sekmesinden veya Actions artifact olarak
2. JAR'ı sunucunun `plugins/` klasörüne koy
3. Sunucuyu başlat — `config.yml` otomatik oluşturulur
4. `config.yml`'i düzenle, `/bladexreload` ile yükle

---

## 🔧 Komutlar & İzinler

| Komut | Açıklama | İzin |
|-------|----------|------|
| `/kilicvermenu` | Kılıç menüsünü açar | `bladex.admin` |
| `/iade` | İade menüsünü açar | `bladex.admin` |
| `/bladexreload` | Config'i yeniden yükler | `bladex.admin` |

> Tüm izinler varsayılan olarak **op** oyuncularına verilir.

---

## ⚙️ Config Özelleştirme

`plugins/BladeX/config.yml` dosyasından şunları değiştirebilirsiniz:

```yaml
# Kılıç adı, rengi
swords:
  shulker:
    display_name: "&#e8473e&lShulker Kılıcı"   # İstediğin isim ve renk
    cooldown: 30                                  # Bekleme süresi (saniye)
    radius: 6                                     # Etki alanı (blok)
    lore:                                         # Açıklama satırları
      - "..."

# Menü başlığı
menu:
  title: "&#3b3b3bKılıç Menü"

# Alt itemler (Elytra Kilidi, Güç Yıldızı, Yenilmezlik Totemi)
menu_bottom_items:
  left:
    display_name: "&#cc33ff&lElytra Kilidi"
    cooldown: 80
    ...
```

**Renk Formatları:**
- `&a` `&b` `&c` ... — Standart Minecraft renkleri
- `&#RRGGBB` — Hex renk (örnek: `&#ff5500`)
- `&l` `&n` `&o` — Kalın, altı çizili, italik

---

## 🛠️ Derleme (Build)

### GitHub Actions (Otomatik)
Her `main` branch'e push yapıldığında otomatik derlenir.  
**Actions → En son workflow → Artifacts** kısmından JAR'ı indir.

### Manuel Derleme
```bash
git clone https://github.com/KULLANICIN/BladeX.git
cd BladeX
mvn clean package
# JAR: target/BladeX-1.0.0.jar
```

**Gereksinimler:** Java 21+, Maven 3.8+

---

## 📁 Dosya Yapısı

```
BladeX/
├── .github/
│   └── workflows/
│       └── build.yml          ← GitHub Actions otomatik build
├── src/
│   └── main/
│       ├── java/
│       │   └── com/bladex/
│       │       └── BladeX.java    ← Ana plugin dosyası
│       └── resources/
│           ├── plugin.yml         ← Plugin tanımlaması
│           └── config.yml         ← Tam özelleştirme config
├── pom.xml                    ← Maven build dosyası
└── README.md
```

---

## 📋 Gereksinimler

- **Sunucu:** Paper 1.21.x (Spigot desteklenmez)
- **Java:** 21 veya üzeri
- **İzin sistemi:** Herhangi bir permissions plugin (LuckPerms önerilir)

---

## 🏷️ Lisans

Bu proje Fear Craft'a özel geliştirilmiştir. İzinsiz dağıtılması yasaktır.

---

<p align="center">Made with ❤️ for <b>Fear Craft</b></p>
