package nvk.cotrip.notifications

object NotificationNavigationState {
    @Volatile
    private var openDiscussionIdeaId: String? = null

    fun requestOpenDiscussion(ideaId: String) {
        openDiscussionIdeaId = ideaId
    }

    fun consumeOpenDiscussion(ideaId: String): Boolean {
        if (openDiscussionIdeaId != ideaId) return false
        openDiscussionIdeaId = null
        return true
    }
}

