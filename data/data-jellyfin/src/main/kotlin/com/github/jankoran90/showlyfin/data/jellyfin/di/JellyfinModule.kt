package com.github.jankoran90.showlyfin.data.jellyfin.di

import android.content.Context
import com.github.jankoran90.showlyfin.data.jellyfin.util.CoroutineContextApiClientFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.android.androidDevice
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object JellyfinModule {

    @Provides
    @Singleton
    fun providesJellyfinClientInfo(): ClientInfo = ClientInfo(
        name = "Showlyfin",
        version = "0.1",
    )

    @Provides
    @Singleton
    fun providesJellyfinDeviceInfo(
        @ApplicationContext context: Context,
    ): DeviceInfo = androidDevice(context)

    @Provides
    @Singleton
    fun providesJellyfinApiClientFactory(): CoroutineContextApiClientFactory =
        CoroutineContextApiClientFactory(OkHttpFactory(OkHttpClient.Builder().build()))

    @Provides
    @Singleton
    fun providesJellyfin(
        @ApplicationContext context: Context,
        clientInfo: ClientInfo,
        deviceInfo: DeviceInfo,
        apiClientFactory: CoroutineContextApiClientFactory,
    ): Jellyfin = createJellyfin {
        this.context = context
        this.clientInfo = clientInfo
        this.deviceInfo = deviceInfo
        this.apiClientFactory = apiClientFactory
        this.socketConnectionFactory = apiClientFactory
        minimumServerVersion = Jellyfin.minimumVersion
    }

    @Provides
    @Singleton
    fun providesApiClient(jellyfin: Jellyfin): ApiClient = jellyfin.createApi()
}
