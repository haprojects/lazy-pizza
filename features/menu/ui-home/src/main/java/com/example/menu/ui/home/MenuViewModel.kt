package com.example.menu.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.domain.usecase.ObserveUserIdFlowUseCase
import com.example.auth.domain.usecase.SignOutUseCase
import com.example.cart.domain.usecase.AddCartItemUseCase
import com.example.cart.domain.usecase.ClearCartUseCase
import com.example.cart.domain.usecase.ObserveCartUseCase
import com.example.cart.domain.usecase.RemoveCartItemUseCase
import com.example.cart.domain.usecase.UpdateCartItemQuantityUseCase
import com.example.menu.domain.usecase.ObserveMenuUseCase
import com.example.menu.ui.home.mapper.MenuDomainToUiMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class MenuViewModel @Inject constructor(
    private val observeMenuUseCase: ObserveMenuUseCase,
    private val observeCartUseCase: ObserveCartUseCase,
    private val addCartItemUseCase: AddCartItemUseCase,
    private val updateCartItemQuantityUseCase: UpdateCartItemQuantityUseCase,
    private val removeCartItemUseCase: RemoveCartItemUseCase,
    private val clearCartUseCase: ClearCartUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val observeUserIdFlowUseCase: ObserveUserIdFlowUseCase,
    private val menuMapper: MenuDomainToUiMapper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MenuUiState(isLoggedIn = false, content = MenuContentUiState.Loading))
    val uiState: StateFlow<MenuUiState> = _uiState.onStart {
        observeMenu()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
        initialValue = MenuUiState(isLoggedIn = false, content = MenuContentUiState.Loading),
    )

    private var allSections: List<MenuSectionDisplayModel> = emptyList()

    private fun observeMenu() {
        viewModelScope.launch {
            combine(
                observeMenuUseCase(),
                observeCartUseCase(),
                observeUserIdFlowUseCase(),
            ) { menuSections, cart, userId ->
                val current = _uiState.value.content
                val searchQuery = if (current is MenuContentUiState.Ready) current.searchQuery else ""
                val isSignedIn = userId != null

                val nextContent = menuMapper.mapToMenuContentUiState(menuSections, cart, searchQuery)
                allSections = (nextContent as? MenuContentUiState.Ready)?.originalMenu ?: emptyList()

                MenuUiState(
                    isLoggedIn = isSignedIn,
                    content = nextContent,
                )
            }.collect { uiState ->
                _uiState.value = uiState
            }
        }
    }

    fun updateSearchQuery(query: String) {
        val normalized = query.trim()
        updateSuccessState { current ->
            if (current.searchQuery == normalized) return@updateSuccessState current
            val newFiltered = applyFilters(
                sections = allSections,
                query = normalized,
            )
            current.copy(
                searchQuery = normalized,
                filteredMenu = newFiltered,
            )
        }
    }

    fun addOtherItemToCart(menuItem: MenuItemDisplayModel.Other) {
        viewModelScope.launch {
            addCartItemUseCase(menuMapper.mapToSimpleCartItem(menuItem, quantity = menuItem.quantity + 1))
        }
    }

    fun updateOtherItemQuantity(
        menuItem: MenuItemDisplayModel.Other,
        newQuantity: Int,
    ) {
        viewModelScope.launch {
            when {
                newQuantity == 0 -> removeCartItemUseCase(menuItem.id)
                else -> updateCartItemQuantityUseCase(
                    lineId = menuItem.id,
                    quantity = newQuantity,
                )
            }
        }
    }

    private fun applyFilters(
        sections: List<MenuSectionDisplayModel>,
        query: String,
    ): List<MenuSectionDisplayModel> {
        if (query.isBlank()) return sections
        return sections.mapNotNull { section ->
            val filteredItems = section.items.filter { item ->
                item.name.contains(query, ignoreCase = true)
            }
            if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
        }
    }

    private inline fun updateSuccessState(block: (MenuContentUiState.Ready) -> MenuContentUiState.Ready) {
        _uiState.update { current ->
            val currentContent = current.content
            if (currentContent is MenuContentUiState.Ready) {
                current.copy(content = block(currentContent))
            } else {
                current
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            clearCartUseCase()
            signOutUseCase()
        }
    }
}
