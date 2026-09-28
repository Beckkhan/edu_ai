package com.eduai.turbofa.db

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Five SELECT-only queries for fueling data (spec 5f). Gathers data from
 * fueling/payment/vendors databases.
 */
class FuelingDataSource(databases: DataSourceFactory) {
    private val fuelingDatabase: Database = databases.fuelingDatabase
    private val paymentDatabase: Database = databases.paymentDatabase
    private val vendorsDatabase: Database = databases.vendorsDatabase

    fun fuelingById(id: String): Map<String, Any?>? =
        transaction(fuelingDatabase) {
            FuelingsTable
                .selectAll()
                .where { FuelingsTable.fuelingId eq id }
                .firstOrNull()
                ?.toColumnMap()
        }

    fun paymentsByUserId(userId: String, limit: Int = PAYMENT_LIMIT): List<Map<String, Any?>> {
        require(limit > 0) { "limit must be positive" }
        return transaction(paymentDatabase) {
            PaymentsTable
                .selectAll()
                .where { PaymentsTable.userId eq userId }
                .orderBy(PaymentsTable.createdAt to SortOrder.DESC)
                .limit(limit)
                .map { it.toColumnMap() }
        }
    }

    fun fuelingOrdersById(id: String): Map<String, Any?>? =
        transaction(vendorsDatabase) {
            FuelingOrdersTable
                .selectAll()
                .where { FuelingOrdersTable.fuelingId eq id }
                .firstOrNull()
                ?.toColumnMap()
        }

    fun fuelingEventsById(id: String, limit: Int = EVENT_LIMIT): List<Map<String, Any?>> {
        require(limit > 0) { "limit must be positive" }
        return transaction(vendorsDatabase) {
            FuelingEventsTable
                .selectAll()
                .where { FuelingEventsTable.fuelingId eq id }
                .orderBy(FuelingEventsTable.createdAt to SortOrder.DESC)
                .limit(limit)
                .map { it.toColumnMap() }
        }
    }

    fun vendorFuelingOrdersById(id: String): Map<String, Any?>? =
        transaction(vendorsDatabase) {
            VendorFuelingOrdersTable
                .selectAll()
                .where { VendorFuelingOrdersTable.fuelingId eq id }
                .firstOrNull()
                ?.toColumnMap()
        }

    private fun ResultRow.toColumnMap(): Map<String, Any?> =
        fieldIndex.entries.associate { (expression, _) ->
            val column = expression as Column<*>
            column.name to this[column]
        }

    companion object {
        // D8's payment list and the event list are capped to keep the tool payload small (R11).
        const val PAYMENT_LIMIT = 10
        const val EVENT_LIMIT = 20
    }
}
