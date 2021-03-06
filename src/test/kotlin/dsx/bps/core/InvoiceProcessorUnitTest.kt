package dsx.bps.core

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.source.yaml
import dsx.bps.config.InvoiceProcessorConfig
import dsx.bps.core.datamodel.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito
import io.reactivex.disposables.Disposable
import org.junit.jupiter.api.Nested
import java.io.File
import java.math.BigDecimal

internal class InvoiceProcessorUnitTest {

    private val manager: BlockchainPaymentSystemManager = Mockito.mock(BlockchainPaymentSystemManager::class.java)
    private val invoiceProcessor: InvoiceProcessor
    private val testConfig: Config

    init {
        val initConfig = Config()
        val configFile = File(javaClass.getResource("/TestBpsConfig.yaml").path)
        testConfig = with (initConfig) {
            addSpec(InvoiceProcessorConfig)
            from.yaml.file(configFile)
        }

        testConfig.validateRequired()

        invoiceProcessor = InvoiceProcessor(manager, testConfig)
    }

    @Test
    @DisplayName("create invoice and get invoice test")
    fun createInvoiceTest() {
        val currency = Mockito.mock(Currency::class.java)
        val invoice = invoiceProcessor.createInvoice(currency, BigDecimal.TEN,"testaddress", 1)
        Assertions.assertNotNull(invoiceProcessor.getInvoice(invoice.id))
        Assertions.assertEquals(invoice, invoiceProcessor.getInvoice(invoice.id))
    }

    @Nested
    inner class OnNextTest {
        @Test
        @DisplayName("onNextTest: right tx")
        fun onNextTest1() {
            val tx = Mockito.mock(Tx::class.java)
            Mockito.`when`(tx.currency()).thenReturn(Currency.BTC)
            Mockito.`when`(tx.destination()).thenReturn("testaddress")
            Mockito.`when`(tx.tag()).thenReturn(1)
            Mockito.`when`(tx.amount()).thenReturn(BigDecimal.TEN)
            Mockito.`when`(tx.status()).thenReturn(TxStatus.CONFIRMED)
            Mockito.`when`(tx.txid()).thenReturn(TxId("hash",1))

            val invoice = invoiceProcessor.createInvoice(Currency.BTC, BigDecimal.TEN, "testaddress", 1)
            invoiceProcessor.onNext(tx)
            Assertions.assertEquals(invoice.status, InvoiceStatus.PAID)
            Assertions.assertEquals(invoice.received, BigDecimal.TEN)
            Assertions.assertTrue(invoice.txids.contains(TxId("hash", 1)))
        }

        @Test
        @DisplayName("onNextTest: wrong tx tag")
        fun onNextTest2() {
            val tx1 = Mockito.mock(Tx::class.java)
            Mockito.`when`(tx1.currency()).thenReturn(Currency.BTC)
            Mockito.`when`(tx1.destination()).thenReturn("testaddress")
            Mockito.`when`(tx1.tag()).thenReturn(null)
            Mockito.`when`(tx1.amount()).thenReturn(BigDecimal.TEN)
            Mockito.`when`(tx1.status()).thenReturn(TxStatus.CONFIRMED)
            Mockito.`when`(tx1.txid()).thenReturn(TxId("hash",1))

            val invoice = invoiceProcessor.createInvoice(Currency.BTC, BigDecimal.TEN, "testaddress", 1)
            invoiceProcessor.onNext(tx1)
            Assertions.assertEquals(invoice.status, InvoiceStatus.UNPAID)
            Assertions.assertEquals(invoice.received, BigDecimal.ZERO)
            Assertions.assertFalse(invoice.txids.contains(TxId("hash", 1)))
        }

        @Test
        @DisplayName("onNextTest: wrong tx address")
        fun onNextTest3() {
            val tx1 = Mockito.mock(Tx::class.java)
            Mockito.`when`(tx1.currency()).thenReturn(Currency.BTC)
            Mockito.`when`(tx1.destination()).thenReturn("testaddressother")
            Mockito.`when`(tx1.tag()).thenReturn(1)
            Mockito.`when`(tx1.amount()).thenReturn(BigDecimal.TEN)
            Mockito.`when`(tx1.status()).thenReturn(TxStatus.CONFIRMED)
            Mockito.`when`(tx1.txid()).thenReturn(TxId("hash",1))

            val invoice = invoiceProcessor.createInvoice(Currency.BTC, BigDecimal.TEN, "testaddress", 1)
            invoiceProcessor.onNext(tx1)
            Assertions.assertEquals(invoice.status, InvoiceStatus.UNPAID)
            Assertions.assertEquals(invoice.received, BigDecimal.ZERO)
            Assertions.assertFalse(invoice.txids.contains(TxId("hash", 1)))
        }

        @ParameterizedTest
        @DisplayName("onNextTest: wrong tx currency")
        @EnumSource(value = Currency::class, names = ["TRX", "XRP"])
        fun onNextTest4(currency: Currency) {
            val tx1 = Mockito.mock(Tx::class.java)
            Mockito.`when`(tx1.currency()).thenReturn(currency)
            Mockito.`when`(tx1.destination()).thenReturn("testaddress")
            Mockito.`when`(tx1.tag()).thenReturn(1)
            Mockito.`when`(tx1.amount()).thenReturn(BigDecimal.TEN)
            Mockito.`when`(tx1.status()).thenReturn(TxStatus.CONFIRMED)
            Mockito.`when`(tx1.txid()).thenReturn(TxId("hash",1))

            val invoice = invoiceProcessor.createInvoice(Currency.BTC, BigDecimal.TEN, "testaddress", 1)
            invoiceProcessor.onNext(tx1)
            Assertions.assertEquals(invoice.status, InvoiceStatus.UNPAID)
            Assertions.assertEquals(invoice.received, BigDecimal.ZERO)
            Assertions.assertFalse(invoice.txids.contains(TxId("hash", 1)))
        }

        @ParameterizedTest
        @DisplayName("onNextTest: wrong tx status")
        @EnumSource(value = TxStatus::class, names = ["VALIDATING", "REJECTED"])
        fun onNextTest5(txStatus: TxStatus) {
            val tx1 = Mockito.mock(Tx::class.java)
            Mockito.`when`(tx1.currency()).thenReturn(Currency.TRX)
            Mockito.`when`(tx1.destination()).thenReturn("testaddress")
            Mockito.`when`(tx1.tag()).thenReturn(1)
            Mockito.`when`(tx1.amount()).thenReturn(BigDecimal.TEN)
            Mockito.`when`(tx1.status()).thenReturn(txStatus)
            Mockito.`when`(tx1.txid()).thenReturn(TxId("hash",1))

            val invoice = invoiceProcessor.createInvoice(Currency.BTC, BigDecimal.TEN, "testaddress", 1)
            invoiceProcessor.onNext(tx1)
            Assertions.assertEquals(invoice.status, InvoiceStatus.UNPAID)
            Assertions.assertEquals(invoice.received, BigDecimal.ZERO)
            Assertions.assertFalse(invoice.txids.contains(TxId("hash", 1)))
        }
    }

    @Test
    fun onErrorTest() {
        val e = Mockito.mock(Throwable::class.java)
        invoiceProcessor.onError(e)
    }

    @Test
    fun onCompleteTest() {
        invoiceProcessor.onComplete()
    }

    @Test
    fun onSubscribeTest() {
        val d = Mockito.mock(Disposable::class.java)
        invoiceProcessor.onSubscribe(d)
    }
}