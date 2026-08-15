package com.notifplus.di

import android.content.Context
import androidx.room.Room
import com.notifplus.data.local.NotificationDao
import com.notifplus.data.local.NotifPlusDatabase
import com.notifplus.data.local.MIGRATION_1_2
import com.notifplus.data.local.MIGRATION_2_3
import com.notifplus.data.repository.AutoDismissRepositoryImpl
import com.notifplus.data.repository.NotificationRepositoryImpl
import com.notifplus.data.repository.RetentionRepositoryImpl
import com.notifplus.domain.repository.AutoDismissRepository
import com.notifplus.domain.repository.NotificationRepository
import com.notifplus.domain.repository.RetentionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NotifPlusDatabase =
        Room.databaseBuilder(context, NotifPlusDatabase::class.java, "notifplus.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun provideNotificationDao(database: NotifPlusDatabase): NotificationDao = database.notificationDao()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository

    @Binds
    @Singleton
    abstract fun bindAutoDismissRepository(impl: AutoDismissRepositoryImpl): AutoDismissRepository

    @Binds
    @Singleton
    abstract fun bindRetentionRepository(impl: RetentionRepositoryImpl): RetentionRepository
}
