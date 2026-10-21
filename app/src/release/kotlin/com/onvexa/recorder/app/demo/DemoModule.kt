package com.onvexa.recorder.app.demo

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

class NoOpDemoSeeder @Inject constructor() : DemoSeeder {
    override val available: Boolean = false
    override suspend fun seedIfFirstRun() = Unit
    override suspend fun seed() = Unit
    override suspend fun clear() = Unit
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DemoModule {
    @Binds
    abstract fun bindSeeder(impl: NoOpDemoSeeder): DemoSeeder
}
