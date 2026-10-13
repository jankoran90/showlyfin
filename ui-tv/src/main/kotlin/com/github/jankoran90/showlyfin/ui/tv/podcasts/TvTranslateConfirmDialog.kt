package com.github.jankoran90.showlyfin.ui.tv.podcasts

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode

/**
 * AMFITEÁTR (FLM-05) F2 — potvrzení „Přeložit do češtiny" (user: „až na klik s potvrzením").
 * Vyvolá ho podržení OK na dílu YouTube. Překlad pak běží na serveru i po zavření appky.
 */
@Composable
fun TvTranslateConfirmDialog(
    episode: SourceEpisode,
    alreadyTranslated: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (alreadyTranslated) "Český překlad je hotový" else "Přeložit do češtiny?") },
        text = {
            Text(
                if (alreadyTranslated) {
                    "„${episode.title}“ už má české titulky. Při přehrání se nasadí samy."
                } else {
                    "„${episode.title}“ se přeloží na pozadí. Průběh a odhad času uvidíš vpravo nahoře " +
                        "na každé obrazovce, můžeš mezitím dělat cokoli jiného. Překlad čerpá kvótu mozku."
                },
            )
        },
        confirmButton = {
            if (!alreadyTranslated) {
                TextButton(onClick = onConfirm, modifier = Modifier.tvFocusBorder()) { Text("Přeložit") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.tvFocusBorder()) {
                Text(if (alreadyTranslated) "Zavřít" else "Zrušit")
            }
        },
    )
}
