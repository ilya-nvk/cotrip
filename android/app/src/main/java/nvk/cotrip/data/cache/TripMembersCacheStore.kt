package nvk.cotrip.data.cache

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.network.dto.MemberDto

interface TripMembersCacheStore {
    fun observeMembers(tripId: String): Flow<List<MemberDto>>
    suspend fun getMembers(tripId: String): List<MemberDto>
    suspend fun setMembers(tripId: String, members: List<MemberDto>)
    suspend fun removeMember(tripId: String, memberId: String)
    suspend fun clearTrip(tripId: String)
    suspend fun clearAll()
}

