package dev.catsradar.app.di

import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class KoinModulesTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `presentation module resolves`() {
        presentationModule.verify()
    }
}
