package dev.catsradar.presentation.settings

import dev.catsradar.domain.update.FeedFailure
import dev.catsradar.domain.update.UpdateCheck

class UpdateStateMapper {

    fun checking(): UpdateState = UpdateState(UpdateStatus.Checking)

    fun map(check: UpdateCheck): UpdateState = UpdateState(
        status = when (check) {
            UpdateCheck.UpToDate -> UpdateStatus.UpToDate
            is UpdateCheck.Available -> UpdateStatus.Available(check.version.toString())
            is UpdateCheck.Failed -> UpdateStatus.Failed(check.reason.toToken())
        },
    )

    private fun FeedFailure.toToken(): UpdateFailure = when (this) {
        FeedFailure.OFFLINE -> UpdateFailure.OFFLINE
        FeedFailure.UNAVAILABLE -> UpdateFailure.SOURCE_UNAVAILABLE
        FeedFailure.UNREADABLE -> UpdateFailure.UNREADABLE_ANSWER
    }
}
