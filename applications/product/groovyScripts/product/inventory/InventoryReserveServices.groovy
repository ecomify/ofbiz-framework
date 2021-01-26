/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.sql.Timestamp

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilMisc
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.service.ModelService
import org.apache.ofbiz.service.ServiceUtil


/**
 * Reserve Inventory for a Product
 * @return
 */
def reserveProductInventory() {
    Map result = success()
    List inventoryItemAndLocations = []
    GenericValue inventoryItem = null
    GenericValue lastNonSerInventoryItem = null
    Long daysToShip = 0
    Map serviceResult = [:]
    List conditionList = []

    // this method can be called with some optional parameters:
    //    -facilityId
    //    -containerId
    // If the service definitions are used then only one of these two will ever be specified, or neither of them.
    //
    // Whatever it is called with, it will basically get a list of InventoryItems and reserve the first available inventory.
    //
    // If requireInventory is Y the quantity not reserved is returned, if N then a negative
    // availableToPromise will be used to track quantity ordered beyond what is in stock.
    //

    logVerbose("Parameters : ${parameters}")
    Timestamp nowTimestamp = UtilDateTime.nowTimestamp()

    // check the product; make sure its a physical item
    GenericValue product = from("Product").where(parameters).queryOne()
    GenericValue facility = from("Facility").where(parameters).cache().queryOne()

    if (product) {
        GenericValue productType = product.getRelatedOne("ProductType", false)

        if (productType.isPhysical.equals("N")) {
            parameters.quantityNotReserved = (BigDecimal) 0
        }
    }
    else {
        GenericValue orderHeader = from("OrderHeader").where(parameters).queryOne()
        String orderByString = null

        // before we do the find, put together the orderBy list based on which reserveOrderEnumId is specified
        // FIFO=first in first out, so it should be order by ASCending receive or expire date
        // LIFO=last in first out, so it means order by DESCending receive or expire date
        if (parameters.reserveOrderEnumId.equals("INVRO_GUNIT_COST")) {
            orderByString = "-unitCost"
        }
        else if (parameters.reserveOrderEnumId.equals("INVRO_LUNIT_COST")) {
            orderByString = "+unitCost"
        }
        else if (parameters.reserveOrderEnumId.equals("INVRO_FIFO_EXP")) {
            orderByString = "+expireDate"
        }
        else if (parameters.reserveOrderEnumId.equals("INVRO_LIFO_EXP")) {
            orderByString = "-expireDate"
        }
        else if (parameters.reserveOrderEnumId.equals("INVRO_LIFO_REC")) {
            orderByString = "-datetimeReceived"
        }
        // the default reserveOrderEnumId is INVRO_FIFO_REC, ie FIFO based on date received
        else {
            orderByString = "+datetimeReceived"
            parameters.reserveOrderEnumId ="INVRO_FIFO_REC"
        }

        parameters.quantityNotReserved = parameters.quantity

        // first reserve against InventoryItems in FLT_PICKLOC type locations, then FLT_BULK locations, then InventoryItems with no locations
        conditionList = [
            EntityCondition.makeCondition("productId", EntityOperator.EQUALS, parameters.productId),
            EntityCondition.makeCondition("availableToPromiseTotal",  EntityOperator.GREATER_THAN, "0.0"),
            EntityCondition.makeCondition("locationTypeEnumId", EntityOperator.EQUALS, "FLT_PICKLOC"),
            EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, "INV_NS_DEFECTIVE"),
            EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, "INV_DEFECTIVE")
        ]

        if (parameters.facilityId && parameters.facilityId != null) {
            conditionList.add(EntityCondition.makeCondition("facilityId", EntityOperator.EQUALS, parameters.facilityId))
        }
        if (parameters.containerId && parameters.containerId != null) {
            conditionList.add(EntityCondition.makeCondition("containerId", EntityOperator.EQUALS, parameters.containerId))
        }
        if (parameters.lotId && parameters.lotId != null) {
            conditionList.add(EntityCondition.makeCondition("lotId", EntityOperator.EQUALS, parameters.lotId))
        }

        inventoryItemAndLocations = from("InventoryItemAndLocation").where(conditionList).orderBy(orderByString).queryList()

        for (GenericValue inventoryItemAndLocation : inventoryItemAndLocations) {
            if ((Double) parameters.quantityNotReserved > 0.0) {
                // this is a little trick to get the InventoryItem value object without doing a query,
                // possible since all fields on InventoryItem are also on InventoryItemAndLocation with the same names
                inventoryItem = makeValue("InventoryItem", inventoryItemAndLocation)
                Map resultReserve = reserveForInventoryItemInline(inventoryItem)
                lastNonSerInventoryItem = resultReserve.lastNonSerInventoryItem
            }
        }

        // still some left? try the FLT_BULK locations
        if ((BigDecimal) parameters.quantityNotReserved > 0.0 ) {
            conditionList = [
                EntityCondition.makeCondition("productId", EntityOperator.EQUALS, parameters.productId),
                EntityCondition.makeCondition("availableToPromiseTotal",  EntityOperator.GREATER_THAN, "0.0"),
                EntityCondition.makeCondition("locationTypeEnumId", EntityOperator.EQUALS, "FLT_BULK"),
                EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, "INV_NS_DEFECTIVE"),
                EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, "INV_DEFECTIVE")
            ]
            if (parameters.facilityId && parameters.facilityId != null) {
                conditionList.add(EntityCondition.makeCondition("facilityId", EntityOperator.EQUALS, parameters.facilityId))
            }
            if (parameters.containerId && parameters.containerId != null) {
                conditionList.add(EntityCondition.makeCondition("containerId", EntityOperator.EQUALS, parameters.containerId))
            }
            if (parameters.lotId && parameters.lotId != null) {
                conditionList.add(EntityCondition.makeCondition("lotId", EntityOperator.EQUALS, parameters.lotId))
            }

            inventoryItemAndLocations = from("InventoryItemAndLocation").where(conditionList).orderBy(orderByString).queryList()

            for (GenericValue inventoryItemAndLocation : inventoryItemAndLocations) {
                if ((Double) parameters.quantityNotReserved > 0.0) {
                    // this is a little trick to get the InventoryItem value object without doing a query,
                    // possible since all fields on InventoryItem are also on InventoryItemAndLocation with the same names
                    inventoryItem = makeValue("InventoryItem", inventoryItemAndLocation)
                    Map resultReserve = reserveForInventoryItemInline(inventoryItem)
                    lastNonSerInventoryItem = resultReserve.lastNonSerInventoryItem

                }
            }
        }

        // last of all try reserving in InventoryItems that have no locationSeqId, ie are not in any particular location
        if ((BigDecimal) parameters.quantityNotReserved > 0.0) {
            conditionList = [
                EntityCondition.makeCondition("productId", EntityOperator.EQUALS, parameters.productId),
                EntityCondition.makeCondition("availableToPromiseTotal",  EntityOperator.GREATER_THAN, "0.0"),
                EntityCondition.makeCondition("locationSeqId", EntityOperator.EQUALS, null),
                EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, "INV_NS_DEFECTIVE"),
                EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, "INV_DEFECTIVE")
            ]
            if (parameters.facilityId && parameters.facilityId != null) {
                conditionList.add(EntityCondition.makeCondition("facilityId", EntityOperator.EQUALS, parameters.facilityId))
            }
            if (parameters.containerId && parameters.containerId != null) {
                conditionList.add(EntityCondition.makeCondition("containerId", EntityOperator.EQUALS, parameters.containerId))
            }
            if (parameters.lotId && parameters.lotId != null) {
                conditionList.add(EntityCondition.makeCondition("lotId", EntityOperator.EQUALS, parameters.lotId))
            }
            GenericValue inventoryItemsInfo = from("InventoryItemAndLocation").where(conditionList).orderBy(orderByString).queryList()

            for (GenericValue inventoryItemInfo : inventoryItemsInfo) {
                if ((Double) parameters.quantityNotReserved > 0.0 && !inventoryItem.locationSeqId) {
                    Map resultReserve = reserveForInventoryItemInline(inventoryItem)
                    lastNonSerInventoryItem = resultReserve.lastNonSerInventoryItem
                }
            }
        }

        // if inventory is not required for purchase and quantityNotReserved != 0:
        //      - subtract the remaining quantityNotReserved from the availableToPromise of the last non-serialized inventory item
        //      - or if none was found create a non-ser InventoryItem with availableToPromise = -quantityNotReserved
        if ((BigDecimal) parameters.quantityNotReserved != 0.0) {
            if (parameters.requireInventory.equals("Y")) {
                // use this else pattern to accomplish the anything but Y logic, ie if not specified default to inventory NOT required
            }
            else {
                if (lastNonSerInventoryItem) {
                    // subtract from quantityNotReserved from the availableToPromise of existing inventory item
                    // instead of updating InventoryItem, add an InventoryItemDetail
                    Map createDetailMap = [inventoryItemId: lastNonSerInventoryItem.inventoryItemId, orderId: parameters.orderId,
                        orderItemSeqId: parameters.orderItemSeqId, shipGroupSeqId: parameters.shipGroupSeqId]
                    BigDecimal availableToPromiseDiff = createDetailMap.availableToPromiseDiff - parameters.quantityNotReserved
                    createDetailMap.availableToPromiseDiff = availableToPromiseDiff.setScale(6)

                    if(parameters.reserveReasonEnumId) {
                        createDetailMap.reasonEnumId = parameters.reserveReasonEnumId
                    }
                    serviceResult = run service: "createInventoryItemDetail", with: createDetailMap
                    if (!ServiceUtil.isSuccess(serviceResult)) {
                        return error(serviceResult.errorMessage)
                    }
                    createDetailMap.clear()

                    // get the promiseDatetime
                    GenericValue productFacility = lastNonSerInventoryItem.getRelatedOne("ProductFacility", false)
                    daysToShip = productFacility.daysToShip
                    if (!daysToShip) {
                        // if the product does not have its own days to ship, use Facility.defaultDaysToShip,
                        // if not then use 30 days as a USA industry default
                        if (facility.defaultDaysToShip) {
                            daysToShip = (Long) facility.defaultDaysToShip
                        }
                        else {
                            daysToShip = (Long) 30
                        }

                    }

                    //TODO war vorher groovy Script
                    Timestamp orderDate = orderHeader.getTimestamp("orderDate")
                    Calendar cal = Calendar.getInstance()
                    cal.setTimeInMillis(orderDate.getTime())
                    cal.add(Calendar.DAY_OF_YEAR, daysToShip.intValue())
                    return UtilMisc.toMap("promisedDatetime", new Timestamp(cal.getTimeInMillis()))

                    // create or update OrderItemShipGrpInvRes record
                    Map reserveOisgirMap = [orderId: parameters.orderId, orderItemSeqId: parameters.orderItemSeqId,
                        shipGroupSeqId: parameters.shipGroupSeqId, inventoryItemId: lastNonSerInventoryItem.inventoryItemId,
                        reserveOrderEnumId: parameters.reserveOrderEnumId]
                    reserveOisgirMap.quantity = (BigDecimal) parameters.quantityNotReserved
                    reserveOisgirMap.quantityNotAvailable = (BigDecimal) parameters.quantityNotReserved
                    reserveOisgirMap.reservedDatetime = parameters.reservedDatetime
                    reserveOisgirMap.promisedDatetime = promisedDatetime
                    reserveOisgirMap.sequenceId = parameters.sequenceId
                    reserveOisgirMap.priority = parameters.priority
                    serviceResult = run service: "reserveOrderItemInventory", with: reserveOisgirMap
                    if (!ServiceUtil.isSuccess(serviceResult)) {
                        return error(serviceResult.errorMessage)
                    }
                    reserveOisgirMap.clear()
                }
                else {
                    // no non-ser inv item, create a non-ser InventoryItem with availableToPromise = -quantityNotReserved
                    Map createInventoryItemInMap = [:]
                    Map createInventoryItemOutMap = [:]
                    // the createInventoryItem service is run by the the system user here
                    GenericValue permUserLogin = from("UserLogin").where(userLoginId: "system").queryOne()
                    createInventoryItemInMap.productId = parameters.productId
                    createInventoryItemInMap.facilityId = parameters.facilityId
                    createInventoryItemInMap.containerId = parameters.containerId
                    createInventoryItemInMap.inventoryItemTypeId = "NON_SERIAL_INV_ITEM"
                    createInventoryItemInMap.userLogin = permUserLogin
                    serviceResult = run service: "createInventoryItem", with: createInventoryItemInMap
                    if (!ServiceUtil.isSuccess(serviceResult)) {
                        return error(serviceResult.errorMessage)
                    }
                    createInventoryItemOutMap.inventoryItemId = serviceResult.inventoryItemId
                    GenericValue newNonSerInventoryItem = from("InventoryItem").where(inventoryItemId: createInventoryItemOutMap.inventoryItemId).queryOne()

                    // also create a detail record with the quantities
                    Map createDetailMap = [inventoryItemId: newNonSerInventoryItem.inventoryItemId, orderId: parameters.orderId,
                        orderItemSeqId: parameters.orderItemSeqId, shipGroupSeqId: parameters.shipGroupSeqId]
                    BigDecimal availableToPromiseDiff = createDetailMap.availableToPromiseDiff - parameters.quantityNotReserved
                    createDetailMap.availableToPromiseDiff = availableToPromiseDiff.setScale(6)
                    if ( parameters.reserveReasonEnumId) {
                        createDetailMap.reasonEnumId = parameters.reserveReasonEnumId

                    }
                    serviceResult = run service: "createInventoryItemDetail", with: createDetailMap
                    if (!ServiceUtil.isSuccess(serviceResult)) {
                        return error(serviceResult.errorMessage)
                    }
                    createDetailMap.clear()

                    // get the promiseDatetime
                    GenericValue productFacility = newNonSerInventoryItem.getRelatedOne("ProductFacility", false)
                    daysToShip.clear()
                    daysToShip = productFacility.daysToShip
                    if (!daysToShip) {
                        // if the product does not have its own days to ship, use Facility.defaultDaysToShip,
                        // if not then use 30 days as a USA industry default
                        if (facility.defaultDaysToShip) {
                            daysToShip = (Long) facility.defaultDaysToShip
                        }
                        else {
                            daysToShip = (Long) 30
                        }

                    }
                    java.sql.Timestamp orderDate = orderHeader.getTimestamp("orderDate")
                    com.ibm.icu.util.Calendar cal = com.ibm.icu.util.Calendar.getInstance()
                    cal.setTimeInMillis(orderDate.getTime())
                    cal.add(com.ibm.icu.util.Calendar.DAY_OF_YEAR, daysToShip.intValue())
                    return org.apache.ofbiz.base.util.UtilMisc.toMap("promisedDatetime", new java.sql.Timestamp(cal.getTimeInMillis()))


                    // create OrderItemShipGrpInvRes record
                    Map reserveOisgirMap = [orderId : parameters.orderId,
                        orderItemSeqId: parameters.orderItemSeqId,
                        shipGroupSeqId: parameters.shipGroupSeqId,
                        inventoryItemId: newNonSerInventoryItem.inventoryItemId,
                        reserveOrderEnumId: parameters.reserveOrderEnumId]
                    reserveOisgirMap.quantity = (BigDecimal) parameters.quantityNotReserved
                    reserveOisgirMap.quantityNotAvailable = (BigDecimal) parameters.quantityNotReserved
                    reserveOisgirMap.reservedDatetime = parameters.reservedDatetime
                    reserveOisgirMap.promisedDatetime = promisedDatetime
                    reserveOisgirMap.sequenceId = parameters.sequenceId
                    reserveOisgirMap.priority = parameters.priority
                    serviceResult = run service: "reserveOrderItemInventory", with: reserveOisgirMap
                    if (!ServiceUtil.isSuccess(serviceResult)) {
                        return error(serviceResult.errorMessage)
                    }
                    reserveOisgirMap.clear()
                }
                parameters.quantityNotReserved = (BigDecimal) 0.0
            }
        }

    }
    result.put("quantityNotReserved", parameters.quantityNotReserved)

    return result
}

