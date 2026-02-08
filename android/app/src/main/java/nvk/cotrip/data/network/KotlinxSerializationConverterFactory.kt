package nvk.cotrip.data.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.StringFormat
import kotlinx.serialization.serializer
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

class KotlinxSerializationConverterFactory private constructor(
    private val format: StringFormat,
    private val contentType: MediaType,
) : Converter.Factory() {

    @OptIn(ExperimentalSerializationApi::class)
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *> {
        val serializer = format.serializersModule.serializer(type)
        return Converter { body ->
            format.decodeFromString(serializer, body.string())
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<Annotation>,
        methodAnnotations: Array<Annotation>,
        retrofit: Retrofit,
    ): Converter<Any, RequestBody> {
        val serializer = format.serializersModule.serializer(type)
        return Converter { value ->
            val payload = format.encodeToString(serializer, value)
            payload.toRequestBody(contentType)
        }
    }

    companion object {
        fun create(format: StringFormat, contentType: MediaType): KotlinxSerializationConverterFactory {
            return KotlinxSerializationConverterFactory(format, contentType)
        }
    }
}
