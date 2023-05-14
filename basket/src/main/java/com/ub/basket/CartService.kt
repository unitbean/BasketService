@file:Suppress("UNUSED")

package com.ub.basket

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Provides read-level access to the current state of the cart.
 * All functions are synchronous and do not imply state changes
 *
 * **Strong note**:
 * [stateFlow] is a hot flow, read the docs of [StateFlow] before implementation
 */
interface ICartRead {
    val stateFlow: StateFlow<CartStateModel>
    fun getCartPrice(): Double
    fun getCountInCart(request: BasketRequest): Int
    fun getCartItems(): List<BasketModel>
}

/**
 * Provides change-level access to the current state of the cart.
 * Functions for adding and removing from the basket are suspended
 * due to the fact that at the time of their execution,
 * a request is made to the data source [ICartDataSource] specified
 * in the arguments [CartService] instance, which may cause delays
 * in the operation of the function
 */
interface ICartChange {
    /**
     * Changes the state of the cart if new items are added to it by request
     *
     * @param count items to add. Cannot be less than 1
     */
    suspend fun addItem(request: BasketRequest, count: Int = 1)

    /**
     * Changes the state of the cart if the items contained in it are removed from it by request
     *
     * @param count items to add. Cannot be less than 1
     */
    suspend fun removeItem(request: BasketRequest, count: Int = 1)

    /**
     * Fully resets the cart state
     */
    fun clearBasket()
}

/**
 * Interface of data source for basket.
 *
 * It handle with requests to basket service. Data that needed from source enclosed
 * in [CartDataRequestWrapper] model
 */
interface ICartDataSource {
    suspend fun getItemByRequest(request: SingleItem): CartDataRequestWrapper?
    suspend fun getMultipleItemByRequest(request: MultipleItem): List<CartDataRequestWrapper>
}

/**
 * The logic of a standard shopping cart
 * in an application where there is an opportunity to buy something.
 *
 * The basket supports work with two types of products - single and multiple
 *
 * An internal modifier is supported for products of each level. It can be null
 *
 * It is highly recommended to use the cart via dedicated [ICartRead] or [ICartChange] interfaces
 *
 * Internal work with the price in the basket is carried out by converting the
 * transmitted values to [Long] for greater accuracy of calculations.
 * When retrieving data, the value is converted back to [Double]
 *
 * @throws WrongCountCartRequestException throws an exception if the [count]
 * in item request is less than 1
 *
 * @throws ProductNotFoundException throws if the item was not found in
 * the data source [ICartDataSource.getItemByRequest] from [SingleItem] request
 *
 * @property dataSource instance of [ICartDataSource] that implements the logic
 * of obtaining the minimum necessary data from external sources for the cart to work
 * @property divisionValue the value of the exchangeable currency of the lower level
 */