/**
 * Reserve a Specific Serialized InventoryItem
 * @return
 */
def reserveAnInventoryItem() {
    // Well the InventoryItem I want to reserve is already reserved, But my customer wants just this inventoryItem.
    // Let me find the reservation on this inventory, cancel it and re-reserve something else for the other order.
    // This way I'll get what I want and the other orderItem will also have a similar thing to issue.

    Map result = success()
    Map serviceResult = [:]
    GenericValue inventoryItem = from("InventoryItem").where(parameters).queryOne()
    String facilityId = inventoryItem.facilityId
    Map inventoryReservationLookUp = [inventoryItemId: inventoryItem.inventoryItemId]
    if (inventoryItem.inventoryItemTypeId.equals("NON_SERIAL_INV_ITEM")) {
        // Reservation was holding on to a InventoryItem shadow, Reduce number of Shadow's available
        Map createDetailMap = [inventoryItemId: inventoryItem.inventoryItemId, orderId: parameters.orderId, orderItemSeqId: parameters.orderItemSeqId]
        createDetailMap.quantityOnHandDiff = (BigDecimal) -1
        createDetailMap.availableToPromiseDiff = (BigDecimal) -1
        serviceResult = run service: "createInventoryItemDetail", with: createDetailMap
        if (!ServiceUtil.isSuccess(serviceResult)) {
            return error(serviceResult.errorMessage)
        }
    }
    Map cancelOrderItemShipGrpInvResMap = dispatcher.getDispatchContext().makeValidContext("cancelOrderItemShipGrpInvRes", ModelService.IN_PARAM, parameters)
    cancelOrderItemShipGrpInvResMap.cancelQuantity = parameters.quantity
    // Step 1 cancel our reservation, we'll later reserve Inventory we want
    serviceResult = run service: "cancelOrderItemShipGrpInvRes", with: cancelOrderItemShipGrpInvResMap
    if (!ServiceUtil.isSuccess(serviceResult)) {
        return error(serviceResult.errorMessage)
    }
    // Lets find the inventory to reserve
    List inventoryItems = from("InventoryItem").where(productId: parameters.productId, inventoryItemTypeId: "SERIALIZED_INV_ITEM", serialNumber: parameters.serialNumber)
    inventoryItem.clear()
    inventoryItem = inventoryItems.get(0)
    // If no inventory item found for the serial number, than create it
    if (!inventoryItem) {
        Map receiveCtx = [productId: parameters.productId, facilityId: facilityId, quantityAccepted: parameters.quantity]
        receiveCtx.quantityRejected = (BigDecimal) 0.0
        receiveCtx.inventoryItemTypeId = "SERIALIZED_INV_ITEM"
        receiveCtx.serialNumber = parameters.serialNumber
        serviceResult = run serivce: "receiveInventoryProduct", with: receiveCtx
        if (!ServiceUtil.isSuccess(serviceResult)) {
            return error(serviceResult.errorMessage)
        }
        Map inventoryItemLookupPk = [inventoryItemId: serviceResult.inventoryItemId]
        inventoryItem = from("InventoryItem").where(inventoryItemLookupPk).queryOne()

    }

    // Step 2 Check if its reserved for other order
    inventoryReservationLookUp.inventoryItemId = inventoryItem.inventoryItemId
    GenericValue inventoryItemReservation = from("OrderItemShipGrpInvRes").where(inventoryReservationLookUp).queryFirst()
    if (inventoryItemReservation) {
        cancelOrderItemShipGrpInvResMap = dispatcher.getDispatchContext().makeValidContext("cancelOrderItemShipGrpInvRes", ModelService.IN_PARAM, inventoryItemReservation)
        serviceResult = run service: "cancelOrderItemShipGrpInvRes", with: cancelOrderItemShipGrpInvResMap
        if (!ServiceUtil.isSuccess(serviceResult)) {
            return error(serviceResult.errorMessage)
        }
        // Hold our inventoryItem
        inventoryItem.refresh()
        inventoryItem.statusId = "INV_PROMISED"
        inventoryItem.store()
        // get something else for other order
        // store OrderItemShipGrpInvRes record
        Map reserveOisgirMap = [orderId: inventoryItemReservation.orderId, productId: parameters.productId,
            orderItemSeqId: inventoryItemReservation.orderItemSeqId, shipGroupSeqId: inventoryItemReservation.shipGroupSeqId,
            reserveOrderEnumId: inventoryItemReservation.reserveOrderEnumId, reservedDatetime: inventoryItemReservation.reservedDatetime,
            quantity: (BigDecimal) 1.0, requireInventory: parameters.requireInventory]
        if (inventoryItemReservation.sequenceId) {
            reserveOisgirMap.sequenceId = inventoryItemReservation.sequenceId
        }
        reserveOisgirMap.priority = parameters.priority
        serviceResult = run service: "reserveProductInventory", with: reserveOisgirMap
        if (!ServiceUtil.isSuccess(serviceResult)) {
            return error(serviceResult.errorMessage)
        }
        reserveOisgirMap.clear()
    }
    // Step 3 Now Reserve for our order
    if (inventoryItem.statusId.equals("INV_AVAILABLE")) {
        // change status on inventoryItem
        inventoryItem.statusId = "INV_PROMISED"
        inventoryItem.store()
    }
    Map reserveOisgirMap = [orderId: parameters.orderId, orderItemSeqId: parameters.orderItemSeqId, shipGroupSeqId: parameters.shipGroupSeqId,
        inventoryItemId: inventoryItem.inventoryItemId, reserveOrderEnumId: parameters.reserveOrderEnumId, reservedDatetime: parameters.reservedDatetime,
        promisedDatetime: promisedDatetime, quantity: (BigDecimal) 1.0]
    if (parameters.sequenceId) {
        reserveOisgirMap.sequenceId = parameters.sequenceId
    }
    // store OrderItemShipGrpInvRes record
    reserveOisgirMap.priority = parameters.priority
    serviceResult = run service: "reserveOrderItemInventory", with: reserveOisgirMap
    if (!ServiceUtil.isSuccess(serviceResult)) {
        return error(serviceResult.errorMessage)
    }
    reserveOisgirMap.clear()
    result.inventoryItemId = inventoryItem.inventoryItemId

    return result
}

