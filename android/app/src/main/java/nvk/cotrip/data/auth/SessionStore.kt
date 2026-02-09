package nvk.cotrip.data.auth

interface SessionStore {
    fun getAccessToken(): String?
    fun setAccessToken(token: String)
    fun clear()
}
