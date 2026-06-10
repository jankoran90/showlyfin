package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Plan FUSE — F0: jediný zdroj pravdy o tom, na jakém zařízení běžíme.
 *
 * Telefonní obrazovky (moduly `feature`) se renderují i na TV; jediný rozdíl je chrome (bottom bar vs.
 * fokus rail), fokusovatelnost prvků (D-pad) a overscan. Místo `packageManager.hasSystemFeature`
 * roztroušeného po UI čteme [LocalFormFactor] → každý composable se umí přizpůsobit.
 *
 * `MainActivity` poskytne hodnotu jednou nahoře (`PHONE` default, `TV` pro leanback).
 */
enum class FormFactor { PHONE, TV }

/**
 * Default `PHONE` — drtivá většina obrazovek běží na telefonu a chceme, aby preview/testy bez
 * explicitního provideru daly telefonní chování.
 */
val LocalFormFactor = staticCompositionLocalOf { FormFactor.PHONE }

/** Pohodlná zkratka pro `LocalFormFactor.current == FormFactor.TV` ve větvení chování. */
@Composable
@ReadOnlyComposable
fun isTvFormFactor(): Boolean = LocalFormFactor.current == FormFactor.TV
