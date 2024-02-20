@file:Suppress("UNUSED")

package com.ub.basket

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

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
     * Changes the state of the cart if new items are added to it by [request]
     *
     * @param count items to add. Cannot be less than 1
     */
    suspend fun addItem(request: BasketRequest, count: Int = 1)

    /**
     * Changes the state of the cart if the items contained in it are removed from it by [request]
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
 * External source of basket state
 *
 * It is **highly desirable** to perform the operation of obtaining the state either as quickly as possible, or in a thread other than main
 */
interface CartStateSource {
    operator fun invoke(): List<BasketModel>
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
 * @param cartStateSource optional external source of basket state. Invokes only once while class creation
 * @property divisionValue the value of the exchangeable currency of the lower level
 */
class CartService @JvmOverloads constructor(
    private val dataSource: ICartDataSource,
    cartStateSource: CartStateSource? = null,
    private val divisionValue: Int = STANDARD_DIVISION_VALUE
) : ICartRead, ICartChange {

    private val _state = MutableStateFlow(
        value = CartStateModel(
            products = cartStateSource?.invoke() ?: listOf(),
            divisionValue = divisionValue
        )
    )

    override val stateFlow: StateFlow<CartStateModel> = _state.asStateFlow()

    override fun getCartItems(): List<BasketModel> = _state.value.products

    override fun getCountInCart(request: BasketRequest): Int = _state.value.products.firstOrNull { model ->
        when (request) {
            is SingleItem -> model.id == request.itemId && (model as? SingleBasketModel)?.variant == request.variantId
            is MultipleItem -> model.id == request.itemId && (model as? MultipleBasketModel)?.multipleItems == request.multipleItems
        }
    }?.count ?: 0

    override fun getCartPrice(): Double = _state.value.price

    override suspend fun addItem(request: BasketRequest, count: Int) {
        if (count < 1) throw WrongCountCartRequestException("Count to add must be greater than 0. Now is $count")
        when (request) {
            is SingleItem -> _state.getAndUpdate { state ->
                val nullableItem = state.products.firstOrNull { product ->
                    product is SingleBasketModel
                        && product.id == request.itemId
                        && product.variant == request.variantId
                }
                if (nullableItem != null) {
                    state.copy(
                        innerPrice = state.innerPrice + (nullableItem.singlePrice * divisionValue).toLong() * count,
                        products = state.products.map { item ->
                            if (item is SingleBasketModel && item.id == request.itemId && item.variant == request.variantId) {
                                item.copy(
                                    count = item.count + count
                                )
                            } else {
                                item
                            }
                        }
                    )
                } else {
                    val product = dataSource.getItemByRequest(request)
                        ?: throw ProductNotFoundException("Item for id ${request.itemId} is null")

                    val addedItem = SingleBasketModel(
                        id = product.id,
                        variant = request.variantId,
                        price = product.price,
                        singlePrice = product.price,
                        count = count
                    )

                    val additionalList = listOf(addedItem)

                    state.copy(
                        innerPrice = state.innerPrice + (addedItem.singlePrice * divisionValue).toLong() * count,
                        products = state.products + additionalList
                    )
                }
            }
            is MultipleItem -> _state.getAndUpdate { state ->
                val nullableItem = state.products.firstOrNull { product ->
                    product is MultipleBasketModel
                        && product.id == request.itemId
                        && product.multipleItems.toTypedArray().contentDeepEquals(request.multipleItems.toTypedArray())
                }
                if (nullableItem != null) {
                    state.copy(
                        innerPrice = state.innerPrice + (nullableItem.singlePrice * divisionValue).toLong() * count,
                        products = state.products.map { item ->
                            if (item is MultipleBasketModel && item.id == request.itemId && item.multipleItems == request.multipleItems) {
                                item.copy(
                                    count = item.count + count
                                )
                            } else {
                                item
                            }
                        }
                    )
                } else {
                    val multipleProduct = dataSource.getMultipleItemByRequest(request)
                    val singlePrice: Double = multipleProduct.sumOf { itemInBasket -> itemInBasket.price }
                    val addedItem = MultipleBasketModel(
                        id = request.itemId,
                        price = singlePrice,
                        singlePrice = singlePrice,
                        count = count,
                        multipleItems = request.multipleItems
                    )

                    val additionalList = listOf(addedItem)

                    state.copy(
                        innerPrice = state.innerPrice + (addedItem.singlePrice * divisionValue).toLong() * count,
                        products = state.products + additionalList
                    )
                }
            }
        }
    }

    override suspend fun removeItem(request: BasketRequest, count: Int) {
        if (count < 1) throw WrongCountCartRequestException("Count to remove must be greater than 0. Now is $count")
        when (request) {
            is SingleItem -> _state.getAndUpdate { state ->
                state.products.firstOrNull { model ->
                    model is SingleBasketModel
                        && model.id == request.itemId
                        && model.variant == request.variantId
                }?.let { model ->
                    state.copy(
                        innerPrice = state.innerPrice - (model.singlePrice * divisionValue).toLong() * count,
                        products = state.products - (model as SingleBasketModel).copy(count = count)
                    )
                } ?: state
            }
            is MultipleItem -> _state.getAndUpdate { state ->
                state.products.firstOrNull { model ->
                    model is MultipleBasketModel
                        && model.id == request.itemId
                        && model.multipleItems == request.multipleItems
                }?.let { model ->
                    state.copy(
                        innerPrice = state.innerPrice - (model.singlePrice * divisionValue).toLong() * count,
                        products = state.products - (model as MultipleBasketModel).copy(count = count)
                    )
                } ?: state
            }
        }
    }

    override fun clearBasket() = _state.update {
        CartStateModel(divisionValue = divisionValue)
    }

    private companion object {
        private const val STANDARD_DIVISION_VALUE = 100
    }
}