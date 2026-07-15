import Foundation

enum MARLanguage: String, CaseIterable, Identifiable {
    case system, en, zh, hi, es, fr, ar, bn, pt, ru, de
    var id: String { rawValue }
    var name: String {
        ["system":"System", "en":"English", "zh":"简体中文", "hi":"हिन्दी",
         "es":"Español", "fr":"Français", "ar":"العربية", "bn":"বাংলা",
         "pt":"Português", "ru":"Русский", "de":"Deutsch"][rawValue]!
    }
}

enum T {
    static func code(_ selected: String) -> String {
        if selected != "system" { return selected }
        let system = Locale.current.language.languageCode?.identifier ?? "en"
        return MARLanguage(rawValue: system) == nil ? "en" : system
    }

    static func text(_ key: String, _ language: String) -> String {
        let values: [String: [String: String]] = [
            "title": ["en":"Military Aircraft Radar", "zh":"军用飞机雷达", "hi":"सैन्य विमान रडार", "es":"Radar de aviones militares", "fr":"Radar d’avions militaires", "ar":"رادار الطائرات العسكرية", "bn":"সামরিক বিমান রাডার", "pt":"Radar de aeronaves militares", "ru":"Радар военных самолётов", "de":"Militärflugzeug-Radar"],
            "radar": ["en":"Radar", "zh":"雷达", "hi":"रडार", "es":"Radar", "fr":"Radar", "ar":"الرادار", "bn":"রাডার", "pt":"Radar", "ru":"Радар", "de":"Radar"],
            "aircraft": ["en":"Aircraft", "zh":"飞机", "hi":"विमान", "es":"Aeronaves", "fr":"Aéronefs", "ar":"الطائرات", "bn":"বিমান", "pt":"Aeronaves", "ru":"Самолёты", "de":"Flugzeuge"],
            "settings": ["en":"Settings", "zh":"设置", "hi":"सेटिंग्स", "es":"Ajustes", "fr":"Paramètres", "ar":"الإعدادات", "bn":"সেটিংস", "pt":"Configurações", "ru":"Настройки", "de":"Einstellungen"],
            "start": ["en":"Start monitoring", "zh":"开始监控", "hi":"निगरानी शुरू करें", "es":"Iniciar monitoreo", "fr":"Démarrer la surveillance", "ar":"بدء المراقبة", "bn":"মনিটরিং শুরু করুন", "pt":"Iniciar monitoramento", "ru":"Начать мониторинг", "de":"Überwachung starten"],
            "stop": ["en":"Stop monitoring", "zh":"停止监控", "hi":"निगरानी रोकें", "es":"Detener monitoreo", "fr":"Arrêter la surveillance", "ar":"إيقاف المراقبة", "bn":"মনিটরিং বন্ধ করুন", "pt":"Parar monitoramento", "ru":"Остановить мониторинг", "de":"Überwachung stoppen"],
            "contacts": ["en":"Contacts", "zh":"目标", "hi":"संपर्क", "es":"Contactos", "fr":"Contacts", "ar":"الطائرات", "bn":"কন্টাক্ট", "pt":"Contatos", "ru":"Контакты", "de":"Kontakte"],
            "radius": ["en":"Scan radius", "zh":"扫描半径", "hi":"स्कैन दायरा", "es":"Radio de escaneo", "fr":"Rayon de détection", "ar":"نطاق المسح", "bn":"স্ক্যান ব্যাসার্ধ", "pt":"Raio de varredura", "ru":"Радиус сканирования", "de":"Scan-Radius"],
            "language": ["en":"Language", "zh":"语言", "hi":"भाषा", "es":"Idioma", "fr":"Langue", "ar":"اللغة", "bn":"ভাষা", "pt":"Idioma", "ru":"Язык", "de":"Sprache"],
            "tracker": ["en":"Tracker", "zh":"跟踪器", "hi":"ट्रैकर", "es":"Rastreador", "fr":"Traceur", "ar":"المتعقب", "bn":"ট্র্যাকার", "pt":"Rastreador", "ru":"Трекер", "de":"Tracker"],
            "trackerNote": ["en":"ADS-B Exchange is recommended, as some aircraft may not be visible on Flightradar24.", "zh":"推荐使用 ADS-B Exchange，因为部分飞机可能无法在 Flightradar24 上显示。", "hi":"ADS-B Exchange की सलाह दी जाती है, क्योंकि कुछ विमान Flightradar24 पर दिखाई न दें।", "es":"Se recomienda ADS-B Exchange, ya que es posible que algunas aeronaves no sean visibles en Flightradar24.", "fr":"ADS-B Exchange est recommandé, car certains aéronefs peuvent ne pas être visibles sur Flightradar24.", "ar":"يُنصح باستخدام ADS-B Exchange، إذ قد لا تكون بعض الطائرات مرئية على Flightradar24.", "bn":"ADS-B Exchange ব্যবহারের পরামর্শ দেওয়া হয়, কারণ কিছু বিমান Flightradar24-এ দৃশ্যমান নাও হতে পারে।", "pt":"O ADS-B Exchange é recomendado, pois algumas aeronaves podem não estar visíveis no Flightradar24.", "ru":"Рекомендуется ADS-B Exchange, поскольку некоторые воздушные суда могут не отображаться во Flightradar24.", "de":"ADS-B Exchange wird empfohlen, da einige Luftfahrzeuge auf Flightradar24 möglicherweise nicht sichtbar sind."],
            "legal": ["en":"Legal and data sources", "zh":"法律信息与数据来源", "hi":"कानूनी जानकारी और डेटा स्रोत", "es":"Información legal y fuentes de datos", "fr":"Informations juridiques et sources de données", "ar":"المعلومات القانونية ومصادر البيانات", "bn":"আইনি তথ্য ও ডেটা উৎস", "pt":"Informações legais e fontes de dados", "ru":"Правовая информация и источники данных", "de":"Rechtliches und Datenquellen"],
            "noContacts": ["en":"No military contacts detected", "zh":"未发现军用目标", "hi":"कोई सैन्य संपर्क नहीं मिला", "es":"No se detectaron contactos militares", "fr":"Aucun contact militaire détecté", "ar":"لم يتم رصد طائرات عسكرية", "bn":"কোনো সামরিক কন্টাক্ট পাওয়া যায়নি", "pt":"Nenhum contato militar detectado", "ru":"Военные борта не обнаружены", "de":"Keine militärischen Kontakte erkannt"]
        ]
        let code = code(language)
        return values[key]?[code] ?? values[key]?["en"] ?? key
    }
}
