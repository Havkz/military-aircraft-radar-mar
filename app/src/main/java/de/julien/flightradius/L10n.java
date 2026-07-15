package de.julien.flightradius;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import java.util.Locale;

final class L10n {
    static final String[] CODES = {"system", "en", "zh", "hi", "es", "fr", "ar", "bn", "pt", "ru", "de"};
    static final String[] NAMES = {"System", "English", "简体中文", "हिन्दी", "Español", "Français", "العربية", "বাংলা", "Português", "Русский", "Deutsch"};
    private static final String[] SUPPORTED = {"en", "zh", "hi", "es", "fr", "ar", "bn", "pt", "ru", "de"};

    private L10n() { }

    static String language(Context context) {
        String selected = AppPreferences.get(context).getString(AppPreferences.KEY_LANGUAGE, "system");
        String code = "system".equals(selected) ? Locale.getDefault().getLanguage() : selected;
        for (String supported : SUPPORTED) if (supported.equals(code)) return code;
        return "en";
    }

    static void applyDirection(Activity activity) {
        activity.getWindow().getDecorView().setLayoutDirection(
                "ar".equals(language(activity)) ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    }

    static String t(Context c, String key) {
        switch (key) {
            case "live_radar": return v(c,"LIVE RADAR","实时雷达","लाइव रडार","RADAR EN VIVO","RADAR EN DIRECT","رادار مباشر","লাইভ রাডার","RADAR AO VIVO","РАДАР ОНЛАЙН","LIVE-RADAR");
            case "app_title": return v(c,"MILITARY\nAIRCRAFT RADAR","军用飞机\n雷达","सैन्य विमान\nरडार","RADAR DE AVIONES\nMILITARES","RADAR D’AVIONS\nMILITAIRES","رادار الطائرات\nالعسكرية","সামরিক বিমান\nরাডার","RADAR DE AERONAVES\nMILITARES","РАДАР ВОЕННЫХ\nСАМОЛЁТОВ","MILITÄRFLUGZEUG-\nRADAR");
            case "settings": return v(c,"Settings","设置","सेटिंग्स","Ajustes","Paramètres","الإعدادات","সেটিংস","Configurações","Настройки","Einstellungen");
            case "contacts": return v(c,"CONTACTS","目标","संपर्क","CONTACTOS","CONTACTS","جهات الاتصال","কন্টাক্ট","CONTATOS","КОНТАКТЫ","KONTAKTE");
            case "last_scan": return v(c,"LAST SCAN","上次扫描","अंतिम स्कैन","ÚLTIMO ESCANEO","DERNIER SCAN","آخر مسح","শেষ স্ক্যান","ÚLTIMA VARREDURA","ПОСЛЕДНИЙ СКАН","LETZTER SCAN");
            case "nearest": return v(c,"NEAREST MILITARY CONTACT","最近的军用目标","निकटतम सैन्य संपर्क","CONTACTO MILITAR MÁS CERCANO","CONTACT MILITAIRE LE PLUS PROCHE","أقرب طائرة عسكرية","নিকটতম সামরিক কন্টাক্ট","CONTATO MILITAR MAIS PRÓXIMO","БЛИЖАЙШИЙ ВОЕННЫЙ БОРТ","NÄCHSTER MILITÄRKONTAKT");
            case "scan_radius": return v(c,"SCAN RADIUS","扫描半径","स्कैन दायरा","RADIO DE ESCANEO","RAYON DE DÉTECTION","نطاق المسح","স্ক্যান ব্যাসার্ধ","RAIO DE VARREDURA","РАДИУС СКАНИРОВАНИЯ","SCAN-RADIUS");
            case "battery": return v(c,"BATTERY OPTIMIZATION","电池优化","बैटरी अनुकूलन","OPTIMIZACIÓN DE BATERÍA","OPTIMISATION DE LA BATTERIE","تحسين البطارية","ব্যাটারি অপ্টিমাইজেশন","OTIMIZAÇÃO DE BATERIA","ОПТИМИЗАЦИЯ БАТАРЕИ","AKKUOPTIMIERUNG");
            case "not_available": return v(c,"Not available","不可用","उपलब्ध नहीं","No disponible","Non disponible","غير متاح","উপলভ্য নয়","Não disponível","Недоступно","Nicht verfügbar");
            case "aircraft": return v(c,"AIRCRAFT","飞机","विमान","AERONAVES","AÉRONEFS","الطائرات","বিমান","AERONAVES","САМОЛЁТЫ","FLUGZEUGE");
            case "location_required": return v(c,"Location permission is required","需要位置权限","स्थान अनुमति आवश्यक है","Se requiere permiso de ubicación","L’autorisation de localisation est requise","يلزم إذن الموقع","লোকেশন অনুমতি প্রয়োজন","A permissão de localização é necessária","Требуется разрешение на геолокацию","Standortberechtigung wird benötigt");
            case "radar_active": return v(c,"● RADAR ACTIVE // LIVE","● 雷达已启动 // 实时","● रडार सक्रिय // लाइव","● RADAR ACTIVO // EN VIVO","● RADAR ACTIF // DIRECT","● الرادار نشط // مباشر","● রাডার সক্রিয় // লাইভ","● RADAR ATIVO // AO VIVO","● РАДАР АКТИВЕН // ОНЛАЙН","● RADAR AKTIV // LIVE");
            case "radar_standby": return v(c,"○ RADAR ON STANDBY","○ 雷达待机","○ रडार स्टैंडबाय","○ RADAR EN ESPERA","○ RADAR EN VEILLE","○ الرادار في وضع الاستعداد","○ রাডার স্ট্যান্ডবাই","○ RADAR EM ESPERA","○ РАДАР В РЕЖИМЕ ОЖИДАНИЯ","○ RADAR IN BEREITSCHAFT");
            case "stop_monitoring": return v(c,"STOP MONITORING","停止监控","निगरानी रोकें","DETENER MONITOREO","ARRÊTER LA SURVEILLANCE","إيقاف المراقبة","মনিটরিং বন্ধ করুন","PARAR MONITORAMENTO","ОСТАНОВИТЬ МОНИТОРИНГ","ÜBERWACHUNG STOPPEN");
            case "start_monitoring": return v(c,"START MONITORING","开始监控","निगरानी शुरू करें","INICIAR MONITOREO","DÉMARRER LA SURVEILLANCE","بدء المراقبة","মনিটরিং শুরু করুন","INICIAR MONITORAMENTO","НАЧАТЬ МОНИТОРИНГ","ÜBERWACHUNG STARTEN");
            case "paused": return v(c,"System paused","系统已暂停","सिस्टम रुका हुआ है","Sistema en pausa","Système en pause","النظام متوقف مؤقتًا","সিস্টেম বিরত","Sistema pausado","Система приостановлена","System pausiert");
            case "connected": return v(c,"Connected to live data","已连接实时数据","लाइव डेटा से जुड़ा","Conectado a datos en vivo","Connecté aux données en direct","متصل بالبيانات المباشرة","লাইভ ডেটায় সংযুক্ত","Conectado aos dados ao vivo","Подключено к данным","Mit Live-Daten verbunden");
            case "waiting_location": return v(c,"Waiting for location signal","正在等待位置信号","स्थान संकेत की प्रतीक्षा","Esperando señal de ubicación","En attente du signal de localisation","في انتظار إشارة الموقع","লোকেশন সিগন্যালের অপেক্ষায়","Aguardando sinal de localização","Ожидание сигнала геолокации","Warte auf Standortsignal");
            case "connecting": return v(c,"Connecting …","正在连接…","कनेक्ट हो रहा है…","Conectando…","Connexion…","جارٍ الاتصال…","সংযোগ করা হচ্ছে…","Conectando…","Подключение…","Verbindung wird hergestellt …");
            case "no_contact": return v(c,"No contact","无目标","कोई संपर्क नहीं","Sin contactos","Aucun contact","لا توجد طائرات","কোনো কন্টাক্ট নেই","Nenhum contato","Нет контактов","Kein Kontakt");
            case "back": return v(c,"BACK","返回","वापस","ATRÁS","RETOUR","رجوع","ফিরে যান","VOLTAR","НАЗАД","ZURÜCK");
            case "configuration": return v(c,"APP CONFIGURATION","应用配置","ऐप कॉन्फ़िगरेशन","CONFIGURACIÓN DE LA APP","CONFIGURATION DE L’APP","إعداد التطبيق","অ্যাপ কনফিগারেশন","CONFIGURAÇÃO DO APP","НАСТРОЙКА ПРИЛОЖЕНИЯ","APP-KONFIGURATION");
            case "appearance": return v(c,"APPEARANCE","外观","रूप-रंग","APARIENCIA","APPARENCE","المظهر","চেহারা","APARÊNCIA","ОФОРМЛЕНИЕ","DARSTELLUNG");
            case "theme": return v(c,"Theme","主题","थीम","Tema","Thème","المظهر","থিম","Tema","Тема","Farbschema");
            case "oled_dark": return v(c,"OLED Dark","OLED 深色","OLED डार्क","OLED oscuro","OLED sombre","OLED داكن","OLED ডার্ক","OLED escuro","OLED тёмная","OLED Dunkel");
            case "light": return v(c,"Light","浅色","लाइट","Claro","Clair","فاتح","লাইট","Claro","Светлая","Hell");
            case "system": return v(c,"System","系统","सिस्टम","Sistema","Système","النظام","সিস্টেম","Sistema","Система","System");
            case "language": return v(c,"Language","语言","भाषा","Idioma","Langue","اللغة","ভাষা","Idioma","Язык","Sprache");
            case "units": return v(c,"Units","单位","इकाइयाँ","Unidades","Unités","الوحدات","একক","Unidades","Единицы","Einheiten");
            case "aviation_units": return v(c,"Aviation (NM / ft)","航空（海里/英尺）","विमानन (NM / ft)","Aviación (NM / ft)","Aviation (NM / ft)","طيران (NM / ft)","এভিয়েশন (NM / ft)","Aviação (NM / ft)","Авиационные (NM / ft)","Luftfahrt (NM / ft)");
            case "metric_units": return v(c,"Metric (km / m)","公制（公里/米）","मेट्रिक (km / m)","Métrico (km / m)","Métrique (km / m)","متري (km / m)","মেট্রিক (km / m)","Métrico (km / m)","Метрические (км / м)","Metrisch (km / m)");
            case "live_section": return v(c,"LIVE RADAR","实时雷达","लाइव रडार","RADAR EN VIVO","RADAR EN DIRECT","رادار مباشر","লাইভ রাডার","RADAR AO VIVO","РАДАР ОНЛАЙН","LIVE-RADAR");
            case "refresh_rate": return v(c,"Refresh rate","刷新频率","रीफ़्रेश दर","Frecuencia de actualización","Fréquence d’actualisation","معدل التحديث","রিফ্রেশ রেট","Taxa de atualização","Частота обновления","Aktualisierung");
            case "tracker_tap": return v(c,"Tracker on notification tap","点击通知时使用的跟踪器","सूचना टैप पर ट्रैकर","Rastreador al tocar la notificación","Traceur au toucher de la notification","المتعقب عند لمس الإشعار","নোটিফিকেশন ট্যাপে ট্র্যাকার","Rastreador ao tocar na notificação","Трекер при нажатии","Tracker beim Antippen");
            case "vibration": return v(c,"Vibrate for new contact","新目标时振动","नए संपर्क पर कंपन","Vibrar con nuevo contacto","Vibrer pour un nouveau contact","اهتزاز عند رصد طائرة جديدة","নতুন কন্টাক্টে ভাইব্রেট","Vibrar para novo contato","Вибрация при новом контакте","Vibration bei neuem Kontakt");
            case "information": return v(c,"INFORMATION","信息","जानकारी","INFORMACIÓN","INFORMATIONS","معلومات","তথ্য","INFORMAÇÕES","ИНФОРМАЦИЯ","INFORMATION");
            case "live_aircraft": return v(c,"LIVE AIRCRAFT","实时飞机","लाइव विमान","AERONAVES EN VIVO","AÉRONEFS EN DIRECT","الطائرات المباشرة","লাইভ বিমান","AERONAVES AO VIVO","САМОЛЁТЫ ОНЛАЙН","LIVE-FLUGZEUGE");
            case "session_aircraft": return v(c,"SESSION AIRCRAFT","本次会话飞机","सत्र के विमान","AERONAVES DE LA SESIÓN","AÉRONEFS DE LA SESSION","طائرات الجلسة","সেশনের বিমান","AERONAVES DA SESSÃO","САМОЛЁТЫ СЕАНСА","FLUGZEUGE DIESER SITZUNG");
            case "since_app_start": return v(c,"MILITARY CONTACTS SEEN SINCE APP START","自应用启动以来发现的军用目标","ऐप शुरू होने के बाद देखे गए सैन्य संपर्क","CONTACTOS MILITARES DESDE EL INICIO","CONTACTS MILITAIRES DEPUIS LE DÉMARRAGE","الطائرات العسكرية منذ بدء التطبيق","অ্যাপ চালুর পর দেখা সামরিক কন্টাক্ট","CONTATOS MILITARES DESDE O INÍCIO","ВОЕННЫЕ БОРТА С ЗАПУСКА","MILITÄRKONTAKTE SEIT APP-START");
            case "military_in_radius": return v(c,"MILITARY CONTACTS IN SELECTED RADIUS","所选半径内的军用目标","चुने दायरे में सैन्य संपर्क","CONTACTOS MILITARES EN EL RADIO","CONTACTS MILITAIRES DANS LE RAYON","الطائرات العسكرية ضمن النطاق","নির্বাচিত ব্যাসার্ধে সামরিক কন্টাক্ট","CONTATOS MILITARES NO RAIO","ВОЕННЫЕ БОРТА В РАДИУСЕ","MILITÄRKONTAKTE IM GEWÄHLTEN RADIUS");
            case "empty_list": return v(c,"No military contacts detected.\nLive scan continues.","未发现军用目标。\n实时扫描继续。","कोई सैन्य संपर्क नहीं मिला।\nलाइव स्कैन जारी है।","No se detectaron contactos militares.\nEl escaneo continúa.","Aucun contact militaire détecté.\nLe scan continue.","لم يتم رصد طائرات عسكرية.\nيستمر المسح المباشر.","কোনো সামরিক কন্টাক্ট পাওয়া যায়নি।\nলাইভ স্ক্যান চলছে।","Nenhum contato militar detectado.\nA varredura continua.","Военные борта не обнаружены.\nСканирование продолжается.","Keine militärischen Kontakte erkannt.\nDer Live-Scan läuft weiter.");
            case "no_callsign": return v(c,"NO CALLSIGN","无呼号","कोई कॉलसाइन नहीं","SIN INDICATIVO","SANS INDICATIF","بدون نداء","কলসাইন নেই","SEM INDICATIVO","НЕТ ПОЗЫВНОГО","OHNE CALLSIGN");
            case "distance": return v(c,"Distance","距离","दूरी","Distancia","Distance","المسافة","দূরত্ব","Distância","Расстояние","Entfernung");
            case "altitude": return v(c,"Altitude","高度","ऊँचाई","Altitud","Altitude","الارتفاع","উচ্চতা","Altitude","Высота","Höhe");
            case "ground_speed": return v(c,"Ground speed","地速","ज़मीनी गति","Velocidad terrestre","Vitesse sol","السرعة الأرضية","গ্রাউন্ড স্পিড","Velocidade no solo","Путевая скорость","Geschwindigkeit");
            case "track": return v(c,"Track","航向","ट्रैक","Rumbo","Cap","المسار","ট্র্যাক","Rota","Курс","Kurs");
            case "last_signal": return v(c,"Last signal","最后信号","अंतिम संकेत","Última señal","Dernier signal","آخر إشارة","শেষ সিগন্যাল","Último sinal","Последний сигнал","Letztes Signal");
            case "position": return v(c,"Position","位置","स्थिति","Posición","Position","الموقع","অবস্থান","Posição","Позиция","Position");
            case "status": return v(c,"Status","状态","स्थिति","Estado","Statut","الحالة","স্ট্যাটাস","Status","Статус","Status");
            case "in_range": return v(c,"In range","范围内","दायरे में","En alcance","À portée","ضمن النطاق","সীমার মধ্যে","No alcance","В радиусе","In Reichweite");
            case "out_of_range": return v(c,"Out of range","超出范围","दायरे से बाहर","Fuera de alcance","Hors de portée","خارج النطاق","সীমার বাইরে","Fora do alcance","Вне радиуса","Außer Reichweite");
            case "in_range_time": return v(c,"IN RANGE","范围内时间","दायरे में","EN ALCANCE","À PORTÉE","ضمن النطاق","সীমার মধ্যে","NO ALCANCE","В РАДИУСЕ","IN REICHWEITE");
            case "first_in_range": return v(c,"First in range","首次进入范围","पहली बार दायरे में","Primera vez en alcance","Première détection","أول ظهور ضمن النطاق","প্রথম সীমার মধ্যে","Primeira vez no alcance","Первый контакт","Erstmals in Reichweite");
            case "last_in_range": return v(c,"Last in range","最后在范围内","अंतिम बार दायरे में","Última vez en alcance","Dernière détection","آخر ظهور ضمن النطاق","শেষ সীমার মধ্যে","Última vez no alcance","Последний контакт","Zuletzt in Reichweite");
            case "open_in": return v(c,"OPEN IN ","打开方式：","इसमें खोलें ","ABRIR EN ","OUVRIR DANS ","فتح في ","এতে খুলুন ","ABRIR NO ","ОТКРЫТЬ В ","ÖFFNEN IN ");
            case "altitude_unknown": return v(c,"Altitude unknown","高度未知","ऊँचाई अज्ञात","Altitud desconocida","Altitude inconnue","الارتفاع غير معروف","উচ্চতা অজানা","Altitude desconhecida","Высота неизвестна","Höhe unbekannt");
            case "background_service": return v(c,"MAR background service","MAR 后台服务","MAR बैकग्राउंड सेवा","Servicio en segundo plano de MAR","Service d’arrière-plan MAR","خدمة MAR في الخلفية","MAR ব্যাকগ্রাউন্ড সার্ভিস","Serviço MAR em segundo plano","Фоновая служба MAR","MAR-Hintergrunddienst");
            case "background_description": return v(c,"Silent Android indicator required for monitoring","监控所需的静默 Android 指示","निगरानी हेतु आवश्यक मौन Android संकेत","Indicador silencioso de Android necesario","Indicateur Android silencieux requis","مؤشر أندرويد صامت مطلوب للمراقبة","মনিটরিংয়ের জন্য নীরব Android নির্দেশক","Indicador silencioso do Android necessário","Тихий индикатор Android для мониторинга","Technisch erforderliche, lautlose Android-Systemanzeige");
            case "alert_channel": return v(c,"Military aircraft detected","检测到军用飞机","सैन्य विमान मिला","Aeronave militar detectada","Aéronef militaire détecté","تم رصد طائرة عسكرية","সামরিক বিমান শনাক্ত","Aeronave militar detectada","Обнаружен военный самолёт","Militärflugzeug erkannt");
            case "alert_description": return v(c,"Live alerts for new military aircraft in range","范围内新军用飞机的实时提醒","दायरे में नए सैन्य विमान की लाइव चेतावनी","Alertas de nuevas aeronaves militares","Alertes d’aéronefs militaires dans le rayon","تنبيهات مباشرة للطائرات العسكرية الجديدة","ব্যাসার্ধে নতুন সামরিক বিমানের লাইভ সতর্কতা","Alertas de aeronaves militares no raio","Оповещения о новых военных бортах","Live-Warnungen für neue Militärflugzeuge im Radius");
            case "monitoring_running": return v(c,"Monitoring is running","监控正在运行","निगरानी चल रही है","El monitoreo está activo","La surveillance est active","المراقبة قيد التشغيل","মনিটরিং চলছে","O monitoramento está ativo","Мониторинг запущен","Überwachung läuft");
            case "stop_radar": return v(c,"STOP RADAR","停止雷达","रडार रोकें","DETENER RADAR","ARRÊTER LE RADAR","إيقاف الرادار","রাডার বন্ধ করুন","PARAR RADAR","ОСТАНОВИТЬ РАДАР","RADAR STOPPEN");
            case "found_via": return v(c,"Found via ADSB.lol","由 ADSB.lol 发现","ADSB.lol द्वारा मिला","Detectado mediante ADSB.lol","Détecté via ADSB.lol","تم العثور عليه عبر ADSB.lol","ADSB.lol-এর মাধ্যমে পাওয়া","Detectado via ADSB.lol","Обнаружено через ADSB.lol","Gefunden über ADSB.lol");
            case "restart_reminders": return v(c,"Restart reminders","重启提醒","पुनः आरंभ अनुस्मारक","Recordatorios de reinicio","Rappels de redémarrage","تذكيرات إعادة التشغيل","রিস্টার্ট রিমাইন্ডার","Lembretes de reinício","Напоминания о перезапуске","Neustart-Erinnerungen");
            case "restart_description": return v(c,"Reminds you to restart monitoring after a phone reboot","手机重启后提醒重新启动监控","फ़ोन रीस्टार्ट के बाद निगरानी शुरू करने की याद दिलाता है","Recuerda reiniciar el monitoreo tras reiniciar el teléfono","Rappelle de relancer la surveillance après redémarrage","يذكّرك بإعادة تشغيل المراقبة بعد إعادة تشغيل الهاتف","ফোন রিস্টার্টের পর মনিটরিং চালুর কথা মনে করায়","Lembra de reiniciar o monitoramento após reiniciar o celular","Напоминает запустить мониторинг после перезагрузки","Erinnert nach einem Handy-Neustart an das erneute Starten des Radars");
            case "restart_monitoring": return v(c,"Restart monitoring","重新启动监控","निगरानी पुनः शुरू करें","Reiniciar monitoreo","Relancer la surveillance","إعادة تشغيل المراقبة","মনিটরিং পুনরায় চালু করুন","Reiniciar monitoramento","Перезапустить мониторинг","Überwachung neu starten");
            case "tap_open_radar": return v(c,"Tap to open the radar","点击打开雷达","रडार खोलने के लिए टैप करें","Toca para abrir el radar","Touchez pour ouvrir le radar","اضغط لفتح الرادار","রাডার খুলতে ট্যাপ করুন","Toque para abrir o radar","Нажмите, чтобы открыть радар","Antippen, um das Radar zu öffnen");
            default: return key;
        }
    }

    private static String v(Context c, String en, String zh, String hi, String es, String fr,
                            String ar, String bn, String pt, String ru, String de) {
        String code = language(c);
        if ("zh".equals(code)) return zh; if ("hi".equals(code)) return hi;
        if ("es".equals(code)) return es; if ("fr".equals(code)) return fr;
        if ("ar".equals(code)) return ar; if ("bn".equals(code)) return bn;
        if ("pt".equals(code)) return pt; if ("ru".equals(code)) return ru;
        if ("de".equals(code)) return de; return en;
    }
}
