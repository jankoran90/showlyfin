package com.github.jankoran90.showlyfin.data.uploader.di

import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.api.UploaderApi
import com.github.jankoran90.showlyfin.data.uploader.api.UploaderService
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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
        gson: Gson,
    ): Retrofit = Retrofit.Builder()
        .baseUrl("http://localhost/")
        .client(okHttpBase)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    @Provides
    @Singleton
    fun providesUploaderRemoteDataSource(
        @Named("retrofitUploader") retrofit: Retrofit,
    ): UploaderRemoteDataSource = UploaderApi(retrofit.create(UploaderService::class.java))
}
