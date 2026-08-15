package com.notifplus.di

import com.notifplus.data.repository.NotificationAccessRepositoryImpl
import com.notifplus.data.repository.NotificationListenerHealthRepositoryImpl
import com.notifplus.domain.repository.NotificationAccessRepository
import com.notifplus.domain.repository.NotificationListenerHealthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AccessRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindNotificationAccessRepository(impl: NotificationAccessRepositoryImpl): NotificationAccessRepository

    @Binds
    @Singleton
    abstract fun bindNotificationListenerHealthRepository(
        impl: NotificationListenerHealthRepositoryImpl,
    ): NotificationListenerHealthRepository
}
