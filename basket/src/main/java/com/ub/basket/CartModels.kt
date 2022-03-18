package com.ub.basket

/**
 * Model for transferring the result of requesting product data from an external source.
 */
class CartDataRequestWrapper(
    val id: String,
    val price: Double
)

/**
 * Basket state representation
 */
data class CartStateModel(
    private val divisionValue: Int,
    internal val innerPrice: Long = 0L,
    val products: List<BasketModel> = listOf()
) {
    val price: Double
        get() = innerPrice / divisionValue.toDouble()
}

/**
 * Base class for the cart product items
 */
sealed class BasketModel {
    abstract val id: String
    abstract val price: Double
    abstract val singlePrice: Double
    abstract val count: Int
}

/**
 * Single item model for internal shopping cart logic
 */
data class SingleBasketModel(
    override val id: String,
    val variant: String?,
    override val price: Double,
    override val singlePrice: Double,
    override val count: Int
) : BasketModel() {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SingleBasketModel) return false

        if (id != other.id) return false
        if (variant != other.variant) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + variant.hashCode()
        return result
    }
}

/**
 * Product model with multiple sub-items for the internal logic of the cart
 */
data class MultipleBasketModel(
    override val id: String,
    override val price: Double,
    override val singlePrice: Double,
    override val count: Int,
    val multipleItems: List<CartMultipleItemRequest>
) : BasketModel() {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MultipleBasketModel) return false

        if (id != other.id) return false
        if (multipleItems != other.multipleItems) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + multipleItems.hashCode()
        return result
    }
}

/**
 * Base class for exceptions when working with the cart service
 */
sealed class CartServiceException(message: String) : Exception(message)

/**
 * Exception displaying the wrong count of products when trying to change the cart
 */
class WrongCountCartRequestException(message: String) : CartServiceException(message)

/**
 * Exception showing the absence of the added product in the data source [ICartDataSource]
 */
class ProductNotFoundException(message: String) : CartServiceException(message)

/**
 * Public class for creating all types of cart interaction requests
 */
sealed class BasketRequest {
    companion object {
        @JvmStatic
        fun item(itemId: String, variantId: String? = null): SingleItem = SingleItem(itemId, variantId)
        @JvmStatic
        fun multipleItem(itemId: String, multipleItems: List<CartMultipleItemRequest>): MultipleItem = MultipleItem(itemId, multipleItems)
    }
}

/**
 * Internal model of a cart interaction request
 */
data class SingleItem(
    val itemId: String,
    val variantId: String? = null
) : BasketRequest()

/**
 * Internal model of a cart interaction request
 */
data class MultipleItem(
    val itemId: String,
    val multipleItems: List<CartMultipleItemRequest>
) : BasketRequest()

/**
 * Internal sub-model for multiple request model
 */
data class CartMultipleItemRequest(
    val itemId: String,
    val variantId: String? = null,
    val count: Int = 1
)

internal operator fun List<BasketModel>.plus(otherList: List<BasketModel>): List<BasketModel> {
    val baseList = this.toMutableList()
    val otherItems = otherList.toMutableList()

    val iterator = baseList.iterator()

    while (iterator.hasNext()) {
        val item = iterator.next()
        if (otherList.contains(item)) {
            val position = baseList.indexOf(item)
            val addedItem = otherList[otherList.indexOf(item)]
            when (item) {
                is SingleBasketModel -> item.copy(count = item.count + (addedItem as SingleBasketModel).count)
                is MultipleBasketModel -> item.copy(count = item.count + (addedItem as MultipleBasketModel).count)
            }.let { modifiedItem ->
                baseList[position] = modifiedItem
                otherItems.remove(addedItem)
            }
        }
    }

    baseList.addAll(0, otherItems)

    return baseList
}

internal operator fun List<BasketModel>.plus(otherItem: BasketModel): List<BasketModel> {
    return this.plus(listOf(otherItem))
}

internal operator fun List<BasketModel>.minus(otherList: List<BasketModel>): List<BasketModel> {
    val baseList = this.toMutableList()

    val iterator = baseList.iterator()

    while (iterator.hasNext()) {
        val item = iterator.next()
        if (otherList.contains(item)) {
            val removedPosition = baseList.indexOf(item)
            val itemToRemove = otherList[otherList.indexOf(item)]
            when (item) {
                is SingleBasketModel -> if (item.count - (itemToRemove as SingleBasketModel).count > 0) {
                    item.copy(count = item.count - itemToRemove.count)
                } else null
                is MultipleBasketModel -> if (item.count - (itemToRemove as MultipleBasketModel).count > 0) {
                    item.copy(count = item.count - itemToRemove.count)
                } else null
            }?.let { modifiedItem ->
                baseList[removedPosition] = modifiedItem
            } ?: iterator.remove()
        }
    }

    return baseList
}

internal operator fun List<BasketModel>.minus(otherItem: BasketModel): List<BasketModel> {
    return this.minus(listOf(otherItem))
}