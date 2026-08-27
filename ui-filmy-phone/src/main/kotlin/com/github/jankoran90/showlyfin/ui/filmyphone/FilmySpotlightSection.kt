package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.jankoran90.showlyfin.core.appservices.services.KEY_SPOTLIGHT_NOTIFY
import kotlinx.coroutines.launch

/**
 * SPOTLIGHT (FLM-02) — blok „Sledování tvůrců" v Nastavení Filmy.
 *
 * Parita nastavení = DoD: featura s nastavitelným chováním musí mít blok v Nastavení, který nelže.
 * Ovládá se upozornění (systémová notifikace) a jde ručně vyvolat kontrola, aby uživatel nemusel
 * čekat na pátek. Samo sledování se zapíná hvězdičkou u tvůrce, ne přepínačem tady.
 */
@Composable
fun FilmySpotlightSection(vm: FilmyNovinkyViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("trakt_prefs", android.content.Context.MODE_PRIVATE) }
    var notify by remember { mutableStateOf(prefs.getBoolean(KEY_SPOTLIGHT_NOTIFY, true)) }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SettingSectionTitle("Sledování tvůrců")
        Text(
            text = "Tvůrce začneš sledovat hvězdičkou v jeho filmografii (sekce Tvůrci). Jednou týdně " +
                "v pátek se podíváme, jestli mu nevyšel nový film nebo seriál — najdeš je v sekci Novinky.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingSwitchRow(
            title = "Upozorňovat na novinky",
            subtitle = "Systémové upozornění, když sledovanému tvůrci vyjde nový titul. " +
                "Dětský profil upozornění nedostává (tvůrce si ukládat může).",
            checked = notify,
            onCheckedChange = {
                notify = it
                prefs.edit().putBoolean(KEY_SPOTLIGHT_NOTIFY, it).apply()
            },
        )
        OutlinedButton(
            enabled = !checking,
            onClick = {
                checking = true
                result = null
                scope.launch {
                    vm.refresh()
                    checking = false
                    result = "Novinky načteny — najdeš je v sekci Novinky."
                }
            },
        ) {
            Text(if (checking) "Kontroluji…" else "Zkontrolovat novinky teď")
        }
        result?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
