package com.example.ourmemories.utils

import java.util.Locale

object VersionLogUtils {

    /**
     * Возвращает карту обновлений (Версия -> Описание) в зависимости от языка системы.
     */
    fun getChangelog(): LinkedHashMap<String, String> {
        val language = Locale.getDefault().language

        return when (language) {
            "en" -> getEnglishLog()
            "ky" -> getKyrgyzLog()
            "tk" -> getTurkmenLog()
            else -> getRussianLog()
        }
    }

    private fun getRussianLog(): LinkedHashMap<String, String> {
        val map = LinkedHashMap<String, String>()

        map["V 0.1.7 (Текущая)"] = """
            🔥 Главные новинки:
            • Анимации: Добавлены анимации на экран дерева, галереи, конфетти при выполнении желания, пульсация сердца и анимация списка желаний.
            • Виджет: Добавлена отправка «Живых фото» на экран партнера.
            • Wishlist 2.0: Категории желаний (Кино 🎬, Еда 🍔...), новый дизайн и удобное добавление.
            • Splash Screen: Добавлен новый экран приветствия при входе в приложение.
            • Настройки: Отдельный экран настроек, смена языка (RU/EN/KG/TK) и темы.

            ❤️ Партнер и Профиль:
            • Отображение последней активности («Был в сети»).
            • День рождения и Знак Зодиака партнера.
            • Статистика очков «Дерева Любви» у партнера.
            • Быстрые реакции (Привет) в меню партнера.

            🛠 Улучшения:
            • Редизайн кнопок и диалоговых окон.
            • Удаление конкретных фото из альбома.
            • Помощник настройки «Автозапуска» (исправление виджетов на Xiaomi/Samsung).
            • Прогресс-бар при загрузке фото.
            • Исправлен цвет фона в выборе даты.
        """.trimIndent()

        map["V 0.1.Архив"] = """
            V 0.1.6
            • Добавлена статистика
            • Добавлены уведомления
            • Удалён календарь
            • Добавлен список желаний
            • Свайп для отметки желания
            • Добавлено дерево Любви
            • Добавлена тёмная тема
            • Добавлен виджет на рабочий стол
            
            V 0.1.5
            • Добавлены записки для партнёра
            • Добавлены статусы (эмодзи и текст)
            • Новый дизайн выбора даты
            • Возможность выбора обложки альбома
            • Исправлена загрузка фото в галерее
            • Обновлены экраны входа и регистрации
            
            V 0.1.4
            • Анимация сердца на главном экране
            • Кнопка 'Смотреть все' в ленте
            • Политика конфиденциальности
            • Долгое нажатие на фото в галерее
            • Защита от случайного выхода
            
            V 0.1.3
            • Обновлен дизайн профиля
            • Исправлены баги календаря
            
            V 0.1.2
            • Финальный дизайн кнопок
            • Рабочая регистрация
            
            V 0.1.1
            • Экран профиля
            • Автовход и выход
            • Смена имени и фото
        """.trimIndent()

        return map
    }

    private fun getEnglishLog(): LinkedHashMap<String, String> {
        val map = LinkedHashMap<String, String>()

        map["V 0.1.7 (Current)"] = """
            🔥 Key Features:
            • Animations: Added animations to the tree screen, gallery, confetti on wish completion, pulsating heart, and wishlist.
            • Widget: Added "Live Photo" sending to partner's home screen.
            • Wishlist 2.0: Wish categories (Movies 🎬, Food 🍔...), new design and easy adding.
            • Splash Screen: Added a new welcome screen on app launch.
            • Settings: Separate settings screen, language (RU/EN/KG/TK) and theme switching.

            ❤️ Partner & Profile:
            • Last active status ("Online").
            • Partner's Birthday and Zodiac Sign.
            • Partner's "Love Tree" points statistics.
            • Quick reactions (Hello) in partner menu.

            🛠 Improvements:
            • Redesign of buttons and dialogs.
            • Delete specific photos from albums.
            • "Auto-start" setup helper (fixing widgets on Xiaomi/Samsung).
            • Progress bar during photo upload.
            • Fixed background color in date picker.
        """.trimIndent()

        map["V 0.1.Archive"] = """
            V 0.1.6
            • Added statistics
            • Added notifications
            • Removed calendar
            • Added wishlist
            • Swipe to complete wish
            • Added Love Tree
            • Added Dark Theme
            • Added Home Screen Widget
            
            V 0.1.5
            • Added partner notes
            • Added statuses (emoji & text)
            • New date picker design
            • Ability to choose album cover
            • Fixed photo loading in gallery
            • Updated login and registration screens
            
            V 0.1.4
            • Heart animation on main screen
            • 'See All' button in feed
            • Privacy Policy
            • Long press on photo in gallery
            • Prevent accidental exit
            
            V 0.1.3
            • Updated profile design
            • Fixed calendar bugs
            
            V 0.1.2
            • Final button design
            • Working registration
            
            V 0.1.1
            • Profile screen
            • Auto-login and logout
            • Change name and photo
        """.trimIndent()

        return map
    }

