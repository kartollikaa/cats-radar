package dev.catsradar.domain.model

import dev.catsradar.domain.testing.encounterFixture
import dev.catsradar.domain.testing.inShotOf
import dev.catsradar.domain.testing.withPhoto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ShotsTest {

    @Test
    fun `the cats of one shot group together oldest first, in the order the shots appear`() {
        val tally = encounterFixture("tally", BASE + 2.minutes)
        val shotFirst = shotCat("m", created = BASE)
        val shotSecond = shotCat("z", created = BASE + 1.seconds)
        val shotThird = shotCat("a", created = BASE + 2.seconds)
        val lone = encounterFixture("lone", BASE - 1.minutes).withPhoto()
        val otherCover = encounterFixture("other", BASE).withPhoto().let { cat ->
            cat.copy(photos = cat.photos + shotFirst.photos.single().copy(id = "copy", encounterId = "other"))
        }

        val groups = listOf(tally, shotThird, lone, shotFirst, otherCover, shotSecond).groupedByShot()

        assertEquals(
            listOf(listOf("tally"), listOf("m", "z", "a"), listOf("lone"), listOf("other")),
            groups.map { group -> group.map { it.id } },
        )
    }

    private fun shotCat(id: String, created: Instant): Encounter =
        encounterFixture(id, BASE).copy(createdAt = created).withPhoto().inShotOf("m")

    private companion object {
        val BASE = Instant.parse("2026-09-22T10:00:00Z")
    }
}
