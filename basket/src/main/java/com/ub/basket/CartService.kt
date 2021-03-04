@file:Suppress("UNUSED")

package com.ub.basket

import kotlin.properties.Delegates

/**
 * Provides read-level access to the current state of the cart.
 * All functions are synchronous and do not imply state changes
 */
interface ICartRead {
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
     * @throws WrongCountCartRequestException - throws if the [count] is less than 0
     * @throws ProductNotFoundException - throws if the item was not found in
     * the data source [ICartDataSource.getItemByRequest] from [SingleItem] request
     */
    suspend fun addItem(request: BasketRequest, count: Int = 1)

    /**
     * Changes the state of the cart if the items contained in it are removed from it by request
     *
     * @throws WrongCountCartRequestException - throws an exception if the [count] is less than 0
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
 * It handle with requests to basket service. Data that needed from source:
 * - item id
 * - item price for single item
 */
interface ICartDataSource {
    suspend fun getItemByRequest(request: SingleItem): CartDataRequestWrapper?
    suspend fun getMultipleItemByRequest(request: MultipleItem): List<CartDataRequestWrapper>
}

/**
 * TODO
 */
class CartService @JvmOverloads constructor(
    private val dataSource: ICartDataSource,
    private val divisionValue: Int = STANDARD_DIVISION_VALUE
) : ICartRead, ICartChange {

    private var state: CartStateModel by Delegates.observable(CartStateModel()) { _, _, newState ->
        newState.hashCode()
    }

    override fun getCartItems(): List<BasketModel> = state.products

    override fun getCountInCart(request: BasketRequest): Int {
        return state.products.firstOrNull {
            when (request) {
                is SingleItem -> (it as? SingleBasketModel)?.id == request.itemId && (it as? SingleBasketModel)?.variant == request.variantId
                is MultipleItem -> (it as? MultipleBasketModel)?.multipleProductId == request.itemId && (it as? MultipleBasketModel)?.multipleItems == request.multipleItems
            }
        }?.count ?: 0
    }

    override fun getCartPrice(): Double = state.price / divisionValue.toDouble()

    override suspend fun addItem(request: BasketRequest, count: Int) {
        if (count < 1) throw WrongCountCartRequestException("Count to add must be greater than 0. Now is $count")
        when (request) {
            is SingleItem -> state.products.firstOrNull { product ->
                product is SingleBasketModel
                    && product.id == request.itemId
                    && product.variant == request.variantId
            }.let { nullableItem ->
                if (nullableItem != null) {
                    state = state.copy(
                        price = state.price + (nullableItem.singlePrice * divisionValue).toLong() * count,
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
                        product.id,
                        request.variantId,
                        product.price,
                        product.price,
                        count
                    )

                    val additionalList = listOf(addedItem)

                    state = state.copy(
                        price = state.price + (addedItem.singlePrice * divisionValue).toLong() * count,
                        products = state.products + additionalList
                    )
                }
            }
            is MultipleItem -> state.products.firstOrNull { product ->
                product is MultipleBasketModel
                    && product.multipleProductId == request.itemId
                    && product.multipleItems.toTypedArray().contentDeepEquals(request.multipleItems.toTypedArray())
            }.let { nullableItem ->
                if (nullableItem != null) {
                    state = state.copy(
                        price = state.price + (nullableItem.singlePrice * divisionValue).toLong() * count,
                        products = state.products.map { item ->
                            if (item is MultipleBasketModel && item.multipleProductId == request.itemId && item.multipleItems == request.multipleItems) {
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
                    val singlePrice: Double = multipleProduct.sumByDouble { itemInBasket -> itemInBasket.price }
                    val addedItem = MultipleBasketModel(
                        singlePrice,
                        singlePrice,
                        count,
                        request.itemId,
                        request.multipleItems
                    )

                    val additionalList = listOf(addedItem)

                    state = state.copy(
                        price = state.price + (addedItem.singlePrice * divisionValue).toLong() * count,
                        products = state.products + additionalList
                    )
                }
            }
        }
    }

    override suspend fun removeItem(request: BasketRequest, count: Int) {
        if (count < 1) throw WrongCountCartRequestException("Count to remove must be greater than 0. Now is $count")
        when (request) {
            is SingleItem -> {
                state.products.firstOrNull { it is SingleBasketModel && it.id == request.itemId && it.variant == request.variantId }?.let { model ->
                    state = state.copy(
                        price = state.price - (model.singlePrice * divisionValue).toLong() * count,
                        products = state.products - (model as SingleBasketModel).copy(count = count)
                    )
                }
            }
            is MultipleItem -> {
                state.products.firstOrNull { it is MultipleBasketModel && it.multipleProductId == request.itemId && it.multipleItems == request.multipleItems }?.let { model ->
                    state = state.copy(
                        price = state.price - (model.singlePrice * divisionValue).toLong() * count,
                        products = state.products - (model as MultipleBasketModel).copy(count = count)
                    )
                }
            }
        }
    }

    override fun clearBasket() {
        state = CartStateModel()
    }

    companion object {
        private const val STANDARD_DIVISION_VALUE = 100
    }
}