/**
 * Does a reservation for one InventoryItem, meant to be called in-line
 * @return
 */
def reserveForInventoryItemInline(GenericValue inventoryItem) {
    Map result = success()
    GenericValue lastNonSerInventoryItem = null
    // only do something with this inventoryItem if there is more inventory to reserve
    if (parameters.quantityNotReserved > (BigDecimal) 0.0) {
        if(inventoryItem.inventoryItemTypeId.equals("SERIALIZED_INV_ITEM")) {
            if (inventoryItem.statusId.equals("INV_AVAILABLE")) {
                // change status on inventoryItem
                inventoryItem.statusId = "INV_PROMISED"
                inventoryItem.store()

                // store OrderItemShipGrpInvRes record
                getPromisedDateTime(inventoryItem)
                Map reserveOisgirMap = [orderId: parameters.orderId, orderItemSeqId: parameters.orderItemSeqId, shipGroupSeqId: parameters.shipGroupSeqId, inventoryItemId: inventoryItem.inventoryItemId,
                    reserveOrderEnumId: parameters.reserveOrderEnumId, reservedDatetime: parameters.reservedDatetime, promisedDatetime: promisedDatetime, quantity: (BigDecimal) 1.0]
                if (parameters.sequenceId) {
                    reserveOisgirMap.sequenceId = parameters.sequenceId
                }
                Map serviceResult = run serivce: "reserveOrderItemInventory", with: reserveOisgirMap
                if (!ServiceUtil.isSuccess(serviceResult)) {
                    return error(serviceResult.errorMessage)
                }
                reserveOisgirMap.clear()
                parameters.quantityNotReserved = parameters.quantityNotReserved - 1.0

            }
        }
        if (inventoryItem.inventoryItemTypeId.equals("NON_SERIAL_INV_ITEM")) {
            // check reasonenumId reserve for ebay inventory
            if(parameters.reserveReasonEnumId) {
                if(parameters.reserveReasonEnumId.equals("EBAY_INV_RES")) {
                    String ebayReserveReasonEnumId = parameters.reserveReasonEnumId
                }
            }
            // reduce atp on inventoryItem if availableToPromise greater than 0, if not the code at the end of this method will handle it
            if (!(inventoryItem.statusId.equals("INV_NS_ON_HOLD")) && !(inventoryItem.statusId.equals("INV_NS_DEFECTIVE"))
            && inventoryItem.availableToPromiseTotal && inventoryItem.availableToPromiseTotal > (BigDecimal) 0.0) {
                if ((BigDecimal) parameters.quantityNotReserved > (BigDecimal) inventoryItem.availableToPromiseTotal) {
                    parameters.deductAmount = inventoryItem.availableToPromiseTotal
                }
                else {
                    parameters.deductAmount = parameters.quantityNotReserved
                }

                // instead of updating InventoryItem, add an InventoryItemDetail
                Map createDetailMap = [inventoryItemId: inventoryItem.inventoryItemId, orderId: parameters.orderId, orderItemSeqId: parameters.orderItemSeqId]
                BigDecimal availableToPromiseDiff = createDetailMap.availableToPromiseDiff - parameters.deductAmount
                createDetailMap.availableToPromiseDiff = availableToPromiseDiff.setScale(6)
                if (ebayReserveReasonEnumId) {
                    createDetailMap.reasonEnumId = parameters.reserveReasonEnumId
                }
                Map serviceResult = run service: "createInventoryItemDetail", with: createDetailMap
                if (!ServiceUtil.isSuccess(serviceResult)) {
                    return error(serviceResult.errorMessage)
                }
                createDetailMap.clear()
                // create OrderItemShipGrpInvRes record  and check for reserved from ebay don't need shipgroup
                if (!ebayReserveReasonEnumId) {
                    getPromisedDateTime(inventoryItem)
                    Map reserveOisgirMap = [orderId: parameters.orderId, orderItemSeqId: parameters.orderItemSeqId,
                        shipGroupSeqId: parameters.shipGroupSeqId, inventoryItemId: inventoryItem.inventoryItemId,
                        reserveOrderEnumId: parameters.reserveOrderEnumId, reservedDatetime: parameters.reservedDatetime,
                        quantity: (BigDecimal) parameters.deductAmount, promisedDatetime: promisedDatetime,
                        priority: parameters.priority]
                    if (parameters.sequenceId) {
                        reserveOisgirMap.sequenceId = parameters.sequenceId
                    }
                    serviceResult = run service: "reserveOrderItemInventory", with: reserveOisgirMap
                    if (!ServiceUtil.isSuccess(serviceResult)) {
                        return error(serviceResult.errorMessage)
                    }
                    reserveOisgirMap.clear()
                }
                BigDecimal quantityNotReserved = parameters.quantityNotReserved - parameters.deductAmount
                parameters.quantityNotReserved = quantityNotReserved.setScale(6)

            }
            // keep track of the last non-serialized inventory item for use if inventory is not required for purchase
            // use env variable named lastNonSerInventoryItem
            lastNonSerInventoryItem = inventoryItem
        }
    }
    result.lastNonSerInventoryItem = lastNonSerInventoryItem
    return result
}

