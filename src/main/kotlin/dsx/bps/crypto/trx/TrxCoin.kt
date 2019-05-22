package dsx.bps.crypto.trx

import dsx.bps.core.datamodel.Currency
import dsx.bps.core.datamodel.*
import dsx.bps.crypto.common.Coin
import dsx.bps.crypto.trx.datamodel.TrxBlock
import dsx.bps.crypto.trx.datamodel.TrxTx
import java.math.BigDecimal
import java.util.*
import kotlin.random.Random

class TrxCoin: Coin {

    constructor(): super()
    constructor(conf: Properties): super(conf)
    constructor(confPath: String): super(confPath)

    override val currency = Currency.TRX

    private val account: String
    private val accountAddress: String
    private val privateKey: String

    override val rpc: TrxRpc
    override val explorer: TrxExplorer

    private val confirmations: Int

    init {
        account = config.getProperty("TRX.account", "TW4hF7TVhme1STRC2NToDA41RxCx1H2HbS") // Base58Checksum representation of account
        accountAddress = config.getProperty("TRX.address", "41dc6c2bc46639e5371fe99e002858754604cf5847")
        privateKey = config.getProperty("TRX.privateKey", "92f451c194301b4a1eae3a818cc181e1a319656dcdf6760cdbe35c54b05bb3ec")

        val host = config.getProperty("TRX.ip", "127.0.0.1")
        val port = config.getProperty("TRX.port", "18190")
        val url = "http://$host:$port/wallet/"
        rpc = TrxRpc(url)

        val frequency = config.getProperty("TRX.frequency", "3000").toLong()
        explorer = TrxExplorer(this, frequency)

        confirmations = 3 // 19 for mainnet
    }

    override fun getBalance(): BigDecimal = rpc.getBalance(accountAddress)

    override fun getAddress(): String = accountAddress

    override fun getTag(): Int? = Random.nextInt(0, Int.MAX_VALUE)

    override fun getTx(txid: TxId): Tx {
        val trxTx = rpc.getTransactionById(txid.hash)
        return constructTx(trxTx)
    }

    override fun sendPayment(amount: BigDecimal, address: String, tag: Int?): Tx {
        val tx = rpc.createTransaction(address, accountAddress, amount)
            .let {
                it.rawData.data = tag?.toString(16)
                rpc.getTransactionSign(privateKey, it)
            }

        val result = rpc.broadcastTransaction(tx)

        if (result.success) {
            return constructTx(tx)
        } else {
            throw RuntimeException("Unable to broadcast TRX transaction $tx with response $result")
        }
    }

    fun getNowBlock(): TrxBlock = rpc.getNowBlock()

    fun getBlockByNum(num: Int): TrxBlock = rpc.getBlockByNum(num)

    fun getBlockById(hash: String): TrxBlock = rpc.getBlockById(hash)

    fun constructTx(trxTx: TrxTx): Tx {
        val contract = trxTx.rawData.contract.first()
        val value = contract.parameter.value

        val txInfo = rpc.getTransactionInfoById(trxTx.hash)
        val lastBlock = rpc.getNowBlock()

        return object: Tx {
            override fun currency() = Currency.TRX

            override fun hash() = trxTx.hash

            override fun amount() = value.amount

            override fun destination() = value.toAddress

            override fun fee() = BigDecimal.ZERO

            override fun status(): TxStatus {
                val conf = lastBlock.blockHeader.rawData.number - txInfo.blockNumber
                return when {
                    conf < confirmations -> TxStatus.VALIDATING
                    else -> TxStatus.CONFIRMED
                }
            }

            override fun tag() = trxTx.rawData.data?.toInt(16)
        }
    }
}