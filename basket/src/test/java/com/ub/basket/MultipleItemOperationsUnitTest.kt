package com.ub.basket

import kotlinx.coroutines.runBlocking
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class MultipleItemOperationsUnitTest {

    private var cartService: CartService = CartService(object : ICartDataSource {
        override suspend fun getItemByRequest(request: SingleItem): CartDataRequestWrapper? {
            throw UnsupportedOperationException("Single request is not supported in this test")
        }

        override suspend fun getMultipleItemByRequest(request: MultipleItem): List<CartDataRequestWrapper> {
            return request.multipleItems.mapNotNull { itemRequest ->
                if (request.itemId != WRONG_ID) {
                    CartDataRequestWrapper(
                        request.itemId,
                        when {
                            itemRequest.itemId.endsWith("100") && itemRequest.variantId?.endsWith("50") == true -> 150.0 * itemRequest.count
                            itemRequest.itemId.endsWith("100") && itemRequest.variantId?.endsWith("75") == true -> 175.0 * itemRequest.count
                            itemRequest.itemId.endsWith("100") -> 100.0 * itemRequest.count
                            itemRequest.itemId.endsWith("250") -> 250.0 * itemRequest.count
                            else -> 500.0 * itemRequest.count
                        }
                    )
                } else null
            }
        }
    })

    @Test
    fun add_single_isCorrect() {
        runBlocking {
            cartService.addItem(BasketRequest.multipleItem("testId1", listOf(CartMultipleItemRequest("testSubId1"))))
            assertEquals(1, cartService.getCartItems().sumOf { it.count })
        }
    }

    @Test
    fun add_multi_isCorrect() {
        runBlocking {
            cartService.addItem(
                BasketRequest.multipleItem(
                    "testId1",
                    listOf(
                        CartMultipleItemRequest("testSubId1"),
                        CartMultipleItemRequest("testSubId2"),
                        CartMultipleItemRequest("testSubId3"),
                        CartMultipleItemRequest("testSubId4"),
                        CartMultipleItemRequest("testSubId5")
                    )
                )
            )
            assertEquals(1, cartService.getCartItems().sumOf { it.count })
        }
    }

    @Test
    fun add_multi_variant_isCorrect() {
        runBlocking {
            cartService.addItem(
                BasketRequest.multipleItem(
                    "testId1",
                    listOf(
                        CartMultipleItemRequest("testSubId100")
                    )
                )
            )
            cartService.addItem(
                BasketRequest.multipleItem(
                    "testId2",
                    listOf(
                        CartMultipleItemRequest("testSubId100"),
                        CartMultipleItemRequest("testSubId100", "subVariant50")
                    )
                )
            )
            assertEquals(2, cartService.getCartItems().sumOf { it.count })
        }
    }

    @Test
    fun add_multi_total_price_isCorrect() {
        runBlocking {
            cartService.addItem(
                BasketRequest.multipleItem(
                    "testId1",
                    listOf(
                        CartMultipleItemRequest("testSubId100"),
                        CartMultipleItemRequest("testSubId100", "subVariant50"),
                        CartMultipleItemRequest("testSubId100", "subVariant75"),
                        CartMultipleItemRequest("testSubId250"),
                        CartMultipleItemRequest("testSubId3"),
                        CartMultipleItemRequest("testSubId4")
                    )
                )
            )
            assertEquals(100.0 + 150.0 + 175.0 + 250.0 + 500.0 + 500.0, cartService.getCartPrice(), 0.0)
        }
    }

    @Test
    fun add_multi_double_different_count_isCorrect() {
        runBlocking {
            cartService.addItem(
                BasketRequest.multipleItem(
                    "testId1",
                    listOf(
                        CartMultipleItemRequest("testSubId3"),
                        CartMultipleItemRequest("testSubId4")
                    )
                )
            )
            cartService.addItem(
                BasketRequest.multipleItem(
                    "testId1",
                    listOf(
                        CartMultipleItemRequest("testSubId2"),
                        CartMultipleItemRequest("testSubId3"),
                        CartMultipleItemRequest("testSubId4")
                    )
                )
            )
            assertEquals(2, cartService.getCartItems().sumOf { it.count })
        }
    }

    @Test
    fun add_multi_double_same_count_isCorrect() {
        runBlocking {
            cartService.addItem(
                BasketRequest.multipleItem(
                    "testId1",
                    listOf(
                        CartMultipleItemRequest("testSubId1"),
                        CartMultipleItemRequest("testSubId2")
                    )
                )
            )
            cartService.addItem(
                BasketRequest.multipleItem(
                    "testId1",
                    listOf(
                        CartMultipleItemRequest("testSubId1"),
                        CartMultipleItemRequest("testSubId3")
                    )
                )
            )
            assertEquals(2, cartService.getCartItems().sumOf { it.count })
        }
    }

    @Test
    fun add_multi_double_count_isCorrect() {
        runBlocking {
            cartService.addItem(
                BasketRequest.multipleItem(
                    "testId1",
                    listOf(
                        CartMultipleItemRequest("testSubId1"),
                        CartMultipleItemRequest("testSubId2")
                    )
                ),
                2
            )
            assertEquals(2, cartService.getCartItems().sumOf { it.count })
        }
    }

    @Test
    fun remove_single_count_isCorrect() {
        runBlocking {
            cartService.addItem(
                BasketRequest.multipleItem(
                    "testId1",
                    listOf(
                        CartMultipleItemRequest("testSubId1"),
                        CartMultipleItemRequest("testSubId2")
                    )
                ),
                2
            )
            cartService.removeItem(
                BasketRequest.multipleItem(
                    "testId1",
                    listOf(
                        CartMultipleItemRequest("testSubId1"),
                        CartMultipleItemRequest("testSubId2")
                    )
                )
            )
            assertEquals(1, cartService.getCartItems().sumOf { it.count })
        }
    }

    @Test(expected = WrongCountCartRequestException::class)
    fun add_single_zero_items_isNotCorrect() {
        runBlocking {
            cartService.addItem(BasketRequest.multipleItem("testId1", listOf(CartMultipleItemRequest("testSubId1"))), 0)
        }
    }

    @Test
    fun add_multi_different_variant_isCorrect() {
        runBlocking {
            cartService.addItem(
                BasketRequest.multipleItem(
                    "testId1",
                    listOf(
                        CartMultipleItemRequest("testSubId100"),
                        CartMultipleItemRequest("testSubId100", "subVariant50", 1)
                    )
                )
            )
            cartService.addItem(
                BasketRequest.multipleItem(
                    "testId1",
                    listOf(
                        CartMultipleItemRequest("testSubId100"),
                        CartMultipleItemRequest("testSubId100", "subVariant50", 2)
                    )
                )
            )
            assertEquals(2, cartService.getCartItems().sumOf { it.count })
        }
    }

    @Test
    fun add_multi_different_variant_deep_isCorrect() {
        runBlocking {
            cartService.addItem(
                BasketRequest.multipleItem(
                    "testId1",
                    listOf(
                        CartMultipleItemRequest("testSubId100", count = 1),
                        CartMultipleItemRequest("testSubId100", "subVariant50", count = 1)
                    )
                )
            )
            cartService.addItem(
                BasketRequest.multipleItem(
                    "testId1",
                    listOf(
                        CartMultipleItemRequest("testSubId100", count = 1),
                        CartMultipleItemRequest("testSubId100", "subVariant50", count = 2)
                    )
                )
            )
            assertEquals(1 + 1 + 1 + 2, cartService.getCartItems().sumOf { model ->
                (model as MultipleBasketModel).multipleItems.sumOf { subItem ->
                    subItem.count
                }
            })
        }
    }

    @Test
    fun add_multi_equal_variant_deep_isCorrect() {
        runBlocking {
            cartService.addItem(
                BasketRequest.multipleItem(
                    "testId1",
                    listOf(
                        CartMultipleItemRequest("testSubId100", count = 1),
                        CartMultipleItemRequest("testSubId100", "subVariant50", count = 2)
                    )
                )
            )
            cartService.addItem(
                BasketRequest.multipleItem(
                    "testId1",
                    listOf(
                        CartMultipleItemRequest("testSubId100", count = 1),
                        CartMultipleItemRequest("testSubId100", "subVariant50", count = 2)
                    )
                )
            )
            assertEquals((1 + 2) * 2, cartService.getCartItems().sumOf { model ->
                (model as MultipleBasketModel).multipleItems.sumOf { subItem ->
                    subItem.count * model.count
                }
            })
        }
    }

    @Test
    fun add_multi_count_variant_price_from_null_isCorrect() {
        runBlocking {
            cartService.addItem(
                BasketRequest.multipleItem(
                    "testId1",
                    listOf(
                        CartMultipleItemRequest("testSubId100", "subVariant50", count = 3)
                    )
                )
            )
            assertEquals(150 * 3.0, cartService.getCartPrice(), 0.0)
        }
    }

    @Test
    fun add_multi_count_variant_price_with_existing_isCorrect() {
        runBlocking {
            cartService.addItem(
                BasketRequest.multipleItem(
                    "testId1",
                    listOf(
                        CartMultipleItemRequest("testSubId100", "subVariant50", count = 2)
                    )
                )
            )
            cartService.addItem(
                BasketRequest.multipleItem(
                    "testId1",
                    listOf(
                        CartMultipleItemRequest("testSubId100", "subVariant50", count = 2)
                    )
                )
            )
            assertEquals(
                150 * 2.0
                    + 150 * 2.0,
                cartService.getCartPrice(),
                0.0
            )
        }
    }

    companion object {
        private const val WRONG_ID = "wrong_id"
    }
}