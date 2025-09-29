package app.cotrip.data.remote

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * Minimal Retrofit converter factory backed by Kotlinx Serialization.
 * Supports JSON request/response bodies used within the sample app.
 */
class KotlinSerializationConverterFactory private constructor(
    private val format: SerialFormat,
    private val contentType: MediaType
) : Converter.Factory() {

    @OptIn(ExperimentalSerializationApi::class)
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        val deserializer = format.serializersModule.serializer(type)
        return Converter<ResponseBody, Any> { body ->
            body.use {
                val string = it.string()
                when (format) {
                    is Json -> format.decodeFromString(deserializer, string)
                    else -> throw UnsupportedOperationException("Unsupported format: $format")
                }
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<Annotation>,
        methodAnnotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody>? {
        val serializer = format.serializersModule.serializer(type)
        return Converter<Any, RequestBody> { value ->
            when (format) {
                is Json -> {
                    val jsonString = format.encodeToString(serializer, value)
                    jsonString.toRequestBody(contentType)
                }
                else -> throw UnsupportedOperationException("Unsupported format: $format")
            }
        }
    }

    companion object {
        fun create(json: Json, contentType: MediaType): KotlinSerializationConverterFactory =
            KotlinSerializationConverterFactory(json, contentType)
    }
}
