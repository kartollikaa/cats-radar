package dev.catsradar.data.db

import android.content.Context
import androidx.room3.Room

internal fun buildInMemoryCatsDatabase(context: Context): TestCatsDatabase =
    Room.inMemoryDatabaseBuilder<TestCatsDatabase>(context).withBundledDriver().build()
