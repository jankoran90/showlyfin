package com.github.jankoran90.showlyfin.core.domain

import java.security.MessageDigest

/**
 * Hash app-login PINu profilu (Plan WARDEN W1). Ukládá se `ProfileEntity.loginPinHash`, ne plaintext.
 * SHA-256 hex; PIN je krátký rodinný kód (ne tajné heslo) — hash brání jen náhodnému přečtení v DB.
 */
object PinHasher {
    fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(pin.trim().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** true = [pin] odpovídá uloženému [hash] (null/blank hash = bez PINu = nikdy neověřuje). */
    fun verify(pin: String, hash: String?): Boolean =
        !hash.isNullOrBlank() && hash == hash(pin)
}
