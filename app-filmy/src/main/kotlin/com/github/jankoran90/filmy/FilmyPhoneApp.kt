package com.github.jankoran90.filmy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CELLULOID (SHW-98) M1.2 — tenký telefonní placeholder appky „Filmy".
 *
 * Záměrně minimální: plná telefonní vrstva ve stylu TV (sidebar, sekce-jako-taby, view-modes, karta
 * detailu, Filmotéka grid) se staví ve Fázi 2 v modulu `:ui-filmy-phone`. Tady jen branding F na AMOLED
 * pozadí, aby launch na telefonu nespadl a bylo vidět, že jde o samostatnou appku. TV běží už teď plně.
 *
 * Bez tokenů z `core-theme` (ještě není extrahováno — Fáze 4); barvy jsou dočasné konstanty kánonu.
 */
private val AmoledBlack = Color(0xFF000000)
private val Amber = Color(0xFFFF7A1A)
private val SoftGray = Color(0xFFB0B0B0)

@Composable
fun FilmyPhoneApp() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            // Logo F v kánon barvě (amber na tmavém rámečku) — dočasně inline, později přes token systém.
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF141414)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "F",
                    color = Amber,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Text(
                text = "Filmy",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Telefonní verze se staví.\nNa televizi appka běží naplno.",
                color = SoftGray,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
