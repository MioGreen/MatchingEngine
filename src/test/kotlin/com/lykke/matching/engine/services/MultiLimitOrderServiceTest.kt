package com.lykke.matching.engine.services

import com.lykke.matching.engine.cache.WalletCredentialsCache
import com.lykke.matching.engine.daos.Asset
import com.lykke.matching.engine.daos.AssetPair
import com.lykke.matching.engine.daos.TradeInfo
import com.lykke.matching.engine.daos.VolumePrice
import com.lykke.matching.engine.database.TestBackOfficeDatabaseAccessor
import com.lykke.matching.engine.database.TestFileOrderDatabaseAccessor
import com.lykke.matching.engine.database.TestMarketOrderDatabaseAccessor
import com.lykke.matching.engine.database.TestWalletDatabaseAccessor
import com.lykke.matching.engine.database.buildWallet
import com.lykke.matching.engine.database.cache.AssetPairsCache
import com.lykke.matching.engine.database.cache.AssetsCache
import com.lykke.matching.engine.holders.AssetsHolder
import com.lykke.matching.engine.holders.AssetsPairsHolder
import com.lykke.matching.engine.holders.BalancesHolder
import com.lykke.matching.engine.messages.MessageType
import com.lykke.matching.engine.messages.MessageWrapper
import com.lykke.matching.engine.messages.ProtocolMessages
import com.lykke.matching.engine.notification.BalanceUpdateNotification
import com.lykke.matching.engine.notification.QuotesUpdate
import com.lykke.matching.engine.order.OrderStatus
import com.lykke.matching.engine.outgoing.messages.JsonSerializable
import com.lykke.matching.engine.outgoing.messages.LimitOrdersReport
import com.lykke.matching.engine.outgoing.messages.OrderBook
import com.lykke.matching.engine.utils.MessageBuilder
import com.lykke.matching.engine.utils.MessageBuilder.Companion.buildLimitOrder
import com.lykke.matching.engine.utils.MessageBuilder.Companion.buildLimitOrderWrapper
import org.junit.Before
import org.junit.Test
import java.util.Date
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.assertEquals

class MultiLimitOrderServiceTest {
    var testDatabaseAccessor = TestFileOrderDatabaseAccessor()
    var testMarketDatabaseAccessor = TestMarketOrderDatabaseAccessor()
    val testWalletDatabaseAccessor = TestWalletDatabaseAccessor()
    var testBackOfficeDatabaseAccessor = TestBackOfficeDatabaseAccessor()
    val tradesInfoQueue = LinkedBlockingQueue<TradeInfo>()
    val quotesNotificationQueue = LinkedBlockingQueue<QuotesUpdate>()
    val orderBookQueue = LinkedBlockingQueue<OrderBook>()
    val rabbitOrderBookQueue = LinkedBlockingQueue<JsonSerializable>()
    val balanceUpdateQueue = LinkedBlockingQueue<JsonSerializable>()
    val limitOrdersQueue = LinkedBlockingQueue<JsonSerializable>()
    val trustedLimitOrdersQueue = LinkedBlockingQueue<JsonSerializable>()
    val walletCredentialsCache = WalletCredentialsCache(testBackOfficeDatabaseAccessor)
    val rabbitSwapQueue = LinkedBlockingQueue<JsonSerializable>()


    val assetsHolder = AssetsHolder(AssetsCache(testBackOfficeDatabaseAccessor, 60000))
    val assetsPairsHolder = AssetsPairsHolder(AssetPairsCache(testWalletDatabaseAccessor, 60000))
    val balancesHolder = BalancesHolder(testWalletDatabaseAccessor, assetsHolder, LinkedBlockingQueue<BalanceUpdateNotification>(), balanceUpdateQueue, setOf("Client1", "Client5"))
    var genericLimitService = initLimitService()
    var service = initService()