/**
 * Get Inventory Promised Date/Time
 * @return
 */
def getPromisedDateTime(GenericValue inventoryItem) {
    Map result = success()
    // get the promiseDatetime
    GenericValue productFacility = inventoryItem.getRelatedOne("ProductFacility", false)
    Long daysToShip = (Long) productFacility.daysToShip
    if (!daysToShip) {
        // if the product does not have its own days to ship, use Facility.defaultDaysToShip, if not then use 30 days as a USA industry default
        daysToShip = (Long) facility.defaultDaysToShip
    }
    if (!daysToShip) {
        daysToShip = (Long) 30
    }
    result.daysToShip = daysToShip

    Timestamp orderDate = orderHeader.getTimestamp("orderDate")
    Calendar cal = Calendar.getInstance()
    cal.setTimeInMillis(orderDate.getTime())
    cal.add(Calendar.DAY_OF_YEAR, daysToShip.intValue())
    Timestamp promisedDatetime = UtilMisc.toMap("promisedDatetime", new java.sql.Timestamp(cal.getTimeInMillis()))
    result.promisedDatetime = promisedDatetime

    return result
}

/**
 * Reserve Order Item Inventory
 * @return
 */
def reserveOrderItemInventory() {
    Map result = success()
    GenericValue checkOisgirEntity = from("OrderItemShipGrpInvRes").where(parameters).queryOne()
    GenericValue orderItem = from("OrderItem").where(parameters).queryOne()
    parameters.promisedDatetime = orderItem.shipBeforeDate
    parameters.currentPromisedDate = orderItem.shipBeforeDate

    if (!checkOisgirEntity) {
        // create OrderItemShipGrpInvRes record
        GenericValue newOisgirEntity = makeValue("OrderItemShipGrpInvRes", parameters)
        Timestamp nowTimestamp = UtilDateTime.nowTimestamp()
        newOisgirEntity.createdDatetime = nowTimestamp
        newOisgirEntity.priority = parameters.priority

        if(!newOisgirEntity.reservedDatetime) {
            newOisgirEntity.reservedDatetime = nowTimestamp
        }
        newOisgirEntity.create()
    }
    else {
        BigDecimal quantity = checkOisgirEntity.quantity + parameters.quantity
        checkOisgirEntity.quantity = quantity.setScale(6)

        BigDecimal quantityNotAvailable = checkOisgirEntity.quantityNotAvailable + parameters.quantityNotAvailable
        checkOisgirEntity.quantityNotAvailable = quantityNotAvailable.setScale(6)
        checkOisgirEntity.store()
    }
    return result
}

