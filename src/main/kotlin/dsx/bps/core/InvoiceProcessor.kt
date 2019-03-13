package dsx.bps.core

import dsx.bps.core.datamodel.Currency
import dsx.bps.core.datamodel.Invoice
import dsx.bps.core.datamodel.InvoiceStatus
import dsx.bps.core.datamodel.Tx
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import dsx.bps.crypto.common.Tx

class InvoiceProcessor: Observer<Tx> {

    // Default value for now
    private var confirmations = 1

    // TODO: Implement db-storage for invoices
    private val invoices: HashMap<String, Invoice> = hashMapOf()
    private val unpaidInvoices: HashSet<String> = hashSetOf()

    fun createInvoice(currency: Currency, amount: BigDecimal, address: String): Invoice {
        val inv = Invoice(currency, amount, address)
        invoices[inv.id] = inv
        unpaidInvoices.add(inv.id)
        return inv
    }

    fun getInvoice(id: String): Invoice? = invoices[id]

    override fun onComplete() {}

    override fun onSubscribe(d: Disposable) {}

    /** Check for payment in transaction [tx] */
    // TODO: Implement determination of payment by tag. Depending on currency
    override fun onNext(tx: Tx) {
        if (unpaidInvoices.isEmpty())
            return

        unpaidInvoices
            .mapNotNull { id -> invoices[id] }
            .filter { inv -> inv.currency == tx.currency() && inv.address == tx.destination() }
            .forEach {inv ->
                inv.txIds.add(tx.hash())

                if (tx.confirmations() >= confirmations)
                    inv.received += tx.amount()

                if (inv.status == InvoiceStatus.PAID)
                    unpaidInvoices.remove(inv.id)
            }
    }

    override fun onError(e: Throwable) {
        println(e.message + ":\n" + e.stackTrace)
    }
}