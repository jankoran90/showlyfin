package com.github.jankoran90.showlyfin.data.uploader.api

import com.github.jankoran90.showlyfin.data.uploader.model.OpsHeartbeatBody
import com.github.jankoran90.showlyfin.data.uploader.model.OpsHistoryResponse
import com.github.jankoran90.showlyfin.data.uploader.model.OpsOverviewResponse
import com.github.jankoran90.showlyfin.data.uploader.model.OpsSourcesResponse
import com.github.jankoran90.showlyfin.data.uploader.model.OpsSweepResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * PROVOZ (SHW-114) — `routes/ops.py`. Vlastní rozhraní vedle [UploaderService]: ta má přes 170 řádků
 * a míchat do ní další doménu by z ní udělala smetiště.
 */
interface OpsService {
    @GET suspend fun overview(@Url url: String, @Header("Cookie") cookie: String): OpsOverviewResponse

    @GET suspend fun sources(@Url url: String, @Header("Cookie") cookie: String): OpsSourcesResponse

    @GET suspend fun history(@Url url: String, @Header("Cookie") cookie: String): OpsHistoryResponse

    @POST suspend fun sweep(@Url url: String, @Header("Cookie") cookie: String): OpsSweepResponse

    @POST suspend fun verify(@Url url: String, @Header("Cookie") cookie: String): Response<ResponseBody>

    @POST suspend fun setPolicy(@Url url: String, @Header("Cookie") cookie: String): Response<ResponseBody>

    @DELETE suspend fun removeSource(@Url url: String, @Header("Cookie") cookie: String): Response<ResponseBody>

    @POST suspend fun heartbeat(
        @Url url: String,
        @Header("Cookie") cookie: String,
        @Body body: OpsHeartbeatBody,
    ): Response<ResponseBody>

    @POST suspend fun stop(@Url url: String, @Header("Cookie") cookie: String): Response<ResponseBody>
}