    @Before
    fun setUp() {
        testDatabaseAccessor = TestFileOrderDatabaseAccessor()
        testWalletDatabaseAccessor.clear()

        testBackOfficeDatabaseAccessor.addAsset(Asset("USD", 2, "USD"))
        testBackOfficeDatabaseAccessor.addAsset(Asset("EUR", 2, "EUR"))
        testBackOfficeDatabaseAccessor.addAsset(Asset("CHF", 2, "CHF"))
        testBackOfficeDatabaseAccessor.addAsset(Asset("TIME", 8, "TIME"))
        testBackOfficeDatabaseAccessor.addAsset(Asset("BTC", 8, "BTC"))

        testWalletDatabaseAccessor.addAssetPair(AssetPair("EURUSD", "EUR", "USD", 5, 5))
        testWalletDatabaseAccessor.addAssetPair(AssetPair("EURCHF", "EUR", "CHF", 5, 5))
        testWalletDatabaseAccessor.addAssetPair(AssetPair("TIMEUSD", "TIME", "USD", 6, 6))
        testWalletDatabaseAccessor.addAssetPair(AssetPair("BTCEUR", "BTC", "EUR", 8, 8))
        testWalletDatabaseAccessor.addAssetPair(AssetPair("BTCCHF", "BTC", "CHF", 8, 8))

        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client1", "EUR", 1000.0))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client1", "USD", 1000.0))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client2", "EUR", 1000.0))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client2", "USD", 1000.0))

        genericLimitService = initLimitService()
        service = initService()
    }

    @Test
    fun testAddLimitOrder() {
        service.processMessage(buildMultiLimitOrderWrapper(pair = "EURUSD", clientId = "Client1", volumes = listOf(VolumePrice(100.0, 1.2), VolumePrice(100.0, 1.3))))

        assertEquals(1000.0, testWalletDatabaseAccessor.getBalance("Client1", "USD"))
        assertEquals(0.0, testWalletDatabaseAccessor.getReservedBalance("Client1", "USD"))

        assertEquals(1, limitOrdersQueue.size)
        val limitOrders = limitOrdersQueue.poll() as LimitOrdersReport
        assertEquals(2, limitOrders.orders.size)
        assertEquals(1.2, limitOrders.orders[0].order.price)
        assertEquals(1.3, limitOrders.orders[1].order.price)
    }

    @Test
    fun testAdd2LimitOrder() {
        service.processMessage(buildMultiLimitOrderWrapper(pair = "EURUSD", clientId = "Client1", volumes = listOf(VolumePrice(100.0, 1.2), VolumePrice(100.0, 1.3))))

        assertEquals(1000.0, testWalletDatabaseAccessor.getBalance("Client1", "USD"))
        assertEquals(0.0, testWalletDatabaseAccessor.getReservedBalance("Client1", "USD"))

        assertEquals(1, limitOrdersQueue.size)
        var limitOrders = limitOrdersQueue.poll() as LimitOrdersReport
        assertEquals(2, limitOrders.orders.size)
        assertEquals(1.2, limitOrders.orders[0].order.price)
        assertEquals(1.3, limitOrders.orders[1].order.price)


        service.processMessage(buildMultiLimitOrderWrapper(pair = "EURUSD", clientId = "Client1", volumes = listOf(VolumePrice(100.0, 1.4), VolumePrice(100.0, 1.5))))

        assertEquals(1, limitOrdersQueue.size)
        limitOrders = limitOrdersQueue.poll() as LimitOrdersReport
        assertEquals(2, limitOrders.orders.size)
        assertEquals(1.4, limitOrders.orders[0].order.price)
        assertEquals(1.5, limitOrders.orders[1].order.price)
    }

    @Test
    fun testAddAndCancelLimitOrder() {
        service.processMessage(buildMultiLimitOrderWrapper(pair = "EURUSD", clientId = "Client1", volumes = listOf(VolumePrice(100.0, 1.2), VolumePrice(100.0, 1.3))))

        assertEquals(1000.0, testWalletDatabaseAccessor.getBalance("Client1", "USD"))
        assertEquals(0.0, testWalletDatabaseAccessor.getReservedBalance("Client1", "USD"))

        assertEquals(1, limitOrdersQueue.size)
        var limitOrders = limitOrdersQueue.poll() as LimitOrdersReport
        assertEquals(2, limitOrders.orders.size)
        assertEquals(1.2, limitOrders.orders[0].order.price)
        assertEquals(1.3, limitOrders.orders[1].order.price)


        service.processMessage(buildMultiLimitOrderWrapper(pair = "EURUSD", clientId = "Client1", volumes = listOf(VolumePrice(100.0, 1.4), VolumePrice(100.0, 1.5))))

        assertEquals(1, limitOrdersQueue.size)
        limitOrders = limitOrdersQueue.poll() as LimitOrdersReport
        assertEquals(2, limitOrders.orders.size)
        assertEquals(1.4, limitOrders.orders[0].order.price)
        assertEquals(1.5, limitOrders.orders[1].order.price)


        service.processMessage(buildMultiLimitOrderWrapper(pair = "EURUSD", clientId = "Client1", volumes = listOf(VolumePrice(100.0, 2.0), VolumePrice(100.0, 2.1)), cancel = true))

        assertEquals(1, limitOrdersQueue.size)
        limitOrders = limitOrdersQueue.poll() as LimitOrdersReport
        assertEquals(6, limitOrders.orders.size)
        assertEquals(1.2, limitOrders.orders[0].order.price)
        assertEquals(1.3, limitOrders.orders[1].order.price)
        assertEquals(1.4, limitOrders.orders[2].order.price)
        assertEquals(1.5, limitOrders.orders[3].order.price)
        assertEquals(2.0, limitOrders.orders[4].order.price)
        assertEquals(2.1, limitOrders.orders[5].order.price)
    }

    @Test
    fun testAddAndMatchLimitOrder() {
        service.processMessage(buildMultiLimitOrderWrapper(pair = "EURUSD", clientId = "Client1", volumes = listOf(VolumePrice(100.0, 1.3), VolumePrice(100.0, 1.2))))

        assertEquals(1, limitOrdersQueue.size)
        limitOrdersQueue.poll()

        val singleOrderService = initSingleLimitOrderService()
        singleOrderService.processMessage(buildLimitOrderWrapper(buildLimitOrder(clientId = "Client2", price = 1.25, volume = -150.0)))

        assertEquals(1, trustedLimitOrdersQueue.size)
        var result = trustedLimitOrdersQueue.poll() as LimitOrdersReport

        assertEquals(OrderStatus.Processing.name, result.orders[0].order.status)
        assertEquals(-50.0, result.orders[0].order.remainingVolume)
        assertEquals(OrderStatus.Matched.name, result.orders[1].order.status)
        assertEquals(1.3, result.orders[1].order.price)

        assertEquals(870.0, testWalletDatabaseAccessor.getBalance("Client1", "USD"))
        assertEquals(1100.0, testWalletDatabaseAccessor.getBalance("Client1", "EUR"))
        assertEquals(0.0, testWalletDatabaseAccessor.getReservedBalance("Client1", "USD"))

        assertEquals(1130.0, testWalletDatabaseAccessor.getBalance("Client2", "USD"))
        assertEquals(900.0, testWalletDatabaseAccessor.getBalance("Client2", "EUR"))
        assertEquals(50.0, testWalletDatabaseAccessor.getReservedBalance("Client2", "EUR"))

        service.processMessage(buildMultiLimitOrderWrapper(pair = "EURUSD", clientId = "Client1", volumes = listOf(VolumePrice(10.0, 1.3), VolumePrice(100.0, 1.26), VolumePrice(100.0, 1.2)), cancel = true))

        assertEquals(1, trustedLimitOrdersQueue.size)
        result = trustedLimitOrdersQueue.poll() as LimitOrdersReport

        assertEquals(3, result.orders.size)
        assertEquals(OrderStatus.Matched.name, result.orders[0].order.status)
        assertEquals(0.0, result.orders[0].order.remainingVolume)
        assertEquals(1.3, result.orders[0].order.price)
        assertEquals(OrderStatus.Matched.name, result.orders[1].order.status)
        assertEquals(1.25, result.orders[1].order.price)
        assertEquals(OrderStatus.Processing.name, result.orders[2].order.status)
        assertEquals(60.0, result.orders[2].order.remainingVolume)
        assertEquals(1.26, result.orders[2].order.price)

        assertEquals(807.5, testWalletDatabaseAccessor.getBalance("Client1", "USD"))
        assertEquals(1150.0, testWalletDatabaseAccessor.getBalance("Client1", "EUR"))
        assertEquals(0.0, testWalletDatabaseAccessor.getReservedBalance("Client1", "USD"))

        assertEquals(1192.5, testWalletDatabaseAccessor.getBalance("Client2", "USD"))
        assertEquals(850.0, testWalletDatabaseAccessor.getBalance("Client2", "EUR"))
        assertEquals(0.0, testWalletDatabaseAccessor.getReservedBalance("Client2", "EUR"))
    }

    @Test
    fun testAddAndMatchLimitOrder2() {
        service.processMessage(buildMultiLimitOrderWrapper(pair = "EURUSD", clientId = "Client1", volumes = listOf(VolumePrice(-100.0, 1.2), VolumePrice(-100.0, 1.3))))

        assertEquals(1, limitOrdersQueue.size)
        limitOrdersQueue.poll()

        val singleOrderService = initSingleLimitOrderService()
        singleOrderService.processMessage(buildLimitOrderWrapper(buildLimitOrder(clientId = "Client2", price = 1.25, volume = 150.0)))

        assertEquals(1, trustedLimitOrdersQueue.size)
        var result = trustedLimitOrdersQueue.poll() as LimitOrdersReport

        assertEquals(OrderStatus.Processing.name, result.orders[0].order.status)
        assertEquals(50.0, result.orders[0].order.remainingVolume)
        assertEquals(OrderStatus.Matched.name, result.orders[1].order.status)
        assertEquals(1.2, result.orders[1].order.price)

        assertEquals(1120.0, testWalletDatabaseAccessor.getBalance("Client1", "USD"))
        assertEquals(900.0, testWalletDatabaseAccessor.getBalance("Client1", "EUR"))
        assertEquals(0.0, testWalletDatabaseAccessor.getReservedBalance("Client1", "USD"))

        assertEquals(880.0, testWalletDatabaseAccessor.getBalance("Client2", "USD"))
        assertEquals(1100.0, testWalletDatabaseAccessor.getBalance("Client2", "EUR"))
        assertEquals(62.5, testWalletDatabaseAccessor.getReservedBalance("Client2", "USD"))

        service.processMessage(buildMultiLimitOrderWrapper(pair = "EURUSD", clientId = "Client1", volumes = listOf(VolumePrice(-10.0, 1.2), VolumePrice(-10.0, 1.24), VolumePrice(-10.0, 1.29), VolumePrice(-10.0, 1.3)), cancel = true))

        assertEquals(1, trustedLimitOrdersQueue.size)
        result = trustedLimitOrdersQueue.poll() as LimitOrdersReport

        assertEquals(3, result.orders.size)
        assertEquals(OrderStatus.Matched.name, result.orders[0].order.status)
        assertEquals(0.0, result.orders[0].order.remainingVolume)
        assertEquals(1.2, result.orders[0].order.price)
        assertEquals(OrderStatus.Processing.name, result.orders[1].order.status)
        assertEquals(30.0, result.orders[1].order.remainingVolume)
        assertEquals(1.25, result.orders[1].order.price)
        assertEquals(OrderStatus.Processing.name, result.orders[1].order.status)
        assertEquals(OrderStatus.Matched.name, result.orders[2].order.status)
        assertEquals(0.0, result.orders[2].order.remainingVolume)
        assertEquals(1.24, result.orders[2].order.price)

        assertEquals(1.25, genericLimitService.getOrderBook("EURUSD").getBidPrice())

        assertEquals(1145.0, testWalletDatabaseAccessor.getBalance("Client1", "USD"))
        assertEquals(880.0, testWalletDatabaseAccessor.getBalance("Client1", "EUR"))
        assertEquals(0.0, testWalletDatabaseAccessor.getReservedBalance("Client1", "USD"))

        assertEquals(855.0, testWalletDatabaseAccessor.getBalance("Client2", "USD"))
        assertEquals(1120.0, testWalletDatabaseAccessor.getBalance("Client2", "EUR"))
        assertEquals(37.5, testWalletDatabaseAccessor.getReservedBalance("Client2", "USD"))
    }

    @Test
    fun testAddAndMatchLimitOrder3() {
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client5", "USD", 18.6))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client5", "TIME", 1000.0))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client2", "TIME", 1000.0))

        service.processMessage(buildMultiLimitOrderWrapper(pair = "TIMEUSD", clientId = "Client5", volumes = listOf(VolumePrice(-100.0, 26.955076))))
        service.processMessage(buildMultiLimitOrderWrapper(pair = "TIMEUSD", clientId = "Client5", volumes = listOf(VolumePrice(0.69031943, 26.915076))))

        assertEquals(2, limitOrdersQueue.size)
        limitOrdersQueue.poll()
        limitOrdersQueue.poll()

        val singleOrderService = initSingleLimitOrderService()
        singleOrderService.processMessage(buildLimitOrderWrapper(buildLimitOrder(assetId = "TIMEUSD", clientId = "Client2", price = 26.88023, volume = -26.0)))

        assertEquals(1, trustedLimitOrdersQueue.size)
        val result = trustedLimitOrdersQueue.poll() as LimitOrdersReport

        assertEquals(OrderStatus.Processing.name, result.orders[0].order.status)
        assertEquals(-25.30968057, result.orders[0].order.remainingVolume)
        assertEquals(OrderStatus.Matched.name, result.orders[1].order.status)
        assertEquals(26.915076, result.orders[1].order.price)

        var orderBook = genericLimitService.getOrderBook("TIMEUSD")
        assertEquals(2, orderBook.getOrderBook(false).size)
        var bestAskOrder = orderBook.getOrderBook(false).peek()
        assertEquals(26.88023, bestAskOrder.price)
        assertEquals(-26.0, bestAskOrder.volume)
        assertEquals(-25.30968057, bestAskOrder.remainingVolume)

        assertEquals(0, orderBook.getOrderBook(true).size)

        assertEquals(0.02, testWalletDatabaseAccessor.getBalance("Client5", "USD"))
        assertEquals(1000.69031943, testWalletDatabaseAccessor.getBalance("Client5", "TIME"))
        assertEquals(0.0, testWalletDatabaseAccessor.getReservedBalance("Client5", "USD"))

        assertEquals(1018.58, testWalletDatabaseAccessor.getBalance("Client2", "USD"))
        assertEquals(999.30968057, testWalletDatabaseAccessor.getBalance("Client2", "TIME"))
        assertEquals(25.30968057, testWalletDatabaseAccessor.getReservedBalance("Client2", "TIME"))

        service.processMessage(buildMultiLimitOrderWrapper(pair = "TIMEUSD", clientId = "Client5", volumes = listOf(VolumePrice(10.0, 26.915076), VolumePrice(10.0, 26.875076)), cancel = true))

        assertEquals(0, trustedLimitOrdersQueue.size)

        orderBook = genericLimitService.getOrderBook("TIMEUSD")
        assertEquals(2, orderBook.getOrderBook(false).size)
        bestAskOrder = orderBook.getOrderBook(false).peek()
        assertEquals(26.88023, bestAskOrder.price)
        assertEquals(-26.0, bestAskOrder.volume)
        assertEquals(-25.30968057, bestAskOrder.remainingVolume)

        assertEquals(1, orderBook.getOrderBook(true).size)
    }

    @Test
    fun testAddAndMatchLimitOrderZeroVolumes() {
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client5", "BTC", 1000.0))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client2", "EUR", 1000.0))

        val singleOrderService = initSingleLimitOrderService()
        singleOrderService.processMessage(buildLimitOrderWrapper(buildLimitOrder(assetId = "BTCEUR", clientId = "Client2", price = 3629.355, volume = 0.19259621)))

        assertEquals(1, trustedLimitOrdersQueue.size)
        var result = trustedLimitOrdersQueue.poll() as LimitOrdersReport

        assertEquals(OrderStatus.InOrderBook.name, result.orders[0].order.status)
        assertEquals(0.19259621, result.orders[0].order.remainingVolume)
        assertEquals(699.01, result.orders[0].order.reservedLimitVolume!!)
        assertEquals(699.01, testWalletDatabaseAccessor.getReservedBalance("Client2", "EUR"))

        service.processMessage(buildMultiLimitOrderWrapper(pair = "BTCEUR", clientId = "Client5", volumes = listOf(VolumePrice(-0.00574996, 3628.707)), cancel = true))
        result = trustedLimitOrdersQueue.poll() as LimitOrdersReport
        assertEquals(OrderStatus.Matched.name, result.orders[0].order.status)
        assertEquals(OrderStatus.Processing.name, result.orders[1].order.status)
        assertEquals(0.18684625, result.orders[1].order.remainingVolume)
        assertEquals(678.15, result.orders[1].order.reservedLimitVolume!!)
        assertEquals(678.15, testWalletDatabaseAccessor.getReservedBalance("Client2", "EUR"))

        service.processMessage(buildMultiLimitOrderWrapper(pair = "BTCEUR", clientId = "Client5", volumes = listOf(VolumePrice(-0.01431186, 3624.794), VolumePrice(-0.02956591, 3626.591)), cancel = true))
        result = trustedLimitOrdersQueue.poll() as LimitOrdersReport
        assertEquals(OrderStatus.Matched.name, result.orders[0].order.status)
        assertEquals(OrderStatus.Processing.name, result.orders[1].order.status)
        assertEquals(0.14296848, result.orders[1].order.remainingVolume)
        assertEquals(518.91, result.orders[1].order.reservedLimitVolume!!)
        assertEquals(518.91, testWalletDatabaseAccessor.getReservedBalance("Client2", "EUR"))
        assertEquals(OrderStatus.Matched.name, result.orders[2].order.status)

        service.processMessage(buildMultiLimitOrderWrapper(pair = "BTCEUR", clientId = "Client5", volumes = listOf(VolumePrice(-0.04996673, 3625.855)), cancel = true))
        result = trustedLimitOrdersQueue.poll() as LimitOrdersReport
        assertEquals(OrderStatus.Matched.name, result.orders[0].order.status)
        assertEquals(OrderStatus.Processing.name, result.orders[1].order.status)
        assertEquals(0.09300175, result.orders[1].order.remainingVolume)
        assertEquals(337.57, result.orders[1].order.reservedLimitVolume!!)
        assertEquals(337.57, testWalletDatabaseAccessor.getReservedBalance("Client2", "EUR"))

        service.processMessage(buildMultiLimitOrderWrapper(pair = "BTCEUR", clientId = "Client5", volumes = listOf(VolumePrice(-0.00628173, 3622.865), VolumePrice(-0.01280207, 3625.489), VolumePrice(-0.02201331, 3627.41), VolumePrice(-0.02628901, 3629.139)), cancel = true))
        result = trustedLimitOrdersQueue.poll() as LimitOrdersReport
        assertEquals(OrderStatus.Matched.name, result.orders[0].order.status)
        assertEquals(OrderStatus.Processing.name, result.orders[1].order.status)
        assertEquals(0.02561563, result.orders[1].order.remainingVolume)
        assertEquals(93.02, result.orders[1].order.reservedLimitVolume!!)
        assertEquals(93.02, testWalletDatabaseAccessor.getReservedBalance("Client2", "EUR"))
        assertEquals(OrderStatus.Matched.name, result.orders[2].order.status)
        assertEquals(OrderStatus.Matched.name, result.orders[3].order.status)
        assertEquals(OrderStatus.Matched.name, result.orders[4].order.status)

        service.processMessage(buildMultiLimitOrderWrapper(pair = "BTCEUR", clientId = "Client5", volumes = listOf(VolumePrice(-0.01708411, 3626.11)), cancel = true))
        result = trustedLimitOrdersQueue.poll() as LimitOrdersReport
        assertEquals(OrderStatus.Matched.name, result.orders[0].order.status)
        assertEquals(OrderStatus.Processing.name, result.orders[1].order.status)
        assertEquals(0.00853152, result.orders[1].order.remainingVolume)
        assertEquals(31.02, result.orders[1].order.reservedLimitVolume!!)
        assertEquals(31.02, testWalletDatabaseAccessor.getReservedBalance("Client2", "EUR"))

        service.processMessage(buildMultiLimitOrderWrapper(pair = "BTCEUR", clientId = "Client5", volumes = listOf(VolumePrice(-0.00959341, 3625.302)), cancel = true))
        result = trustedLimitOrdersQueue.poll() as LimitOrdersReport
        assertEquals(OrderStatus.Processing.name, result.orders[0].order.status)
        assertEquals(OrderStatus.Matched.name, result.orders[1].order.status)
        assertEquals(0.0, result.orders[1].order.remainingVolume)
        assertEquals(0.0, result.orders[1].order.reservedLimitVolume!!)
        assertEquals(0.0, testWalletDatabaseAccessor.getReservedBalance("Client2", "EUR"))

        val orderBook = genericLimitService.getOrderBook("BTCEUR")
        assertEquals(1, orderBook.getOrderBook(false).size)
        val bestAskOrder = orderBook.getOrderBook(false).peek()
        assertEquals(3625.302, bestAskOrder.price)
        assertEquals(-0.00959341, bestAskOrder.volume)
        assertEquals(-0.00106189, bestAskOrder.remainingVolume)

        assertEquals(0, orderBook.getOrderBook(true).size)
    }

    @Test
    fun testAddAndMatchAndCancel() {
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client2", "BTC", 0.26170853, 0.001))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client3", "CHF", 1000.0))

        val singleLimitOrderService = initSingleLimitOrderService()
        singleLimitOrderService.processMessage(buildLimitOrderWrapper(buildLimitOrder(clientId = "Client2", assetId = "BTCCHF", uid = "1", price = 4384.15, volume = -0.26070853)))

        assertEquals(1, trustedLimitOrdersQueue.size)
        var result = trustedLimitOrdersQueue.poll() as LimitOrdersReport
        assertEquals(OrderStatus.InOrderBook.name, result.orders[0].order.status)
        assertEquals(0.26170853, testWalletDatabaseAccessor.getBalance("Client2", "BTC"))
        assertEquals(0.26170853, testWalletDatabaseAccessor.getReservedBalance("Client2", "BTC"))

        service.processMessage(buildMultiLimitOrderWrapper(pair = "BTCCHF", clientId = "Client3", volumes = listOf(VolumePrice(0.00643271, 4390.84), VolumePrice(0.01359005, 4387.87), VolumePrice(0.02033985, 4384.811)), cancel = true))
        result = trustedLimitOrdersQueue.poll() as LimitOrdersReport
        assertEquals(OrderStatus.Matched.name, result.orders[0].order.status)
        assertEquals(OrderStatus.Processing.name, result.orders[1].order.status)
        assertEquals(-0.22034592, result.orders[1].order.remainingVolume)
        assertEquals(0.22034592, result.orders[1].order.reservedLimitVolume!!)
        assertEquals(0.22134592, testWalletDatabaseAccessor.getReservedBalance("Client2", "BTC"))
        assertEquals(OrderStatus.Matched.name, result.orders[2].order.status)
        assertEquals(OrderStatus.Matched.name, result.orders[3].order.status)

        service.processMessage(buildMultiLimitOrderWrapper(pair = "BTCCHF", clientId = "Client3", volumes = listOf(VolumePrice(0.01691068, 4387.21)), cancel = true))
        result = trustedLimitOrdersQueue.poll() as LimitOrdersReport
        assertEquals(OrderStatus.Matched.name, result.orders[0].order.status)
        assertEquals(OrderStatus.Processing.name, result.orders[1].order.status)
        assertEquals(-0.20343524, result.orders[1].order.remainingVolume)
        assertEquals(0.20343524, result.orders[1].order.reservedLimitVolume!!)
        assertEquals(0.20443524, testWalletDatabaseAccessor.getReservedBalance("Client2", "BTC"))

        val cancelService = LimitOrderCancelService(genericLimitService, limitOrdersQueue, assetsHolder, assetsPairsHolder, balancesHolder, orderBookQueue, rabbitOrderBookQueue)
        cancelService.processMessage(MessageBuilder.buildLimitOrderCancelWrapper("1"))
        assertEquals(0.001, testWalletDatabaseAccessor.getReservedBalance("Client2", "BTC"))
    }

    @Test
    fun testBalance() {
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client2", "BTC", 0.26170853, 0.001))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client3", "CHF", 100.0))

        val singleLimitOrderService = initSingleLimitOrderService()
        singleLimitOrderService.processMessage(buildLimitOrderWrapper(buildLimitOrder(clientId = "Client2", assetId = "BTCCHF", uid = "1", price = 4384.15, volume = -0.26070853)))

        assertEquals(1, trustedLimitOrdersQueue.size)
        var result = trustedLimitOrdersQueue.poll() as LimitOrdersReport
        assertEquals(OrderStatus.InOrderBook.name, result.orders[0].order.status)
        assertEquals(0.26170853, testWalletDatabaseAccessor.getBalance("Client2", "BTC"))
        assertEquals(0.26170853, testWalletDatabaseAccessor.getReservedBalance("Client2", "BTC"))

        service.processMessage(buildMultiLimitOrderWrapper(pair = "BTCCHF", clientId = "Client3", volumes = listOf(VolumePrice(0.00643271, 4390.84), VolumePrice(0.01359005, 4387.87), VolumePrice(0.02033985, 4384.811)), cancel = true))
        result = trustedLimitOrdersQueue.poll() as LimitOrdersReport
        assertEquals(OrderStatus.Matched.name, result.orders[0].order.status)
        assertEquals(OrderStatus.Processing.name, result.orders[1].order.status)
        assertEquals(-0.24068577, result.orders[1].order.remainingVolume)
        assertEquals(0.24068577, result.orders[1].order.reservedLimitVolume!!)
        assertEquals(0.24168577, testWalletDatabaseAccessor.getReservedBalance("Client2", "BTC"))
        assertEquals(OrderStatus.Matched.name, result.orders[2].order.status)

        assertEquals(0.0, genericLimitService.getOrderBook("BTCCHF").getBidPrice())
        assertEquals(12.2, testWalletDatabaseAccessor.getBalance("Client3", "CHF"))
    }

    @Test
    fun testMatchWithLimitOrderForAllFunds() {
        val marketMaker = "Client1"
        val client = "Client2"

        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet(client, "EUR", 700.04, reservedBalance = 700.04))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet(marketMaker, "BTC", 2.0))

        testDatabaseAccessor.addLimitOrder(buildLimitOrder(clientId = client, assetId = "BTCEUR", price = 4722.0, volume = 0.14825226))
        genericLimitService = initLimitService()
        service = initService()

        service.processMessage(buildMultiLimitOrderWrapper(pair = "BTCEUR", clientId = marketMaker, volumes = listOf(VolumePrice(-0.4435, 4721.403)), cancel = true))

        assertEquals(1, trustedLimitOrdersQueue.size)
        val result = trustedLimitOrdersQueue.poll() as LimitOrdersReport
        assertEquals(OrderStatus.Matched.name, result.orders[1].order.status)

        assertEquals(0.0, testWalletDatabaseAccessor.getBalance(client, "EUR"))
        assertEquals(0.0, testWalletDatabaseAccessor.getReservedBalance(client, "EUR"))
        assertEquals(0, genericLimitService.getOrderBook("BTCEUR").getOrderBook(true).size)
    }

    private fun initLimitService() = GenericLimitOrderService(testDatabaseAccessor, assetsHolder, assetsPairsHolder, balancesHolder, tradesInfoQueue, quotesNotificationQueue)
    private fun initSingleLimitOrderService() = SingleLimitOrderService(genericLimitService, trustedLimitOrdersQueue, orderBookQueue, rabbitOrderBookQueue, assetsHolder, assetsPairsHolder, emptySet(), balancesHolder, testMarketDatabaseAccessor)
    private fun initService() = MultiLimitOrderService(genericLimitService, limitOrdersQueue, trustedLimitOrdersQueue, orderBookQueue, rabbitOrderBookQueue, assetsHolder, assetsPairsHolder, emptySet(), balancesHolder, testMarketDatabaseAccessor)

    private fun buildMultiLimitOrderWrapper(pair: String, clientId: String, volumes: List<VolumePrice>, cancel: Boolean = false): MessageWrapper {
        return MessageWrapper("Test", MessageType.MULTI_LIMIT_ORDER.type, buildLimitOrder(pair, clientId, volumes, cancel).toByteArray(), null)
    }

    private fun buildLimitOrder(assetPairId: String, clientId: String, volumes: List<VolumePrice>, cancel: Boolean): ProtocolMessages.OldMultiLimitOrder {
        val uid = Date().time
        val orderBuilder = ProtocolMessages.OldMultiLimitOrder.newBuilder()
                .setUid(uid)
                .setTimestamp(uid)
                .setClientId(clientId)
                .setAssetPairId(assetPairId)
                .setCancelAllPreviousLimitOrders(cancel)
        volumes.forEachIndexed { index, volume ->
            orderBuilder.addOrders(ProtocolMessages.OldMultiLimitOrder.Order.newBuilder()
                    .setVolume(volume.volume)
                    .setPrice(volume.price)
                    .build())
        }
        return orderBuilder.build()
    }
}