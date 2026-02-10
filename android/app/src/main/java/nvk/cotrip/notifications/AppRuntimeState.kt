package nvk.cotrip.notifications

import java.util.concurrent.atomic.AtomicBoolean

object AppRuntimeState {
    private val isForegroundAtomic = AtomicBoolean(false)

    @Volatile
    private var activeDiscussionIdeaId: String? = null

    fun setAppForeground(isForeground: Boolean) {
        isForegroundAtomic.set(isForeground)
    }

    fun isAppForeground(): Boolean = isForegroundAtomic.get()

    fun setActiveDiscussionIdeaId(ideaId: String) {
        activeDiscussionIdeaId = ideaId
    }

    fun clearActiveDiscussionIdeaId(ideaId: String? = null) {
        if (ideaId == null || activeDiscussionIdeaId == ideaId) {
            activeDiscussionIdeaId = null
        }
    }

    fun isDiscussionOpenForIdea(ideaId: String): Boolean = activeDiscussionIdeaId == ideaId
}
