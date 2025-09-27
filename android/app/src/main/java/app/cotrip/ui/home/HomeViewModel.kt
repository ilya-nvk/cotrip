package app.cotrip.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import app.cotrip.common.auth.GoogleSignInManager
import app.cotrip.domain.usecase.GetTripsUseCase
import app.cotrip.domain.usecase.RefreshTripsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val googleSignInManager: GoogleSignInManager,
    private val getTripsUseCase: GetTripsUseCase,
    private val refreshTripsUseCase: RefreshTripsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeTrips()
        refreshTrips()
    }

    private fun observeTrips() {
        viewModelScope.launch {
            getTripsUseCase()
                .onStart { _uiState.value = _uiState.value.copy(isLoading = true) }
                .catch { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = throwable.message
                    )
                }
                .collect { trips ->
                    _uiState.value = HomeUiState(
                        isLoading = false,
                        trips = trips,
                        error = null
                    )
                }
        }
    }

    fun refreshTrips() {
        viewModelScope.launch {
            try {
                refreshTripsUseCase()
            } catch (ignored: Exception) {
                // No-op for sample app
            }
        }
    }

    fun getGoogleSignInIntent(): Intent =
        googleSignInManager.getClient().signInIntent.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
