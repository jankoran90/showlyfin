package com.github.jankoran90.showlyfin.data.uploader.model

import com.google.gson.annotations.SerializedName

/**
 * DROPSHIP F2 — odpověď `POST /api/audiobook/upload`. Backend nahraje audio soubory/archiv do
 * cílové ABS knihovny, rozbalí archivy, zmerguje do jedné složky, spustí ABS scan a případně
 * Audible enrich (auto_match). `scan.status` = HTTP kód ABS scan volání (200 = ok).
 */
data class AudiobookUploadResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("library_id") val libraryId: String? = null,
    @SerializedName("folder") val folder: String? = null,
    @SerializedName("tracks") val tracks: Int = 0,
    @SerializedName("item_id") val itemId: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("author") val author: String? = null,
    @SerializedName("detected") val detected: AudiobookUploadDetected? = null,
    @SerializedName("scan") val scan: AudiobookUploadScan? = null,
    @SerializedName("enrich") val enrich: AudiobookUploadEnrich? = null,
)

data class AudiobookUploadDetected(
    @SerializedName("title") val title: String? = null,
    @SerializedName("author") val author: String? = null,
)

data class AudiobookUploadScan(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("status") val status: Int = 0,
)

data class AudiobookUploadEnrich(
    @SerializedName("matched") val matched: Boolean = false,
    @SerializedName("error") val error: String? = null,
)
