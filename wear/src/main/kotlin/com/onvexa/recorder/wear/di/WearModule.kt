package com.onvexa.recorder.wear.di

import com.onvexa.recorder.wear.link.WearPhoneLink
import com.onvexa.recorder.wear.link.PhoneLink
import com.onvexa.recorder.wear.recorder.AndroidRecorderController
import com.onvexa.recorder.wear.recorder.RecorderController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WearModule {
    @Binds @Singleton
    abstract fun bindRecorder(impl: AndroidRecorderController): RecorderController

    @Binds @Singleton
    abstract fun bindPhoneLink(impl: WearPhoneLink): PhoneLink
}
