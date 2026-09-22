package dev.catsradar.data.db

import android.content.Context
import androidx.room3.Room

fun buildInMemoryCatsDatabase(context: Context): CatsDatabase =
    Room.inMemoryDatabaseBuilder<CatsDatabase>(context).withBundledDriver().build()