/**
 * Cancel Inventory Reservation for an Order
 * @return
 */
def cancelOrderInventoryReservation() {
    // Iterates through each OrderItemShipGrpInvRes on each OrderItem for the order
    // with the given orderId and cancels the reservation by removing the
    // OrderItemShipGrpInvRes and incrementing the corresponding non-serialized
    // inventoryItem's availableToPromise quantity, or setting the status of the
    // corresponding serialized inventoryItem to available.

    Map oisgirListLookupMap = [orderId: parameters.orderId]
    if (parameters.orderItemSeqId) {
        oisgirListLookupMap.orderItemSeqId = parameters.orderItemSeqId
        logVerbose("OISGIR Cancel for single item : ${oisgirListLookupMap}")
    }
    if (parameters.shipGroupSeqId) {
        oisgirListLookupMap.shipGroupSeqId = parameters.shipGroupSeqId
        logVerbose("OISGIR Cancel for single item : ${oisgirListLookupMap}")
    }
    List oisgirList = from("OrderItemShipGrpInvRes").where(oisgirListLookupMap).queryList()
    for (GenericValue oisgir : oisgirList) {
        Map cancelOisgirMap = [orderId: oisgir.orderId, orderItemSeqId: oisgir.orderItemSeqId, shipGroupSeqId: oisgir.shipGroupSeqId, inventoryItemId: oisgir.inventoryItemId]
        Map serviceResult = run service: "cancelOrderItemShipGrpInvRes", with: cancelOisgirMap
        if (!ServiceUtil.isSuccess(serviceResult)) {
            return error(serviceResult.errorMessage)
        }
        // checkDecomposeInventoryItem service is called to decompose a marketing package (if the product is a mkt pkg)
        Map checkDiiMap = [inventoryItemId: oisgir.inventoryItemId]
        Map servviceResult = run service: "checkDecomposeInventoryItem", with: checkDiiMap
        if (!ServiceUtil.isSuccess(serviceResult)) {
            return error(serviceResult.errorMessage)
        }

    }
    return success()
}


