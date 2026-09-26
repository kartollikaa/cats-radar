package dev.catsradar.app.testing

import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat

internal class RecordingActivityResultRegistry : ActivityResultRegistry(), ActivityResultRegistryOwner {

    class Launch(val requestCode: Int, val contract: ActivityResultContract<*, *>, val input: Any?)

    val launches = mutableListOf<Launch>()

    override val activityResultRegistry: ActivityResultRegistry get() = this

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        launches += Launch(requestCode, contract, input)
    }

    fun <O> answer(result: O) {
        dispatchResult(launches.last().requestCode, result)
    }
}
