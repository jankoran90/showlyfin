package com.github.jankoran90.showlyfin.core.domain

/**
 * Normalizace serverové URL z uživatelského vstupu — **dovolí psát BEZ schématu** (`video.jankoran.cz`
 * → `https://video.jankoran.cz`). Ořeže mezery, úvodní i koncová lomítka. Prázdné nechá prázdné.
 *
 * Jeden zdroj pravdy pro celou app (dřív 5 kopií, jedna z nich u ABS loginu scheme nepřidávala → bare
 * host padal; web navíc ukládal `/video.jankoran.cz` s úvodním lomítkem). Volá se na všech místech,
 * kde uživatel zadává URL serveru (JF setup, ABS login, admin editor creds, hlavní login, applier).
 */
fun normalizeServerUrl(raw: String): String {
    var t = raw.trim()
    while (t.startsWith("/")) t = t.substring(1)
    t = t.trimEnd('/')
    if (t.isEmpty() || t.startsWith("http://") || t.startsWith("https://")) return t
    return "https://$t"
}