/**
 * Cancel Inventory Reservation Qty For An Item
 * @return
 */
def cancelOrderItemInvResQty() {
    // This will cancel the specified amount by looking through the reservations in order and cancelling
    // just the right amount
    Map result = success()
    Map cancelOisgirMap = [:]

    if (parameters.cancelQuantity) {
        Map cancelMap = [orderId: parameters.orderId, orderItemSeqId: parameters.orderItemSeqId, shipGroupSeqId: parameters.shipGroupSeqId]
        Map serviceResult = run service: "cancelOrderInventoryReservation", with: cancelMap
    }
    if (parameters.cancelQuantity) {
        BigDecimal toCancelAmount = parameters.cancelQuantity
        Map oisgirListLookupMap = [orderId: parameters.orderId, orderItemSeqId: parameters.orderItemSeqId, shipGroupSeqId: parameters.shipGroupSeqId]
        List oisgirList = from("OrderItemShipGrpInvRes").where(oisgirListLookupMap).queryList()
        for(GenericValue oisgir : oisgirList) {
            if (toCancelAmount > (BigDecimal) 0.0) {
                if (oisgir.quantity >= toCancelAmount) {
                    cancelOisgirMap.cancelQuantity = toCancelAmount
                }
                if (oisgir.quantity < toCancelAmount) {
                    cancelOisgirMap.cancelQuantity = oisgir.quantity
                }
                cancelOisgirMap.orderId = oisgir.orderId
                cancelOisgirMap.orderItemSeqId = oisgir.orderItemSeqId
                cancelOisgirMap.shipGroupSeqId = oisgir.shipGroupSeqId
                cancelOisgirMap.inventoryItemId = oisgir.inventoryItemId
                Map serviceResult = run service: "cancelOrderItemShipGrpInvRes", with: cancelOisgirMap
                if (!ServiceUtil.isSuccess(serviceResult)) {
                    return error(serviceResult.errorMessage)
                }
                // checkDecomposeInventoryItem service is called to decompose a marketing package (if the product is a mkt pkg)
                checkDiiMap.inventoryItemId = oisgir.inventoryItemId
                serviceResult = run service: "checkDecomposeInventoryItem", with: checkDiiMap
                if (!ServiceUtil.isSuccess(serviceResult)) {
                    return error(serviceResult.errorMessage)
                }
                // update the toCancelAmount
                toCancelAmount = toCancelAmount - cancelOisgirMap.cancelQuantity
                toCancelAmount = toCancelAmount.setScale(6)
            }
        }
    }
    return result
}

