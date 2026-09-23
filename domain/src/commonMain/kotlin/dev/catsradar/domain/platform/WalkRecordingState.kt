package dev.catsradar.domain.platform

/**
 * Whether a walk's route is being recorded, kept across process death and reboot: still marked at
 * the next start means the recording was cut off rather than stopped.
 */
interface WalkRecordingState {
    val recording: Boolean

    fun markRecording()

    fun markStopped()
}
