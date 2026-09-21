package com.pitchpop

/** Thread-confined SpeexDSP echo cancellation and residual echo suppression. */
class SoftwareEchoCanceller : AutoCloseable {
    companion object {
        const val FRAME_SIZE = 320
        const val PROCESSING_DELAY_MS = 20L // Speex preprocessor's overlap-add frame delay.
        init { System.loadLibrary("pitchpop_echo") }
    }
    private var handle = nativeCreate().also { check(it != 0L) { "エコー除去を開始できませんでした。" } }
    fun process(microphone: ShortArray, reference: ShortArray, cleaned: ShortArray) {
        check(handle != 0L)
        require(microphone.size == FRAME_SIZE && reference.size == FRAME_SIZE && cleaned.size == FRAME_SIZE)
        nativeProcess(handle, microphone, reference, cleaned)
    }
    fun reset() { close(); handle = nativeCreate().also { check(it != 0L) } }
    override fun close() { if (handle != 0L) { nativeDestroy(handle); handle = 0 } }
    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeProcess(handle: Long, microphone: ShortArray, reference: ShortArray, cleaned: ShortArray)
}