class CartService @JvmOverloads constructor(
    private val dataSource: ICartDataSource,
    private val divisionValue: Int = STANDARD_DIVISION_VALUE
) : ICartRead, ICartChange {

    private val _state: MutableStateFlow<CartStateModel> by lazy {
        MutableStateFlow(CartStateModel(divisionValue = divisionValue))
    }

    override val stateFlow: StateFlow<CartStateModel> = _state.asStateFlow()

    override fun getCartItems(): List<BasketModel> = _state.value.products

    override fun getCountInCart(request: BasketRequest): Int {
        return _state.value.products.firstOrNull {
            when (request) {
                is SingleItem -> it.id == request.itemId && (it as? SingleBasketModel)?.variant == request.variantId
                is MultipleItem -> it.id == request.itemId && (it as? MultipleBasketModel)?.multipleItems == request.multipleItems
            }
        }?.count ?: 0
    }

    override fun getCartPrice(): Double = _state.value.price

    override suspend fun addItem(request: BasketRequest, count: Int) {
        if (count < 1) throw WrongCountCartRequestException("Count to add must be greater than 0. Now is $count")
        when (request) {
            is SingleItem -> _state.value.products.firstOrNull { product ->
                product is SingleBasketModel
                    && product.id == request.itemId
                    && product.variant == request.variantId
            }.let { nullableItem ->
                if (nullableItem != null) {
                    val newState = _state.value.copy(
                        innerPrice = _state.value.innerPrice + (nullableItem.singlePrice * divisionValue).toLong() * count,
                        products = _state.value.products.map { item ->
                            if (item is SingleBasketModel && item.id == request.itemId && item.variant == request.variantId) {
                                item.copy(
                                    count = item.count + count
                                )
                            } else {
                                item
                            }
                        }
                    )
                    _state.emit(newState)
                } else {
                    val product = dataSource.getItemByRequest(request)
                        ?: throw ProductNotFoundException("Item for id ${request.itemId} is null")

                    val addedItem = SingleBasketModel(
                        product.id,
                        request.variantId,
                        product.price,
                        product.price,
                        count
                    )

                    val additionalList = listOf(addedItem)

                    val newState = _state.value.copy(
                        innerPrice = _state.value.innerPrice + (addedItem.singlePrice * divisionValue).toLong() * count,
                        products = _state.value.products + additionalList
                    )
                    _state.emit(newState)
                }
            }
            is MultipleItem -> _state.value.products.firstOrNull { product ->
                product is MultipleBasketModel
                    && product.id == request.itemId
                    && product.multipleItems.toTypedArray().contentDeepEquals(request.multipleItems.toTypedArray())
            }.let { nullableItem ->
                if (nullableItem != null) {
                    val newState = _state.value.copy(
                        innerPrice = _state.value.innerPrice + (nullableItem.singlePrice * divisionValue).toLong() * count,
                        products = _state.value.products.map { item ->
                            if (item is MultipleBasketModel && item.id == request.itemId && item.multipleItems == request.multipleItems) {
                                item.copy(
                                    count = item.count + count
                                )
                            } else {
                                item
                            }
                        }
                    )
                    _state.emit(newState)
                } else {
                    val multipleProduct = dataSource.getMultipleItemByRequest(request)
                    val singlePrice: Double = multipleProduct.sumOf { itemInBasket -> itemInBasket.price }
                    val addedItem = MultipleBasketModel(
                        request.itemId,
                        singlePrice,
                        singlePrice,
                        count,
                        request.multipleItems
                    )

                    val additionalList = listOf(addedItem)

                    val newState = _state.value.copy(
                        innerPrice = _state.value.innerPrice + (addedItem.singlePrice * divisionValue).toLong() * count,
                        products = _state.value.products + additionalList
                    )
                    _state.emit(newState)
                }
            }
        }
    }

    override suspend fun removeItem(request: BasketRequest, count: Int) {
        if (count < 1) throw WrongCountCartRequestException("Count to remove must be greater than 0. Now is $count")
        when (request) {
            is SingleItem -> {
                _state.value.products.firstOrNull { it is SingleBasketModel && it.id == request.itemId && it.variant == request.variantId }?.let { model ->
                    val newState = _state.value.copy(
                        innerPrice = _state.value.innerPrice - (model.singlePrice * divisionValue).toLong() * count,
                        products = _state.value.products - (model as SingleBasketModel).copy(count = count)
                    )
                    _state.emit(newState)
                }
            }
            is MultipleItem -> {
                _state.value.products.firstOrNull { it is MultipleBasketModel && it.id == request.itemId && it.multipleItems == request.multipleItems }?.let { model ->
                    val newState = _state.value.copy(
                        innerPrice = _state.value.innerPrice - (model.singlePrice * divisionValue).toLong() * count,
                        products = _state.value.products - (model as MultipleBasketModel).copy(count = count)
                    )
                    _state.emit(newState)
                }
            }
        }
    }

    override fun clearBasket() {
        _state.tryEmit(CartStateModel(divisionValue = divisionValue))
    }

    private companion object {
        private const val STANDARD_DIVISION_VALUE = 100
    }
}