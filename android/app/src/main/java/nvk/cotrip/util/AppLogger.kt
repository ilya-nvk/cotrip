package nvk.cotrip.util

import android.util.Log
import nvk.cotrip.BuildConfig

object AppLogger {
    private const val MAX_TAG_LENGTH = 23

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag.safeTag(), message)
        }
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag.safeTag(), message)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.w(tag.safeTag(), message)
        } else {
            Log.w(tag.safeTag(), message, throwable)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.e(tag.safeTag(), message)
        } else {
            Log.e(tag.safeTag(), message, throwable)
        }
    }

    private fun String.safeTag(): String {
        return if (length <= MAX_TAG_LENGTH) this else take(MAX_TAG_LENGTH)
    }
}