/**
 * Cancel An Inventory Reservation
 * @return
 */
def cancelOrderItemShipGrpInvRes() {
    BigDecimal cancelQuantity = null
    GenericValue orderItemShipGrpInvRes = from("OrderItemShipGrpInvRes")
    GenericValue reserveAnInventoryItem = orderItemShipGrpInvRes.getRelatedOne("InventoryItem", false)
    if (inventoryItem.inventoryItemTypeId.equals("SERIALIZED_INV_ITEM")) {
        logVerbose("Serialized inventory re-enabled.")
        inventoryItem.statusId = "INV_AVAILABLE"
        orderItemShipGrpInvRes.remove()
        inventoryItem.store()
    }
    if (inventoryItem.inventoryItemTypeId.equals("NON_SERIAL_INV_ITEM")) {
        logVerbose("Non-Serialized inventory item incrementing availableToPromise.")
        cancelQuantity = parameters.cancelQuantity
        if (!cancelQuantity) {
            cancelQuantity = orderItemShipGrpInvRes.quantity
        }
        
        // instead of updating InventoryItem, add an InventoryItemDetail
        Map createDetailMap = [inventoryItemId: inventoryItem.inventoryItemId, orderId: parameters.orderId, orderItemSeqId: parameters.orderItemSeqId, shipGroupSeqId: parameters.shipGroupSeqId, availableToPromiseDiff: cancelQuantity]
        Map serviceResult = run service: "createInventoryItemDetail", with: createDetailMap
        if (!ServiceUtil.isSuccess(serviceResult)) {
            return error(serviceResult.errorMessage)
            }
            createDetailMap.clear()
            if (cancelQuantity < orderItemShipGrpInvRes.quantity) {
                BigDecimal quantity = orderItemShipGrpInvRes.quantity - cancelQuantity
                orderItemShipGrpInvRes.quantity = quantity.setScale(6)
                
                orderItemShipGrpInvRes.store()
            }
            else {
                orderItemShipGrpInvRes.remove()
            }
    }
    return success()
}
