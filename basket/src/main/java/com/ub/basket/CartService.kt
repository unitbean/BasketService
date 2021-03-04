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
     */
    suspend fun addItem(request: BasketRequest, count: Int = 1)

    /**
     * Changes the state of the cart if the items contained in it are removed from it by request
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
 * @throws WrongCountCartRequestException - throws an exception if the [count]
 * in item request is less than 1
 *
 * @throws ProductNotFoundException - throws if the item was not found in
 * the data source [ICartDataSource.getItemByRequest] from [SingleItem] request
 *
 * @property dataSource - instance of [ICartDataSource] that implements the logic
 * of obtaining the minimum necessary data from external sources for the cart to work
 * @property divisionValue - the value of the exchangeable currency of the lower level
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
                is SingleItem -> it.id == request.itemId && (it as? SingleBasketModel)?.variant == request.variantId
                is MultipleItem -> it.id == request.itemId && (it as? MultipleBasketModel)?.multipleItems == request.multipleItems
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
                    && product.id == request.itemId
                    && product.multipleItems.toTypedArray().contentDeepEquals(request.multipleItems.toTypedArray())
            }.let { nullableItem ->
                if (nullableItem != null) {
                    state = state.copy(
                        price = state.price + (nullableItem.singlePrice * divisionValue).toLong() * count,
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
                    val singlePrice: Double = multipleProduct.sumByDouble { itemInBasket -> itemInBasket.price }
                    val addedItem = MultipleBasketModel(
                        request.itemId,
                        singlePrice,
                        singlePrice,
                        count,
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
                state.products.firstOrNull { it is MultipleBasketModel && it.id == request.itemId && it.multipleItems == request.multipleItems }?.let { model ->
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

    private companion object {
        private const val STANDARD_DIVISION_VALUE = 100
    }
}