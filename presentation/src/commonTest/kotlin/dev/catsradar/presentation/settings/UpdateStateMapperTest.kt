package dev.catsradar.presentation.settings

import dev.catsradar.domain.update.AppVersion
import dev.catsradar.domain.update.FeedFailure
import dev.catsradar.domain.update.ReleasePackage
import dev.catsradar.domain.update.UpdateCheck
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateStateMapperTest {

    private val mapper = UpdateStateMapper()

    @Test
    fun `a check in progress takes the button away`() {
        assertEquals(UpdateState(UpdateStatus.Checking), mapper.checking())
        assertEquals(false, mapper.checking().checkEnabled)
    }

    @Test
    fun `an available release names its version`() {
        val check = UpdateCheck.Available(AppVersion.parse("v1.5.0-beta")!!, ReleasePackage("https://x/a.apk", 1, null))

        assertEquals(UpdateState(UpdateStatus.Available("1.5.0-beta")), mapper.map(check))
    }

    @Test
    fun `an up-to-date app says so`() {
        assertEquals(UpdateState(UpdateStatus.UpToDate), mapper.map(UpdateCheck.UpToDate))
    }

    @Test
    fun `each way the feed fails has its own message, and none share one`() {
        val tokens = FeedFailure.entries.associateWith { reason ->
            (mapper.map(UpdateCheck.Failed(reason)).status as UpdateStatus.Failed).reason
        }

        assertEquals(
            mapOf(
                FeedFailure.OFFLINE to UpdateFailure.OFFLINE,
                FeedFailure.UNAVAILABLE to UpdateFailure.SOURCE_UNAVAILABLE,
                FeedFailure.UNREADABLE to UpdateFailure.UNREADABLE_ANSWER,
            ),
            tokens,
        )
        assertEquals(tokens.size, tokens.values.toSet().size)
    }
}
