package com.onvexa.recorder.wear.di

import com.onvexa.recorder.wear.link.FakePhoneLink
import com.onvexa.recorder.wear.link.PhoneLink
import com.onvexa.recorder.wear.recorder.FakeRecorderController
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
    abstract fun bindRecorder(impl: FakeRecorderController): RecorderController

    @Binds @Singleton
    abstract fun bindPhoneLink(impl: FakePhoneLink): PhoneLink
}