    private fun getKyrgyzLog(): LinkedHashMap<String, String> {
        val map = LinkedHashMap<String, String>()

        map["V 0.1.7 (Азыркы)"] = """
            🔥 Жаңылыктар:
            • Анимациялар: Дарак экранына, галереяга, каалоо аткарылганда конфетти, жүрөктү басканда жана каалоолор тизмесине анимациялар кошулду.
            • Виджет: Өнөктөштүн экранына «Жандуу сүрөт» жөнөтүү кошулду.
            • Wishlist 2.0: Каалоолор категориялары (Кино 🎬, Тамак 🍔...), жаңы дизайн.
            • Splash Screen: Тиркемеге киргенде жаңы саламдашуу экраны кошулду.
            • Жөндөөлөр: Тилди (RU/EN/KG/TK) жана теманы өзгөртүү үчүн өзүнчө экран.

            ❤️ Өнөктөш жана Профиль:
            • Акыркы активдүүлүк («Тармакта болгон»).
            • Өнөктөштүн Туулган күнү жана Зодиак белгиси.
            • Өнөктөштүн «Сүйүү Дарагы» упайлары.
            • Ыкчам реакциялар (Салам) менюда.

            🛠 Оңдоолор:
            • Баскычтардын жана диалогдордун дизайны жаңыртылды.
            • Альбомдон айрым сүрөттөрдү өчүрүү.
            • «Автозапуск» жардамчысы (Xiaomi/Samsung виджеттерин оңдоо).
            • Сүрөт жүктөө учурунда прогресс-бар.
            • Дата тандоодогу фондун түсү оңдолду.
        """.trimIndent()

        map["V 0.1.Архив"] = """
            V 0.1.6
            • Статистика кошулду
            • Билдирүүлөр кошулду
            • Календарь алынып салынды
            • Каалоолор тизмеси кошулду
            • Каалоону аткаруу үчүн свайп
            • Сүйүү дарагы кошулду
            • Караңгы тема кошулду
            • Виджет кошулду
            
            V 0.1.5
            • Өнөктөш үчүн жазуулар
            • Статустар (эмодзи жана текст)
            • Дата тандоонун жаңы дизайны
            • Альбомдун мукабасын тандоо мүмкүнчүлүгү
            • Галереядагы сүрөт жүктөө оңдолду
            • Кирүү жана катталуу экрандары жаңыртылды
            
            V 0.1.4
            • Башкы экрандагы жүрөк анимациясы
            • Лентада 'Баарын көрүү' баскычы
            • Купуялык саясаты
            • Галереядагы сүрөттү басып туруу
            • Кокустан чыгып кетүүдөн коргоо
            
            V 0.1.3
            • Профиль дизайны жаңыртылды
            • Календарь каталары оңдолду
            
            V 0.1.2
            • Баскычтардын акыркы дизайны
            • Иштеген катталуу
            
            V 0.1.1
            • Профиль экраны
            • Авто-кирүү жана чыгуу
            • Аты жана сүрөтүн өзгөртүү
        """.trimIndent()

        return map
    }

    private fun getTurkmenLog(): LinkedHashMap<String, String> {
        val map = LinkedHashMap<String, String>()

        map["V 0.1.7 (Häzirki)"] = """
            🔥 Esasy täzelikler:
            • Animasiýalar: Darag ekranyna, galereýa, isleg ýerine ýetirilende konfetti, ýürek urýan wagt basylanda we islegler sanawyna animasiýalar goşuldy.
            • Wijet: Ýoldaşyň ekranyna «Janly surat» ibermek goşuldy.
            • Wishlist 2.0: Isleg kategoriýalary (Kino 🎬, Iýmit 🍔...), täze dizaýn.
            • Splash Screen: Programma gireniňizde täze garşylaýyş ekrany goşuldy.
            • Sazlamalar: Dili (RU/EN/KG/TK) we temany üýtgetmek üçin aýratyn ekran.

            ❤️ Ýoldaş we Profil:
            • Soňky işjeňlik («Online boldy»).
            • Ýoldaşyň Doglan güni we Zodiak alamaty.
            • Ýoldaşyň «Söýgi Daragy» utuklary.
            • Çalt reaksiýalar (Salam) menýuda.

            🛠 Gowulandyrmalar:
            • Düwme we dialoglaryň dizaýny täzelendi.
            • Albomdan aýratyn suratlary pozmak.
            • «Awtoçykaryş» kömekçisi (Xiaomi/Samsung üçin).
            • Surat ýüklenende progress-bar.
            • Sene saýlananda fon reňki düzedildi.
        """.trimIndent()

        map["V 0.1.Arhiw"] = """
            V 0.1.6
            • Statistika goşuldy
            • Habarnamalar goşuldy
            • Senenama aýyryldy
            • Islegler sanawy goşuldy
            • Islegi ýerine ýetirmek üçin swaýp
            • Söýgi daragy goşuldy
            • Garaňky tema goşuldy
            • Wijet goşuldy
            
            V 0.1.5
            • Ýoldaş üçin hatlar
            • Statuslar (emoji we tekst)
            • Sene saýlawynyň täze dizaýny
            • Albomyň daşyny (obloşka) saýlamak
            • Galereýada surat ýüklemek düzedildi
            • Giriş we hasaba alyş ekranlary täzelendi
            
            V 0.1.4
            • Baş ekrandaky ýürek animasiýasy
            • Lentada 'Hemmesini görmek' düwmesi
            • Gizlinlik syýasaty
            • Galereýada surata basyp durmak
            • Tötänleýin çykmakdan gorag
            
            V 0.1.3
            • Profil dizaýny täzelendi
            • Senenama ýalňyşlyklary düzedildi
            
            V 0.1.2
            • Düwmeleriň soňky dizaýny
            • Işleýän hasaba alyş
            
            V 0.1.1
            • Profil ekrany
            • Awto-giriş we çykmak
            • Ady we suraty üýtgetmek
        """.trimIndent()

        return map
    }
}