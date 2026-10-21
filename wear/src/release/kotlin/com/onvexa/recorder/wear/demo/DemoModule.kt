package com.onvexa.recorder.wear.demo

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

class NoOpWatchDemoSeeder @Inject constructor() : WatchDemoSeeder {
    override suspend fun seedIfFirstRun() = Unit
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DemoModule {
    @Binds
    abstract fun bindSeeder(impl: NoOpWatchDemoSeeder): WatchDemoSeeder
}
