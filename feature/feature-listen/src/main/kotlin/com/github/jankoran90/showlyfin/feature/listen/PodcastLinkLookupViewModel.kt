package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import com.github.jankoran90.showlyfin.feature.listen.player.PodcastLinkStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * WEFT (SHW-75/W2): lehký lookup nad [PodcastLinkStore] pro navigaci v app shellu (ShowlyfinPhoneApp).
 * Když je zdroj členem propojeného pořadu (audio RSS + video YouTube = TWINE), proklik z Timeline i
 * klik na cover v přehrávači (PERCH) má mířit na SLOUČENOU obrazovku, ne na původní audio/video.
 * Singleton store → tato (Activity-scoped) instance vidí stejná propojení jako sekce Poslechu.
 */
@HiltViewModel
class PodcastLinkLookupViewModel @Inject constructor(
    private val linkStore: PodcastLinkStore,
) : ViewModel() {

    /**
     * Propojený pořad, do kterého zdroj patří — nebo null. [type] = "rss"/"youtube", [ref] = feedUrl/handle.
     * Nejdřív přesný klíč `type:ref`, jako pojistka i jen podle `ref` (kdyby se typ lišil).
     */
    fun groupFor(type: String, ref: String): PodcastLinkStore.LinkGroup? =
        linkStore.groupForKey(linkStore.key(type, ref)) ?: linkStore.groupForRef(ref)
}
