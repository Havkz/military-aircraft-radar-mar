package de.julien.flightradius;

import android.content.Context;

import org.json.JSONObject;

final class MapL10n {
    private static final String[][] ROWS = {
            {"map_menu","Map menu","地图菜单","मानचित्र मेनू","Menú del mapa","Menu de la carte","قائمة الخريطة","মানচিত্র মেনু","Menu do mapa","Меню карты","Kartenmenü"},
            {"timestamps","Timestamps on tracks","轨迹时间戳","ट्रैक पर समय","Marcas de tiempo en rutas","Horodatage des traces","الوقت على المسارات","ট্র্যাকে সময়","Horários nas trilhas","Время на треках","Zeitstempel auf Spuren"},
            {"dim_map","Dim map","调暗地图","मानचित्र मंद करें","Atenuar mapa","Assombrir la carte","تعتيم الخريطة","মানচিত্র ম্লান করুন","Escurecer mapa","Затемнить карту","Karte abdunkeln"},
            {"reset_view","Reset map view","重置地图视图","मानचित्र दृश्य रीसेट","Restablecer vista","Réinitialiser la vue","إعادة ضبط العرض","দৃশ্য রিসেট করুন","Redefinir visualização","Сбросить вид","Kartenansicht zurücksetzen"},
            {"follow_selected","Follow selected aircraft","跟随所选飞机","चुने विमान का अनुसरण","Seguir aeronave seleccionada","Suivre l’aéronef sélectionné","تتبع الطائرة المحددة","নির্বাচিত বিমান অনুসরণ","Seguir aeronave selecionada","Следовать за выбранным","Ausgewähltem Flugzeug folgen"},
            {"filters","Filters","筛选器","फ़िल्टर","Filtros","Filtres","عوامل التصفية","ফিল্টার","Filtros","Фильтры","Filter"},
            {"military_only","Military only","仅军用","केवल सैन्य","Solo militares","Militaires uniquement","عسكري فقط","শুধু সামরিক","Somente militares","Только военные","Nur Militär"},
            {"isolate_selected","Isolate selected aircraft","仅显示所选飞机","चुना विमान अलग दिखाएँ","Aislar aeronave seleccionada","Isoler l’aéronef sélectionné","عزل الطائرة المحددة","নির্বাচিত বিমান আলাদা করুন","Isolar aeronave selecionada","Изолировать выбранный","Ausgewähltes Flugzeug isolieren"},
            {"all_filters","All filter options…","所有筛选选项…","सभी फ़िल्टर विकल्प…","Todas las opciones…","Toutes les options…","كل خيارات التصفية…","সব ফিল্টার বিকল্প…","Todas as opções…","Все параметры…","Alle Filteroptionen…"},
            {"tools","Tools","工具","उपकरण","Herramientas","Outils","أدوات","সরঞ্জাম","Ferramentas","Инструменты","Werkzeuge"},
            {"tracks_all","Show tracks for all aircraft","显示所有飞机轨迹","सभी विमानों के ट्रैक","Mostrar rutas de todos","Afficher toutes les traces","عرض مسارات كل الطائرات","সব বিমানের ট্র্যাক","Mostrar trilhas de todos","Треки всех самолётов","Spuren aller Flugzeuge"},
            {"select_multiple","Select multiple aircraft","选择多架飞机","कई विमान चुनें","Seleccionar varias aeronaves","Sélectionner plusieurs aéronefs","تحديد عدة طائرات","একাধিক বিমান নির্বাচন","Selecionar várias aeronaves","Выбрать несколько","Mehrere Flugzeuge auswählen"},
            {"keep_faded","Keep faded aircraft visible","保留淡出飞机","धुंधले विमान दिखाएँ","Mantener aeronaves atenuadas","Garder les aéronefs estompés","إبقاء الطائرات الباهتة","ম্লান বিমান দৃশ্যমান","Manter aeronaves esmaecidas","Оставлять исчезнувшие","Verblasste Flugzeuge anzeigen"},
            {"random_aircraft","Follow a random aircraft","跟随随机飞机","यादृच्छिक विमान का अनुसरण","Seguir una aeronave aleatoria","Suivre un aéronef aléatoire","تتبع طائرة عشوائية","এলোমেলো বিমান অনুসরণ","Seguir aeronave aleatória","Следовать за случайным","Zufälligem Flugzeug folgen"},
            {"measure_distance","Measure distance","测量距离","दूरी मापें","Medir distancia","Mesurer la distance","قياس المسافة","দূরত্ব মাপুন","Medir distância","Измерить расстояние","Entfernung messen"},
            {"bookmarks","Bookmarks","书签","बुकमार्क","Marcadores","Favoris","الإشارات المرجعية","বুকমার্ক","Favoritos","Закладки","Lesezeichen"},
            {"map_settings","Map settings","地图设置","मानचित्र सेटिंग्स","Ajustes del mapa","Paramètres de carte","إعدادات الخريطة","মানচিত্র সেটিংস","Configurações do mapa","Настройки карты","Karteneinstellungen"},
            {"general","General","常规","सामान्य","General","Général","عام","সাধারণ","Geral","Общие","Allgemein"},
            {"text_size","Text and label size","文字和标签大小","टेक्स्ट और लेबल आकार","Tamaño de texto y etiquetas","Taille du texte et des libellés","حجم النص والتسميات","টেক্সট ও লেবেলের আকার","Tamanho de texto e rótulos","Размер текста и меток","Text- und Labelgröße"},
            {"icon_size","Aircraft icon size","飞机图标大小","विमान आइकन आकार","Tamaño del icono","Taille de l’icône","حجم رمز الطائرة","বিমান আইকনের আকার","Tamanho do ícone","Размер значка","Flugzeugsymbolgröße"},
            {"map_appearance","Map appearance","地图外观","मानचित्र रूप","Apariencia del mapa","Apparence de la carte","مظهر الخريطة","মানচিত্রের চেহারা","Aparência do mapa","Вид карты","Kartendarstellung"},
            {"dark_map","Dark map","深色地图","गहरा मानचित्र","Mapa oscuro","Carte sombre","خريطة داكنة","ডার্ক মানচিত্র","Mapa escuro","Тёмная карта","Dunkle Karte"},
            {"darker_colors","Darker colors","更深颜色","गहरे रंग","Colores más oscuros","Couleurs plus sombres","ألوان أغمق","আরও গাঢ় রং","Cores mais escuras","Более тёмные цвета","Dunklere Farben"},
            {"colored_aircraft","Colored aircraft","彩色飞机","रंगीन विमान","Aeronaves coloreadas","Aéronefs colorés","طائرات ملونة","রঙিন বিমান","Aeronaves coloridas","Цветные самолёты","Farbige Flugzeuge"},
            {"colored_tracks","Colored tracks","彩色轨迹","रंगीन ट्रैक","Rutas coloreadas","Traces colorées","مسارات ملونة","রঙিন ট্র্যাক","Trilhas coloridas","Цветные треки","Farbige Flugspuren"},
            {"hardware_tracks","Hardware-accelerated tracks","硬件加速轨迹","हार्डवेयर त्वरित ट्रैक","Rutas aceleradas por hardware","Traces accélérées matériellement","مسارات مسرعة عتاديًا","হার্ডওয়্যার-ত্বরিত ট্র্যাক","Trilhas aceleradas por hardware","Аппаратное ускорение треков","Hardwarebeschleunigte Flugspuren"},
            {"labels_altitude","Labels & altitude","标签和高度","लेबल और ऊँचाई","Etiquetas y altitud","Libellés et altitude","التسميات والارتفاع","লেবেল ও উচ্চতা","Rótulos e altitude","Метки и высота","Labels & Höhendaten"},
            {"show_labels","Show aircraft labels","显示飞机标签","विमान लेबल दिखाएँ","Mostrar etiquetas de aeronaves","Afficher les libellés des aéronefs","عرض تسميات الطائرات","বিমানের লেবেল দেখান","Mostrar rótulos das aeronaves","Показывать метки самолётов","Flugzeuglabels anzeigen"},
            {"label_units","Show units in labels","标签中显示单位","लेबल में इकाइयाँ","Mostrar unidades","Afficher les unités","عرض الوحدات","লেবেলে একক দেখান","Mostrar unidades","Показывать единицы","Einheiten in Labels"},
            {"smaller_labels","Smaller labels","较小标签","छोटे लेबल","Etiquetas más pequeñas","Libellés plus petits","تسميات أصغر","ছোট লেবেল","Rótulos menores","Мелкие метки","Kleinere Labels"},
            {"smaller_wind_labels","Smaller wind labels","更小的风标签","छोटे पवन लेबल","Etiquetas de viento más pequeñas","Libellés de vent plus petits","تسميات رياح أصغر","ছোট বাতাসের লেবেল","Rótulos de vento menores","Мелкие метки ветра","Kleinere Windlabels"},
            {"geometric_alt","Use geometric altitude (WGS84)","使用几何高度 (WGS84)","ज्यामितीय ऊँचाई (WGS84)","Usar altitud geométrica (WGS84)","Utiliser l’altitude géométrique (WGS84)","استخدام الارتفاع الهندسي (WGS84)","জ্যামিতিক উচ্চতা (WGS84)","Usar altitude geométrica (WGS84)","Геометрическая высота (WGS84)","Geometrische Höhe (WGS84)"},
            {"egm_conversion","Convert WGS84 altitude to mean sea level (Android 14+)","将 WGS84 高度转换为平均海平面（Android 14+）","WGS84 ऊँचाई को समुद्र तल में बदलें (Android 14+)","Convertir WGS84 a nivel medio del mar (Android 14+)","Convertir WGS84 vers le niveau moyen de la mer (Android 14+)","تحويل ارتفاع WGS84 إلى متوسط سطح البحر (Android 14+)","WGS84 উচ্চতা সমুদ্রপৃষ্ঠে রূপান্তর (Android 14+)","Converter WGS84 para nível médio do mar (Android 14+)","Преобразовать WGS84 к уровню моря (Android 14+)","WGS84-Höhe in Meereshöhe umrechnen (Android 14+)"},
            {"qnh_correct","Correct barometric altitude using QNH","使用 QNH 修正气压高度","QNH से बैरो ऊँचाई सुधारें","Corregir altitud con QNH","Corriger l’altitude avec QNH","تصحيح الارتفاع باستخدام QNH","QNH দিয়ে ব্যারো উচ্চতা সংশোধন","Corrigir altitude com QNH","Коррекция высоты по QNH","Barometrische Höhe mit QNH korrigieren"},
            {"track_utc","Track timestamps in UTC","轨迹时间使用 UTC","ट्रैक समय UTC में","Marcas de ruta en UTC","Horodatage des traces en UTC","وقت المسارات بتوقيت UTC","ট্র্যাকের সময় UTC","Horários das trilhas em UTC","Время треков в UTC","Spur-Zeitstempel in UTC"},
            {"live_track_utc","Live track labels in UTC","实时轨迹标签使用 UTC","लाइव ट्रैक लेबल UTC में","Etiquetas de ruta en vivo en UTC","Libellés des traces en direct en UTC","تسميات المسار المباشر بتوقيت UTC","লাইভ ট্র্যাক লেবেল UTC-তে","Rótulos de trilha ao vivo em UTC","Метки текущего трека в UTC","Live-Spurlabels in UTC"},
            {"historic_track_utc","Historic track labels in UTC","历史轨迹标签使用 UTC","ऐतिहासिक ट्रैक लेबल UTC में","Etiquetas de rutas históricas en UTC","Libellés des traces historiques en UTC","تسميات المسارات التاريخية بتوقيت UTC","ঐতিহাসিক ট্র্যাক লেবেল UTC-তে","Rótulos de trilhas históricas em UTC","Метки истории трека в UTC","Historische Spurlabels in UTC"},
            {"tracks_history","Tracks & history","轨迹和历史","ट्रैक और इतिहास","Rutas e historial","Traces et historique","المسارات والسجل","ট্র্যাক ও ইতিহাস","Trilhas e histórico","Треки и история","Spuren & Verlauf"},
            {"last_leg","Last leg only","仅最后航段","केवल अंतिम चरण","Solo último tramo","Dernier trajet uniquement","آخر مسار فقط","শুধু শেষ পর্ব","Somente último trecho","Только последний участок","Nur letzter Flugabschnitt"},
            {"altitude_chart","Altitude chart","高度图","ऊँचाई चार्ट","Gráfico de altitud","Graphique d’altitude","مخطط الارتفاع","উচ্চতার চার্ট","Gráfico de altitude","График высоты","Höhendiagramm"},
            {"info_panel","Aircraft info panel","飞机信息面板","विमान जानकारी पैनल","Panel de información","Panneau d’information","لوحة معلومات الطائرة","বিমানের তথ্য প্যানেল","Painel de informações","Панель информации","Flugzeug-Infopanel"},
            {"enable_info","Enable infoblock","启用信息块","जानकारी ब्लॉक चालू","Activar panel de información","Activer le bloc d’information","تفعيل مربع المعلومات","তথ্য ব্লক চালু","Ativar bloco de informações","Включить инфоблок","Infoblock aktivieren"},
            {"wide_info","Wide infoblock","宽信息块","चौड़ा जानकारी ब्लॉक","Panel ancho","Bloc d’information large","مربع معلومات عريض","প্রশস্ত তথ্য ব্লক","Bloco de informações largo","Широкий инфоблок","Breiter Infoblock"},
            {"hover_info","Show infoblock on pointer hover","指针悬停时显示信息块","पॉइंटर होवर पर जानकारी दिखाएँ","Mostrar información al pasar el puntero","Afficher les informations au survol","عرض المعلومات عند تمرير المؤشر","পয়েন্টার রাখলে তথ্য দেখান","Mostrar informações ao passar o ponteiro","Показывать данные при наведении","Infoblock beim Darüberzeigen öffnen"},
            {"auto_select","Auto-select first aircraft","自动选择第一架飞机","पहला विमान स्वतः चुनें","Seleccionar primera aeronave","Sélectionner le premier aéronef","تحديد أول طائرة تلقائيًا","প্রথম বিমান স্বয়ংক্রিয় নির্বাচন","Selecionar primeira aeronave","Автовыбор первого самолёта","Erstes Flugzeug automatisch auswählen"},
            {"pictures_planespotters","Pictures: planespotters.net","图片：planespotters.net","तस्वीरें: planespotters.net","Fotos: planespotters.net","Photos : planespotters.net","صور: planespotters.net","ছবি: planespotters.net","Fotos: planespotters.net","Фото: planespotters.net","Bilder: planespotters.net"},
            {"pictures_planespotting","Pictures: planespotting.be","图片：planespotting.be","तस्वीरें: planespotting.be","Fotos: planespotting.be","Photos : planespotting.be","صور: planespotting.be","ছবি: planespotting.be","Fotos: planespotting.be","Фото: planespotting.be","Bilder: planespotting.be"},
            {"traffic_privacy","Traffic & privacy","交通和隐私","यातायात और गोपनीयता","Tráfico y privacidad","Trafic et confidentialité","الحركة والخصوصية","ট্রাফিক ও গোপনীয়তা","Tráfego e privacidade","Трафик и конфиденциальность","Verkehr & Datenschutz"},
            {"ground_vehicles","Show ground vehicles","显示地面车辆","ग्राउंड वाहन दिखाएँ","Mostrar vehículos terrestres","Afficher les véhicules au sol","عرض المركبات الأرضية","স্থলযান দেখান","Mostrar veículos terrestres","Показывать наземный транспорт","Bodenfahrzeuge anzeigen"},
            {"non_icao","Show non-ICAO targets","显示非 ICAO 目标","गैर-ICAO लक्ष्य दिखाएँ","Mostrar objetivos no ICAO","Afficher les cibles non OACI","عرض الأهداف غير ICAO","নন-ICAO লক্ষ্য দেখান","Mostrar alvos não ICAO","Показывать не-ICAO цели","Nicht-ICAO-Ziele anzeigen"},
            {"update_gps","Update GPS marker","更新 GPS 标记","GPS चिह्न अपडेट करें","Actualizar marcador GPS","Actualiser le repère GPS","تحديث علامة GPS","GPS চিহ্ন আপডেট","Atualizar marcador GPS","Обновлять GPS-метку","GPS-Position aktualisieren"},
            {"include_filters_url","Include filters in the map URL","在地图网址中包含筛选条件","मानचित्र URL में फ़िल्टर शामिल करें","Incluir filtros en la URL del mapa","Inclure les filtres dans l’URL de la carte","تضمين الفلاتر في رابط الخريطة","মানচিত্র URL-এ ফিল্টার রাখুন","Incluir filtros no URL do mapa","Добавлять фильтры в URL карты","Filter in die Karten-URL aufnehmen"},
            {"advanced","Advanced","高级","उन्नत","Avanzado","Avancé","متقدم","উন্নত","Avançado","Дополнительно","Erweitert"},
            {"debug_tracks","Debug track labels","调试轨迹标签","डिबग ट्रैक लेबल","Etiquetas de depuración","Libellés de débogage","تسميات تصحيح المسارات","ডিবাগ ট্র্যাক লেবেল","Rótulos de depuração","Отладочные метки","Debug-Spurlabels"},
            {"bypass_filters","Debug: bypass filters","调试：绕过筛选","डिबग: फ़िल्टर छोड़ें","Depuración: omitir filtros","Débogage : ignorer les filtres","تصحيح: تجاوز الفلاتر","ডিবাগ: ফিল্টার এড়ান","Depuração: ignorar filtros","Отладка: без фильтров","Debug: Filter umgehen"},
            {"reset_all","Reset all map settings","重置所有地图设置","सभी मानचित्र सेटिंग रीसेट","Restablecer ajustes del mapa","Réinitialiser les paramètres","إعادة ضبط إعدادات الخريطة","সব মানচিত্র সেটিং রিসেট","Redefinir configurações","Сбросить настройки карты","Alle Kartenoptionen zurücksetzen"},
            {"search_hint","Callsign, registration, hex or type","呼号、注册号、Hex 或类型","कॉलसाइन, पंजीकरण, Hex या प्रकार","Indicativo, matrícula, hex o tipo","Indicatif, immatriculation, hex ou type","نداء أو تسجيل أو Hex أو نوع","কলসাইন, নিবন্ধন, Hex বা ধরন","Indicativo, matrícula, hex ou tipo","Позывной, регистрация, hex или тип","Callsign, Kennung, Hex oder Typ"},
            {"find","Find","查找","खोजें","Buscar","Rechercher","بحث","খুঁজুন","Buscar","Найти","Suchen"},
            {"aircraft_filters","Aircraft filters","飞机筛选器","विमान फ़िल्टर","Filtros de aeronaves","Filtres d’aéronefs","فلاتر الطائرات","বিমান ফিল্টার","Filtros de aeronaves","Фильтры самолётов","Flugzeugfilter"},
            {"type_code","Type code","类型代码","प्रकार कोड","Código de tipo","Code type","رمز النوع","টাইপ কোড","Código de tipo","Код типа","Typcode"},
            {"type_description","Type description","类型描述","प्रकार विवरण","Descripción del tipo","Description du type","وصف النوع","ধরনের বিবরণ","Descrição do tipo","Описание типа","Typbeschreibung"},
            {"hex_id","ICAO hex id","ICAO Hex ID","ICAO Hex ID","ID hex ICAO","Identifiant hex OACI","معرف ICAO السداسي","ICAO Hex ID","ID hex ICAO","ICAO hex ID","ICAO-Hex-ID"},
            {"source","Source","来源","स्रोत","Fuente","Source","المصدر","উৎস","Fonte","Источник","Quelle"},
            {"db_flags","Database flags","数据库标志","डेटाबेस फ़्लैग","Indicadores de base de datos","Indicateurs de base","علامات قاعدة البيانات","ডাটাবেস ফ্ল্যাগ","Sinalizadores de banco","Флаги базы","Datenbank-Flags"},
            {"registration","Registration","注册号","पंजीकरण","Matrícula","Immatriculation","التسجيل","নিবন্ধন","Matrícula","Регистрация","Kennung"},
            {"country_prefix","Country / registration prefix","国家/注册前缀","देश / पंजीकरण उपसर्ग","País / prefijo de matrícula","Pays / préfixe d’immatriculation","الدولة / بادئة التسجيل","দেশ / নিবন্ধন উপসর্গ","País / prefixo de matrícula","Страна / префикс","Land / Kennungspräfix"},
            {"category","Category (A3, B0, …)","类别 (A3, B0, …)","श्रेणी (A3, B0, …)","Categoría (A3, B0, …)","Catégorie (A3, B0, …)","الفئة (A3, B0, …)","বিভাগ (A3, B0, …)","Categoria (A3, B0, …)","Категория (A3, B0, …)","Kategorie (A3, B0, …)"},
            {"apply_filters","Apply filters","应用筛选","फ़िल्टर लागू करें","Aplicar filtros","Appliquer les filtres","تطبيق الفلاتر","ফিল্টার প্রয়োগ","Aplicar filtros","Применить фильтры","Filter anwenden"},
            {"clear_filters","Clear all filters","清除所有筛选","सभी फ़िल्टर हटाएँ","Borrar todos los filtros","Effacer tous les filtres","مسح كل الفلاتر","সব ফিল্টার মুছুন","Limpar todos os filtros","Очистить фильтры","Alle Filter löschen"},
            {"no_bookmarks","No bookmarked aircraft","没有收藏的飞机","कोई बुकमार्क विमान नहीं","No hay aeronaves guardadas","Aucun aéronef favori","لا توجد طائرات محفوظة","কোনো বুকমার্ক বিমান নেই","Nenhuma aeronave favorita","Нет закладок","Keine Flugzeuge gespeichert"},
            {"waiting","Waiting for live aircraft positions …","等待实时飞机位置…","लाइव विमान स्थिति की प्रतीक्षा…","Esperando posiciones en vivo…","Attente des positions en direct…","بانتظار مواقع الطائرات…","লাইভ বিমানের অবস্থানের অপেক্ষা…","Aguardando posições ao vivo…","Ожидание позиций…","Warte auf Live-Positionen …"},
            {"tap_two","Tap two points","点击两个点","दो बिंदु टैप करें","Toca dos puntos","Touchez deux points","اضغط نقطتين","দুটি বিন্দু ট্যাপ করুন","Toque em dois pontos","Нажмите две точки","Zwei Punkte antippen"},
            {"your_position","Your position","您的位置","आपका स्थान","Tu posición","Votre position","موقعك","আপনার অবস্থান","Sua posição","Ваше местоположение","Deine Position"},
            {"unknown_type","Unknown type","未知类型","अज्ञात प्रकार","Tipo desconocido","Type inconnu","نوع غير معروف","অজানা ধরন","Tipo desconhecido","Неизвестный тип","Unbekannter Typ"},
            {"altitude_na","Altitude n/a","高度未知","ऊँचाई उपलब्ध नहीं","Altitud n/d","Altitude indisponible","الارتفاع غير متاح","উচ্চতা নেই","Altitude indisponível","Высота неизвестна","Höhe unbekannt"},
            {"seen","Seen","最后信号","देखा","Visto","Vu","شوهد","দেখা","Visto","Сигнал","Signal"},
            {"military","Military","军用","सैन्य","Militar","Militaire","عسكري","সামরিক","Militar","Военный","Militär"},
            {"track","Track","航迹","ट्रैक","Rumbo","Route","المسار","ট্র্যাক","Rota","Курс","Kurs"},
            {"squawk","Squawk","应答机代码","स्क्वॉक","Código squawk","Code transpondeur","رمز سكواك","স্কোয়াক","Código squawk","Сквок","Squawk"}
            ,{"adsbx_key","ADS-B Exchange API key","ADS-B Exchange API 密钥","ADS-B Exchange API कुंजी","Clave API de ADS-B Exchange","Clé API ADS-B Exchange","مفتاح ADS-B Exchange API","ADS-B Exchange API কী","Chave API ADS-B Exchange","Ключ API ADS-B Exchange","ADS-B-Exchange-API-Key"}
            ,{"configured","Configured","已配置","कॉन्फ़िगर किया गया","Configurado","Configuré","تم الإعداد","কনফিগার করা","Configurado","Настроено","Konfiguriert"}
            ,{"not_configured","Not configured","未配置","कॉन्फ़िगर नहीं","No configurado","Non configuré","غير معد","কনফিগার করা নেই","Não configurado","Не настроено","Nicht konfiguriert"}
            ,{"key_private","Optional. Stored only in the app's private, non-backed-up storage.","可选。仅存储在应用的私有非备份存储中。","वैकल्पिक। केवल ऐप के निजी, बैकअप-रहित स्टोरेज में सहेजा जाता है।","Opcional. Solo se guarda en el almacenamiento privado sin copia de seguridad.","Facultatif. Stocké uniquement dans l’espace privé non sauvegardé de l’application.","اختياري. يُخزن فقط في مساحة التطبيق الخاصة غير المنسوخة احتياطيًا.","ঐচ্ছিক। শুধু অ্যাপের ব্যক্তিগত, ব্যাকআপবিহীন স্টোরেজে রাখা হয়।","Opcional. Armazenada apenas no espaço privado sem backup do app.","Необязательно. Хранится только в приватном хранилище без резервного копирования.","Optional. Nur im privaten, nicht gesicherten App-Speicher abgelegt."}
            ,{"remove","Remove","删除","हटाएँ","Eliminar","Supprimer","إزالة","সরান","Remover","Удалить","Entfernen"}
            ,{"save","Save","保存","सहेजें","Guardar","Enregistrer","حفظ","সংরক্ষণ","Salvar","Сохранить","Speichern"}
            ,{"key_error","Could not store API key","无法保存 API 密钥","API कुंजी सहेजी नहीं जा सकी","No se pudo guardar la clave API","Impossible d’enregistrer la clé API","تعذر حفظ مفتاح API","API কী সংরক্ষণ করা যায়নি","Não foi possível salvar a chave API","Не удалось сохранить ключ API","API-Key konnte nicht gespeichert werden"}
            ,{"provider_notice","MAR combines ADSB.lol and Airplanes.live. ADS-B Exchange is queried only with an official API key you provide. No provider affiliation or endorsement.","MAR 合并 ADSB.lol 和 Airplanes.live。仅使用您提供的官方 API 密钥查询 ADS-B Exchange。与各提供商无隶属或认可关系。","MAR ADSB.lol और Airplanes.live को जोड़ता है। ADS-B Exchange केवल आपकी आधिकारिक API कुंजी से पूछा जाता है। किसी प्रदाता से संबद्धता नहीं है।","MAR combina ADSB.lol y Airplanes.live. ADS-B Exchange solo se consulta con una clave API oficial aportada por el usuario. Sin afiliación ni respaldo.","MAR combine ADSB.lol et Airplanes.live. ADS-B Exchange n’est interrogé qu’avec une clé API officielle fournie par l’utilisateur. Aucune affiliation ni approbation.","يجمع MAR بين ADSB.lol وAirplanes.live. لا يتم الاستعلام من ADS-B Exchange إلا بمفتاح API رسمي يقدمه المستخدم. لا توجد شراكة أو مصادقة.","MAR ADSB.lol ও Airplanes.live একত্র করে। ব্যবহারকারীর দেওয়া সরকারি API কী থাকলেই ADS-B Exchange জিজ্ঞাসা করা হয়। কোনো প্রদানকারীর অনুমোদন নেই।","O MAR combina ADSB.lol e Airplanes.live. O ADS-B Exchange só é consultado com uma chave API oficial fornecida pelo usuário. Sem afiliação ou endosso.","MAR объединяет ADSB.lol и Airplanes.live. ADS-B Exchange запрашивается только с официальным API-ключом пользователя. Связи или одобрения провайдеров нет.","MAR kombiniert ADSB.lol und Airplanes.live. ADS-B Exchange wird nur mit einem von dir hinterlegten offiziellen API-Key abgefragt. Keine Zugehörigkeit oder Empfehlung durch die Anbieter."}
            ,{"photos","Photos","照片","तस्वीरें","Fotos","Photos","صور","ছবি","Fotos","Фото","Bilder"}
            ,{"aircraft_data","Aircraft data","飞机数据","विमान डेटा","Datos de la aeronave","Données de l’aéronef","بيانات الطائرة","বিমানের তথ্য","Dados da aeronave","Данные самолёта","Flugzeugdaten"}
            ,{"altitude_details","Altitude","高度","ऊँचाई","Altitud","Altitude","الارتفاع","উচ্চতা","Altitude","Высота","Höhe"}
            ,{"movement","Movement","运动","गति","Movimiento","Mouvement","الحركة","চলাচল","Movimento","Движение","Bewegung"}
            ,{"signal_accuracy","Signal & accuracy","信号和精度","सिग्नल और सटीकता","Señal y precisión","Signal et précision","الإشارة والدقة","সংকেত ও নির্ভুলতা","Sinal e precisão","Сигнал и точность","Signal & Genauigkeit"}
            ,{"zoom_in_traffic","Zoom in to load live traffic","放大地图以加载实时交通","लाइव यातायात लोड करने के लिए ज़ूम इन करें","Acerca el mapa para cargar tráfico en vivo","Zoomez pour charger le trafic en direct","قرّب الخريطة لتحميل الحركة المباشرة","লাইভ ট্রাফিক লোড করতে জুম ইন করুন","Aproxime o mapa para carregar tráfego ao vivo","Увеличьте карту для загрузки трафика","Hineinzoomen, um Live-Flugverkehr zu laden"}
            ,{"important","Important","重要","महत्वपूर्ण","Importante","Important","مهم","গুরুত্বপূর্ণ","Importante","Важное","Wichtig"}
            ,{"data_provider","Data provider","数据提供商","डेटा प्रदाता","Proveedor de datos","Fournisseur de données","مزود البيانات","ডেটা প্রদানকারী","Provedor de dados","Поставщик данных","Datenanbieter"}
            ,{"emergency","Emergency","紧急状态","आपातकाल","Emergencia","Urgence","طوارئ","জরুরি অবস্থা","Emergência","Аварийный статус","Notfallstatus"}
            ,{"selected_altitude","Selected altitude","选择高度","चयनित ऊँचाई","Altitud seleccionada","Altitude sélectionnée","الارتفاع المحدد","নির্বাচিত উচ্চতা","Altitude selecionada","Заданная высота","Gewählte Höhe"}
            ,{"ground_speed","Ground speed","地速","भू-गति","Velocidad sobre el suelo","Vitesse sol","السرعة الأرضية","ভূমি গতি","Velocidade no solo","Путевая скорость","Geschwindigkeit über Grund"}
            ,{"ground_track","Ground track","地面航迹","ग्राउंड ट्रैक","Rumbo sobre el suelo","Route sol","المسار الأرضي","গ্রাউন্ড ট্র্যাক","Rota no solo","Путевой курс","Bodenkurs"}
            ,{"true_heading","True heading","真航向","सही दिशा","Rumbo verdadero","Cap vrai","الاتجاه الحقيقي","সত্য দিক","Proa verdadeira","Истинный курс","Wahrer Steuerkurs"}
            ,{"magnetic_heading","Magnetic heading","磁航向","चुंबकीय दिशा","Rumbo magnético","Cap magnétique","الاتجاه المغناطيسي","চৌম্বক দিক","Proa magnética","Магнитный курс","Magnetischer Steuerkurs"}
            ,{"selected_heading","Selected heading","选择航向","चयनित दिशा","Rumbo seleccionado","Cap sélectionné","الاتجاه المحدد","নির্বাচিত দিক","Proa selecionada","Заданный курс","Gewählter Steuerkurs"}
            ,{"track_rate","Track rate","航迹变化率","ट्रैक दर","Variación de rumbo","Taux de route","معدل المسار","ট্র্যাক হার","Taxa de rota","Скорость изменения курса","Kursänderungsrate"}
            ,{"roll","Roll","滚转","रोल","Alabeo","Roulis","الميلان","রোল","Rolagem","Крен","Rollwinkel"}
            ,{"latitude","Latitude","纬度","अक्षांश","Latitud","Latitude","خط العرض","অক্ষাংশ","Latitude","Широта","Breitengrad"}
            ,{"longitude","Longitude","经度","देशांतर","Longitud","Longitude","خط الطول","দ্রাঘিমাংশ","Longitude","Долгота","Längengrad"}
            ,{"position","Position","位置","स्थिति","Posición","Position","الموقع","অবস্থান","Posição","Положение","Position"}
            ,{"loading_photo","Searching aircraft photo …","正在搜索飞机照片…","विमान की तस्वीर खोजी जा रही है…","Buscando foto de la aeronave…","Recherche d’une photo…","جارٍ البحث عن صورة الطائرة…","বিমানের ছবি খোঁজা হচ্ছে…","Buscando foto da aeronave…","Поиск фотографии…","Flugzeugfoto wird gesucht …"}
            ,{"other","Other","其他","अन्य","Otro","Autre","أخرى","অন্যান্য","Outro","Другое","Sonstige"}
            ,{"clear_history","Clear track history","清除轨迹历史","ट्रैक इतिहास साफ़ करें","Borrar historial de rutas","Effacer l’historique des traces","مسح سجل المسارات","ট্র্যাক ইতিহাস মুছুন","Limpar histórico de trilhas","Очистить историю треков","Spurverlauf löschen"}
            ,{"cycle_map_style","Change map style","更改地图样式","मानचित्र शैली बदलें","Cambiar estilo del mapa","Changer le style de carte","تغيير نمط الخريطة","মানচিত্রের ধরন বদলান","Alterar estilo do mapa","Изменить стиль карты","Kartenstil wechseln"}
            ,{"distance","Distance","距离","दूरी","Distancia","Distance","المسافة","দূরত্ব","Distância","Расстояние","Entfernung"}
            ,{"barometric","Barometric altitude","气压高度","बैरोमेट्रिक ऊँचाई","Altitud barométrica","Altitude barométrique","الارتفاع البارومتري","ব্যারোমেট্রিক উচ্চতা","Altitude barométrica","Барометрическая высота","Barometrische Höhe"}
            ,{"geometric","Geometric altitude","几何高度","ज्यामितीय ऊँचाई","Altitud geométrica","Altitude géométrique","الارتفاع الهندسي","জ্যামিতিক উচ্চতা","Altitude geométrica","Геометрическая высота","Geometrische Höhe"}
            ,{"speed","Speed","速度","गति","Velocidad","Vitesse","السرعة","গতি","Velocidade","Скорость","Geschwindigkeit"}
            ,{"vertical_rate","Vertical rate","垂直速度","ऊर्ध्वाधर दर","Velocidad vertical","Vitesse verticale","المعدل العمودي","উল্লম্ব হার","Velocidade vertical","Вертикальная скорость","Vertikalrate"}
            ,{"headings","Headings","航向","दिशाएँ","Rumbos","Caps","الاتجاهات","দিক","Proas","Курсы","Richtungen"}
            ,{"selected","Selected values","选定值","चयनित मान","Valores seleccionados","Valeurs sélectionnées","القيم المحددة","নির্বাচিত মান","Valores selecionados","Выбранные значения","Ausgewählte Werte"}
            ,{"wind","Wind","风","हवा","Viento","Vent","الرياح","বাতাস","Vento","Ветер","Wind"}
            ,{"temperature","Temperature","温度","तापमान","Temperatura","Température","درجة الحرارة","তাপমাত্রা","Temperatura","Температура","Temperatur"}
            ,{"nav_modes","Navigation modes","导航模式","नेविगेशन मोड","Modos de navegación","Modes de navigation","أوضاع الملاحة","নেভিগেশন মোড","Modos de navegação","Режимы навигации","Navigationsmodi"}
            ,{"signal","Signal","信号","सिग्नल","Señal","Signal","الإشارة","সিগন্যাল","Sinal","Сигнал","Signal"}
            ,{"messages","Messages","消息","संदेश","Mensajes","Messages","الرسائل","বার্তা","Mensagens","Сообщения","Nachrichten"}
            ,{"position_age","Position age","位置时间","स्थिति आयु","Antigüedad de posición","Âge de la position","عمر الموقع","অবস্থানের বয়স","Idade da posição","Возраст позиции","Positionsalter"}
            ,{"accuracy","Accuracy","精度","सटीकता","Precisión","Précision","الدقة","নির্ভুলতা","Precisão","Точность","Genauigkeit"}
            ,{"rotorcraft","Rotorcraft","旋翼机","रोटरक्राफ्ट","Aeronave de ala rotatoria","Giravion","طائرة دوارة الجناح","রোটরক্রাফট","Aeronave de asa rotativa","Винтокрылый аппарат","Drehflügler"}
            ,{"airplanes_rate","Airplanes.live polling plan","Airplanes.live 轮询方案","Airplanes.live पोलिंग योजना","Plan de consultas de Airplanes.live","Forfait d’interrogation Airplanes.live","خطة استعلام Airplanes.live","Airplanes.live পোলিং পরিকল্পনা","Plano de consultas Airplanes.live","Тариф опроса Airplanes.live","Airplanes.live-Abfragetarif"}
            ,{"free_rate","Free rate · 180 s","免费速率 · 180 秒","मुफ़्त दर · 180 सेकंड","Frecuencia gratuita · 180 s","Fréquence gratuite · 180 s","المعدل المجاني · 180 ث","ফ্রি হার · ১৮০ সেকেন্ড","Taxa gratuita · 180 s","Бесплатный режим · 180 с","Free-Rate · 180 s"}
            ,{"business_rate","Business rate · 1.2 s","商业速率 · 1.2 秒","बिज़नेस दर · 1.2 सेकंड","Frecuencia Business · 1,2 s","Fréquence Business · 1,2 s","معدل الأعمال · 1.2 ث","বিজনেস হার · ১.২ সেকেন্ড","Taxa Business · 1,2 s","Бизнес-режим · 1,2 с","Business-Rate · 1,2 s"}
            ,{"business_rate_warning","Enable only if Airplanes.live has authorized your account or IP for Business use. This polls every 1.2 seconds and can exceed Free and Contributor limits.","仅当 Airplanes.live 已授权您的账户或 IP 用于商业方案时启用。此模式每 1.2 秒查询一次，可能超过免费和贡献者限额。","इसे केवल तभी चालू करें जब Airplanes.live ने आपके खाते या IP को बिज़नेस उपयोग के लिए अधिकृत किया हो। यह हर 1.2 सेकंड में पूछता है और मुफ़्त व योगदानकर्ता सीमा पार कर सकता है।","Actívalo solo si Airplanes.live ha autorizado tu cuenta o IP para uso Business. Consulta cada 1,2 segundos y puede superar los límites Free y Contributor.","Activez uniquement si Airplanes.live a autorisé votre compte ou IP pour l’offre Business. L’interrogation toutes les 1,2 secondes peut dépasser les limites Free et Contributor.","فعّله فقط إذا سمحت Airplanes.live لحسابك أو عنوان IP باستخدام خطة الأعمال. يتم الاستعلام كل 1.2 ثانية وقد يتجاوز حدود الخطتين المجانية والمساهمة.","শুধু Airplanes.live আপনার অ্যাকাউন্ট বা IP-কে বিজনেস ব্যবহারের অনুমতি দিলে চালু করুন। এটি প্রতি ১.২ সেকেন্ডে অনুরোধ করে এবং ফ্রি ও কন্ট্রিবিউটর সীমা ছাড়াতে পারে।","Ative somente se a Airplanes.live autorizou sua conta ou IP para uso Business. A consulta a cada 1,2 segundo pode exceder os limites Free e Contributor.","Включайте только при разрешении Airplanes.live для вашего аккаунта или IP на тарифе Business. Опрос каждые 1,2 секунды может превысить лимиты Free и Contributor.","Nur aktivieren, wenn Airplanes.live deinen Account oder deine IP für Business-Nutzung freigeschaltet hat. Die Abfrage erfolgt alle 1,2 Sekunden und kann Free- sowie Contributor-Limits überschreiten."}
            ,{"enable","Enable","启用","चालू करें","Activar","Activer","تفعيل","চালু করুন","Ativar","Включить","Aktivieren"}
            ,{"custom_alerts","Custom notifications","自定义通知","कस्टम सूचनाएँ","Notificaciones personalizadas","Notifications personnalisées","إشعارات مخصصة","কাস্টম বিজ্ঞপ্তি","Notificações personalizadas","Пользовательские уведомления","Eigene Benachrichtigungen"}
            ,{"add_rule","Add rule","添加规则","नियम जोड़ें","Añadir regla","Ajouter une règle","إضافة قاعدة","নিয়ম যোগ করুন","Adicionar regra","Добавить правило","Regel hinzufügen"}
            ,{"custom_squawk","Custom squawk code","自定义应答机代码","कस्टम स्क्वॉक कोड","Código squawk personalizado","Code squawk personnalisé","رمز سكواك مخصص","কাস্টম স্কোয়াক কোড","Código squawk personalizado","Свой код squawk","Eigener Squawk-Code"}
            ,{"low_level","Long low and level flight","长时间低空平飞","लंबी नीची और समतल उड़ान","Vuelo bajo y nivelado prolongado","Vol bas et en palier prolongé","طيران منخفض ومستوي لفترة طويلة","দীর্ঘ নিচু ও সমতল উড়ান","Voo baixo e nivelado prolongado","Долгий низкий горизонтальный полет","Langer niedriger Horizontalflug"}
            ,{"altitude_below","Altitude below","高度低于","ऊँचाई इससे कम","Altitud inferior a","Altitude inférieure à","ارتفاع أقل من","উচ্চতা এর নিচে","Altitude abaixo de","Высота ниже","Höhe unter"}
            ,{"speed_below","Speed below","速度低于","गति इससे कम","Velocidad inferior a","Vitesse inférieure à","سرعة أقل من","গতি এর নিচে","Velocidade abaixo de","Скорость ниже","Geschwindigkeit unter"}
            ,{"speed_above","Speed above","速度高于","गति इससे अधिक","Velocidad superior a","Vitesse supérieure à","سرعة أعلى من","গতি এর উপরে","Velocidade acima de","Скорость выше","Geschwindigkeit über"}
            ,{"rule_type","Rule type","规则类型","नियम प्रकार","Tipo de regla","Type de règle","نوع القاعدة","নিয়মের ধরন","Tipo de regra","Тип правила","Regeltyp"}
            ,{"disable","Disable","停用","बंद करें","Desactivar","Désactiver","تعطيل","বন্ধ করুন","Desativar","Отключить","Deaktivieren"}
            ,{"edit","Edit","编辑","संपादित करें","Editar","Modifier","تعديل","সম্পাদনা","Editar","Изменить","Bearbeiten"}
            ,{"delete","Delete","删除","हटाएँ","Eliminar","Supprimer","حذف","মুছুন","Excluir","Удалить","Löschen"}
            ,{"rule_name","Optional rule name","可选规则名称","वैकल्पिक नियम नाम","Nombre opcional","Nom facultatif","اسم اختياري للقاعدة","ঐচ্ছিক নিয়মের নাম","Nome opcional","Необязательное имя","Optionaler Regelname"}
            ,{"squawk_code","Four-digit squawk code","四位应答机代码","चार अंकों का स्क्वॉक कोड","Código squawk de cuatro dígitos","Code squawk à quatre chiffres","رمز سكواك من أربعة أرقام","চার সংখ্যার স্কোয়াক কোড","Código squawk de quatro dígitos","Четырехзначный код squawk","Vierstelliger Squawk-Code"}
            ,{"speed_knots","Speed in knots","速度（节）","गति नॉट में","Velocidad en nudos","Vitesse en nœuds","السرعة بالعقد","গতি নটে","Velocidade em nós","Скорость в узлах","Geschwindigkeit in Knoten"}
            ,{"altitude_feet","Altitude in feet","高度（英尺）","ऊँचाई फीट में","Altitud en pies","Altitude en pieds","الارتفاع بالقدم","উচ্চতা ফুটে","Altitude em pés","Высота в футах","Höhe in Fuß"}
            ,{"max_vertical_rate","Maximum vertical rate in ft/min","最大垂直速度（英尺/分钟）","अधिकतम ऊर्ध्वाधर दर फीट/मिनट","Velocidad vertical máxima en ft/min","Vitesse verticale maximale en ft/min","أقصى معدل رأسي قدم/دقيقة","সর্বোচ্চ উল্লম্ব হার ফুট/মিনিট","Taxa vertical máxima em ft/min","Макс. вертикальная скорость, фут/мин","Maximale Vertikalrate in ft/min"}
            ,{"duration_seconds","Minimum duration in seconds","最短持续时间（秒）","न्यूनतम अवधि सेकंड में","Duración mínima en segundos","Durée minimale en secondes","المدة الدنيا بالثواني","ন্যূনতম সময় সেকেন্ডে","Duração mínima em segundos","Минимальная длительность, с","Mindestdauer in Sekunden"}
            ,{"edit_rule","Configure notification rule","配置通知规则","सूचना नियम कॉन्फ़िगर करें","Configurar regla de notificación","Configurer la règle de notification","إعداد قاعدة الإشعار","বিজ্ঞপ্তির নিয়ম কনফিগার করুন","Configurar regra de notificação","Настроить правило уведомления","Benachrichtigungsregel konfigurieren"}
            ,{"invalid_rule","Check the entered values. Squawk codes use four digits from 0 to 7.","请检查输入值。应答机代码由四位 0 至 7 的数字组成。","दर्ज किए गए मान जाँचें। स्क्वॉक कोड में 0 से 7 तक चार अंक होते हैं।","Comprueba los valores. Los códigos squawk usan cuatro dígitos del 0 al 7.","Vérifiez les valeurs. Un code squawk comporte quatre chiffres de 0 à 7.","تحقق من القيم. يتكون رمز سكواك من أربعة أرقام من 0 إلى 7.","মানগুলো পরীক্ষা করুন। স্কোয়াক কোডে ০ থেকে ৭ পর্যন্ত চারটি সংখ্যা থাকে।","Verifique os valores. Códigos squawk usam quatro dígitos de 0 a 7.","Проверьте значения. Код squawk состоит из четырех цифр от 0 до 7.","Prüfe die Werte. Squawk-Codes bestehen aus vier Ziffern von 0 bis 7."}
    };

    private MapL10n() { }

    static String t(Context context, String key) {
        int index = languageIndex(L10n.language(context));
        for (String[] row : ROWS) if (row[0].equals(key)) return row[index];
        return key;
    }

    static JSONObject json(Context context) {
        JSONObject object = new JSONObject();
        int index = languageIndex(L10n.language(context));
        try {
            for (String[] row : ROWS) object.put(row[0], row[index]);
        } catch (Exception ignored) { }
        return object;
    }

    private static int languageIndex(String code) {
        if ("zh".equals(code)) return 2;
        if ("hi".equals(code)) return 3;
        if ("es".equals(code)) return 4;
        if ("fr".equals(code)) return 5;
        if ("ar".equals(code)) return 6;
        if ("bn".equals(code)) return 7;
        if ("pt".equals(code)) return 8;
        if ("ru".equals(code)) return 9;
        if ("de".equals(code)) return 10;
        return 1;
    }
}
