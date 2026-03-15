package nvk.cotrip.backend.notifications

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import nvk.cotrip.backend.config.FirebaseConfig
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream

enum class PushDeliveryStatus {
    SENT,
    INVALID_TOKEN,
    FAILED,
    DISABLED,
}

object FirebasePushService {
    private val logger = LoggerFactory.getLogger(FirebasePushService::class.java)
    private val invalidTokenCodes = setOf("UNREGISTERED", "INVALID_ARGUMENT")

    @Volatile
    private var app: FirebaseApp? = null

    fun init(config: FirebaseConfig) {
        val serviceAccountPath = config.serviceAccountPath
        if (serviceAccountPath.isNullOrBlank()) {
            logger.info("Firebase push disabled: FIREBASE_SERVICE_ACCOUNT_PATH is not configured")
            return
        }

        val serviceAccountFile = File(serviceAccountPath)
        if (!serviceAccountFile.exists()) {
            logger.warn("Firebase push disabled: service account file not found at {}", serviceAccountPath)
            return
        }

        if (app != null) return

        runCatching {
            FileInputStream(serviceAccountFile).use { stream ->
                val optionsBuilder = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(stream))
                config.projectId?.let { optionsBuilder.setProjectId(it) }
                val initializedApp = FirebaseApp.initializeApp(optionsBuilder.build(), FIREBASE_APP_NAME)
                app = initializedApp
                logger.info("Firebase push initialized successfully")
            }
        }.onFailure { error ->
            logger.error("Firebase push initialization failed", error)
        }
    }

    fun sendDataMessage(token: String, data: Map<String, String>): PushDeliveryStatus {
        val firebaseApp = app ?: return PushDeliveryStatus.DISABLED
        return try {
            val message = Message.builder()
                .setToken(token)
                .putAllData(data)
                .build()
            FirebaseMessaging.getInstance(firebaseApp).send(message)
            PushDeliveryStatus.SENT
        } catch (error: FirebaseMessagingException) {
            val code = error.messagingErrorCode?.name
            if (code in invalidTokenCodes) {
                PushDeliveryStatus.INVALID_TOKEN
            } else {
                logger.warn("Firebase push send failed for code={}", code, error)
                PushDeliveryStatus.FAILED
            }
        } catch (error: Exception) {
            logger.warn("Firebase push send failed", error)
            PushDeliveryStatus.FAILED
        }
    }

    private const val FIREBASE_APP_NAME = "cotrip-backend"
}
