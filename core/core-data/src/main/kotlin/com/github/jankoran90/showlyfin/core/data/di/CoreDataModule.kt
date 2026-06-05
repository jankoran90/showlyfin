package com.github.jankoran90.showlyfin.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.github.jankoran90.showlyfin.core.data.ShowlyfinDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "showlyfin_prefs")

@Module
@InstallIn(SingletonComponent::class)
object CoreDataModule {

    @Provides
    @Singleton
    fun providesDatabase(
        @ApplicationContext context: Context,
    ): ShowlyfinDatabase = Room.databaseBuilder(
        context,
        ShowlyfinDatabase::class.java,
        "showlyfin.db",
    ).build()

    @Provides
    @Singleton
    fun providesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.dataStore
}
