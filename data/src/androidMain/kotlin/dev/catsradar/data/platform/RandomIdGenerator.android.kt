package dev.catsradar.data.platform

import dev.catsradar.domain.platform.IdGenerator
import java.util.UUID

class RandomIdGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
