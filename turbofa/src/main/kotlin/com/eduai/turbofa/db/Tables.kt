@file:Suppress("MagicNumber")

package com.eduai.turbofa.db

import org.jetbrains.exposed.sql.Table

// Table objects of docs/schema.sql (§6.1). Structure only (R5): no DDL ever runs from here.
// jsonb columns are declared as text — the driver returns their JSON text as-is (5f).

object FuelingsTable : Table("fuelings") {
    val fuelingId = text("fueling_id")
    val vendorFuelingOrderId = text("vendor_fueling_order_id").nullable()
    val userId = text("user_id")
    val status = text("status")
    val amount = decimal("amount", 10, 2)
    val fuelType = text("fuel_type")
    val gasStationId = text("gas_station_id")
    val gasPumpId = text("gas_pump_id")
    val refuelingGunId = text("refueling_gun_id")
    val fuelReservationKey = text("fuel_reservation_key")
    val createdAt = decimal("created_at", 20, 6)
    val updatedAt = decimal("updated_at", 20, 6)
    val actualAmount = decimal("actual_amount", 10, 2).nullable()
    val vendorTransactionDate = text("vendor_transaction_date").nullable()
    val failedReason = text("failed_reason").nullable()
    val vendorFuelPrice = decimal("vendor_fuel_price", 10, 2).nullable()
    val fueledOrders = text("fueled_orders").nullable()
    val discountFuelPrice = decimal("discount_fuel_price", 10, 2).nullable()
    val fuelingType = text("fueling_type")
    val extra = text("extra").nullable()
    val fuelingPaymentType = text("fueling_payment_type")
    val finishedAt = decimal("finished_at", 20, 6).nullable()

    override val primaryKey = PrimaryKey(fuelingId)
}

object PaymentsTable : Table("payments") {
    val paymentId = text("payment_id")
    val orderId = text("order_id")
    val userId = text("user_id")
    val externalPaymentId = text("external_payment_id").nullable()
    val status = text("status").nullable()
    val createdAt = long("created_at").nullable()
    val updatedAt = long("updated_at").nullable()
    val paymentSystem = varchar("payment_system", 255)
    val paymentMethod = varchar("payment_method", 255)
    val paymentType = text("payment_type")
    val purpose = text("purpose")
    val amount = decimal("amount", 10, 2).nullable()
    val actualAmount = decimal("actual_amount", 10, 2).nullable()
    val cardBindingId = text("card_binding_id").nullable()
    val cardBindingType = text("card_binding_type").nullable()
    val sbpSubscriptionId = text("sbp_subscription_id").nullable()

    override val primaryKey = PrimaryKey(paymentId)
}

object FuelingOrdersTable : Table("fueling_orders") {
    val fuelingId = text("fueling_id")
    val volume = decimal("volume", 10, 3)
    val price = decimal("price", 10, 2)
    val fuelType = text("fuel_type")
    val stationId = text("station_id")
    val pumpId = text("pump_id")
    val createdAt = long("created_at")
    val data = text("data")
    val fuelDescription = text("fuel_description").nullable()
    val brand = text("brand")

    override val primaryKey = PrimaryKey(fuelingId)
}

object FuelingEventsTable : Table("fueling_events") {
    val eventOffset = long("event_offset")
    val fuelingId = text("fueling_id")
    val createdAt = long("created_at")
    val status = text("status")
    val data = text("data")
    val brand = text("brand")

    override val primaryKey = PrimaryKey(eventOffset)
}

object VendorFuelingOrdersTable : Table("vendor_fueling_orders") {
    val fuelingId = text("fueling_id")
    val fuelType = text("fuel_type")
    val quantity = decimal("quantity", 10, 3)
    val totalSum = decimal("total_sum", 10, 2)
    val status = text("status")
    val gasPumpNumber = decimal("gas_pump_number", 10, 3)
    val vendorGasStationId = text("vendor_gas_station_id")
    val vendorFuelPrice = decimal("vendor_fuel_price", 10, 2)
    val email = text("email").nullable()
    val phone = text("phone").nullable()
    val dateCreate = text("date_create")
    val createdAt = decimal("created_at", 20, 6)

    override val primaryKey = PrimaryKey(fuelingId)
}
