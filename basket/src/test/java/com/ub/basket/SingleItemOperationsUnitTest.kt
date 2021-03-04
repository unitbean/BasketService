package com.ub.basket

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class SingleItemOperationsUnitTest {

    private var cartService: CartService = CartService(object : ICartDataSource {
        override suspend fun getItemByRequest(request: SingleItem): CartDataRequestWrapper? {
            return if (request.itemId != WRONG_ID) {
                CartDataRequestWrapper(
                    request.itemId,
                    when {
                        request.itemId == LONG_QUERY -> {
                            delay(100)
                            275.0
                        }
                        request.itemId.endsWith("100") && request.variantId?.endsWith("50") == true -> 150.0
                        request.itemId.endsWith("100") && request.variantId?.endsWith("75") == true  -> 175.0
                        request.itemId.endsWith("100") -> 100.0
                        request.itemId.endsWith("250") -> 250.0
                        else -> 500.0
                    }
                )
            } else null
        }

        override suspend fun getMultipleItemByRequest(request: MultipleItem): List<CartDataRequestWrapper> {
            throw UnsupportedOperationException("Multiple request is not supported in this test")
        }
    })

    @Test
    fun add_single_isCorrect() {
        runBlocking {
            cartService.addItem(BasketRequest.item("testId1"))
            assertEquals(1, cartService.getCartItems().sumBy { it.count })
        }
    }

    @Test
    fun add_single_and_remove_isCorrect() {
        runBlocking {
            cartService.addItem(BasketRequest.item("testId1"))
            cartService.removeItem(BasketRequest.item("testId1"))
            assertEquals(0, cartService.getCartItems().sumBy { it.count })
        }
    }

    @Test
    fun add_double_isCorrect() {
        runBlocking {
            cartService.addItem(BasketRequest.item("testId1"))
            cartService.addItem(BasketRequest.item("testId1"))
            assertEquals(2, cartService.getCartItems().sumBy { it.count })
        }
    }

    @Test
    fun add_double_variant_isCorrect() {
        runBlocking {
            cartService.addItem(BasketRequest.item("testId1"))
            cartService.addItem(BasketRequest.item("testId1", "testSubId50"))
            assertEquals(2, cartService.getCartItems().sumBy { it.count })
        }
    }

    @Test
    fun add_double_count_isCorrect() {
        runBlocking {
            cartService.addItem(BasketRequest.item("testId1"), count = 2)
            assertEquals(2, cartService.getCartItems().sumBy { it.count })
        }
    }

    @Test
    fun add_single_count_sum_isCorrect() {
        runBlocking {
            cartService.addItem(BasketRequest.item("testId1"))
            assertEquals(500.0, cartService.getCartPrice(), 0.0)
        }
    }

    @Test
    fun add_double_count_sum_isCorrect() {
        runBlocking {
            cartService.addItem(BasketRequest.item("testId1"), count = 2)
            assertEquals(1000.0, cartService.getCartPrice(), 0.0)
        }
    }

    @Test
    fun add_double_count_different_items_isCorrect() {
        runBlocking {
            cartService.addItem(BasketRequest.item("testId1"))
            cartService.addItem(BasketRequest.item("testId2"))
            assertEquals(2, cartService.getCartItems().sumBy { it.count })
        }
    }

    @Test
    fun add_single_and_remove_wrong_position_isCorrect() {
        runBlocking {
            cartService.addItem(BasketRequest.item("testId1"))
            cartService.removeItem(BasketRequest.item("testId2"))
            assertEquals(1, cartService.getCartItems().sumBy { it.count })
        }
    }

    @Test
    fun add_single_and_remove_double_wrong_position_isCorrect() {
        runBlocking {
            cartService.addItem(BasketRequest.item("testId1"), 3)
            cartService.removeItem(BasketRequest.item("testId1"), 2)
            assertEquals(1, cartService.getCartItems().sumBy { it.count })
        }
    }

    @Test(expected = WrongCountCartRequestException::class)
    fun add_single_zero_items_isNotCorrect() {
        runBlocking {
            cartService.addItem(BasketRequest.item("testId1"), 0)
        }
    }

    @Test(expected = ProductNotFoundException::class)
    fun add_single_wrong_item_isNotCorrect() {
        runBlocking {
            cartService.addItem(BasketRequest.item(WRONG_ID))
        }
    }

    @Test
    fun add_and_clear_isCorrect() {
        runBlocking {
            cartService.addItem(BasketRequest.item("testId1"), 5)
            cartService.clearBasket()
            assertEquals(0, cartService.getCartItems().sumBy { it.count })
            assertEquals(0.0, cartService.getCartPrice(), 0.0)
        }
    }

    @Test
    fun add_and_get_count_isCorrect() {
        runBlocking {
            cartService.addItem(BasketRequest.item("testId1"), 2)
            val count = cartService.getCountInCart(BasketRequest.item("testId1"))
            assertEquals(2, count)
        }
    }

    @Test
    fun add_and_get_wrong_count_isCorrect() {
        runBlocking {
            cartService.addItem(BasketRequest.item("testId1"), 2)
            val count = cartService.getCountInCart(BasketRequest.item("testId2"))
            assertEquals(0, count)
        }
    }

    @Test
    fun get_empty_isCorrect() {
        runBlocking {
            val count = cartService.getCountInCart(BasketRequest.item("testId1"))
            assertEquals(0, count)
        }
    }

    @Test(timeout = 200)
    fun add_long_query_cache_isCorrect() {
        runBlocking {
            cartService.addItem(BasketRequest.item(LONG_QUERY))
            cartService.addItem(BasketRequest.item(LONG_QUERY))
            assertEquals(2, cartService.getCartItems().sumBy { it.count })
        }
    }

    @Test
    fun add_different_prices_isCorrect() {
        runBlocking {
            cartService.addItem(BasketRequest.item("testId100"))
            cartService.addItem(BasketRequest.item("testId250"))
            cartService.addItem(BasketRequest.item("testId100", "testSubId50"))
            cartService.addItem(BasketRequest.item("testId100", "testSubId75"))
            cartService.addItem(BasketRequest.item("testId500"))
            assertEquals(100.0 + 250.0 + + 150.0 + 175.0 + 500.0, cartService.getCartPrice(), 0.0)
        }
    }

    companion object {
        private const val WRONG_ID = "wrong_id"
        private const val LONG_QUERY = "long_query"
    }
}