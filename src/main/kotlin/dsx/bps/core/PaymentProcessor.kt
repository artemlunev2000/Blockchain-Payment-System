package dsx.bps.core

import java.math.BigDecimal

class PaymentProcessor {

    // TODO: Implement db-storage for payments
    private val payments: HashMap<String, Payment> = hashMapOf()

    fun createPayment(currency: Currency, amount: BigDecimal, address: String): Payment {
        val pay = Payment(currency, amount, address)
        payments[pay.id] = pay
        return pay
    }

    fun getPayment(id: String): Payment? = payments[id]
}