package com.github.jankoran90.showlyfin.data.uploader.di

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.domain.ProfileConfigGateway
import com.github.jankoran90.showlyfin.data.uploader.UploaderProfileConfigGateway
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.api.UploaderApi
import com.github.jankoran90.showlyfin.data.uploader.api.UploaderAuthInterceptor
import com.github.jankoran90.showlyfin.data.uploader.api.UploaderService
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UploaderModule {

    @Provides
    @Singleton
    @Named("retrofitUploader")
    fun providesUploaderRetrofit(
        @Named("okHttpBase") okHttpBase: OkHttpClient,
        @Named("traktPreferences") prefs: SharedPreferences,
        gson: Gson,
    ): Retrofit {
        val client = okHttpBase.newBuilder()
            .addInterceptor(UploaderAuthInterceptor(prefs))
            .build()
        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun providesUploaderRemoteDataSource(
        @Named("retrofitUploader") retrofit: Retrofit,
    ): UploaderRemoteDataSource = UploaderApi(retrofit.create(UploaderService::class.java))

    // Plan PROFILES Fáze 2 — backend gateway pro config balík (dependency inversion do core-data)
    @Provides
    @Singleton
    fun providesProfileConfigGateway(
        remote: UploaderRemoteDataSource,
        @Named("traktPreferences") prefs: SharedPreferences,
    ): ProfileConfigGateway = UploaderProfileConfigGateway(remote, prefs)
}
