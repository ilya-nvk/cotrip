package nvk.cotrip.data.network

import retrofit2.HttpException
import retrofit2.Response

fun Response<Unit>.requireSuccess() {
    if (!isSuccessful) {
        throw HttpException(this)
    }
}
