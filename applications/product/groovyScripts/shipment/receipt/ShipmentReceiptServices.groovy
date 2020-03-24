/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") you may not use this file except in compliance
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
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition

/**
 * Create a ShipmentReceipt
 * @return
 */
def createShipmentReceipt() {
    Map result = success()
    GenericValue newEntity = makeValue("ShipmentReceipt")
    newEntity.setNonPKFields(parameters)

    String receiptId = delegator.getNextSeqId("ShipmentReceipt").toString()
    newEntity.receiptId = receiptId
    result.receiptId = receiptId

    if (!newEntity.datetimeReceived) {
        Timestamp nowTimestamp = UtilDateTime.nowTimestamp()
        newEntity.datetimeReceived = nowTimestamp
    }
    newEntity.receivedByUserLoginId = userLogin.userLoginId
    newEntity.create()

    if (parameters.inventoryItemDetailSeqId) {
        GenericValue invDet = from("InventoryItemDetail")
                .where(inventoryItemDetailSeqId: parameters.inventoryItemDetailSeqId, inventoryItemId: parameters.inventoryItemId)
                .queryOne()
        invDet.receiptId = receiptId
        invDet.store()
    }
    Boolean affectAccounting = true

    GenericValue product = from("Product").where(parameters).queryOne()
    if (product.productTypeId == "SERVICE_PRODUCT" || product.productTypeId == "ASSET_USAGE_OUT_IN" || product.productTypeId == "ASSET_USAGE_OUT_IN") {
        affectAccounting = false
    }
    result.affectAccounting = affectAccounting
    return result
}

/**
 * Create a ShipmentReceipt Role
 * @return
 */
def createShipmentReceiptRole() {
    GenericValue newEntity = makeValue("ShipmentReceiptRole", parameters)
    newEntity.create()
    return success()
}

/**
 * Remove a ShipmentReceipt Role
 * @return
 */
def removeShipmentReceiptRole() {
    GenericValue lookupPKMap = makeValue("ShipmentReceiptRole")
    lookupPKMap.setPKFields(parameters)
    GenericValue lookedUpValue = from("ShipmentReceiptRole")
            .where(lookupPKMap)
            .queryOne()
    lookedUpValue.remove()
    return success()
}

/**
 * Receive Inventory in new Inventory Item(s)
 * @return
 */
def receiveInventoryProduct () {
    /**
     * NOTES
     * 
     * - for serialized items with a serial number passed in: the quantityAccepted _should_ always be 1
     * - if the type is SERIALIZED_INV_ITEM but there is not serial number (which is weird...) we'll create a bunch of individual InventoryItems
     * - DEJ20070822: something to consider for the future: maybe instead of this funny looping maybe for serialized items we should only allow a quantity of 1, ie return an error if it is not 1
     */
    Map result = success()
    String currentInventoryItemId
    Double loops = (double) 1.0
    if (parameters.inventoryItemTypeId == "SERIALIZED_INV_ITEM") {
        if ((parameters.serialNumber || parameters.currentInventoryItemId) && (BigDecimal) parameters.quantityAccepted > 1) {
            return error(UtilProperties.getMessage("ProductUiLabels", "FacilityReceiveInventoryProduct", parameters.locale))
        }
        loops = parameters.quantityAccepted
        parameters.quantityAccepted = (BigDecimal) 1
    }
    parameters.quantityOnHandDiff = parameters.quantityAccepted
    parameters.availableToPromiseDiff = parameters.quantityAccepted
    // before getting going, see if there are any validation issues so far

    //Status for Non serialized and Serialized inventory are different, lets make sure correct status is stored in database
    if (parameters.inventoryItemTypeId == "NON_SERIAL_INV_ITEM") {
        if (parameters.statusId == "INV_DEFECTIVE") {
            // This status may come from the Receive Return Screen
            parameters.statusId = "INV_NS_DEFECTIVE"

        } else {
            if (parameters.statusId == "INV_ON_HOLD") {
                parameters.statusId = "INV_NS_ON_HOLD"
            } else {
                if (parameters.statusId == "INV_RETURNED") {
                    parameters.statusId = "INV_NS_RETURNED"
                }
            }
        }
        // Any other status should be just set to null, if it is not a valid status for Non Serialized inventory
        if (parameters.statusId != "INV_NS_DEFECTIVE"
        && parameters.statusId != "INV_NS_ON_HOLD"
        && parameters.statusId =! "INV_NS_RETURNED") {
            parameters.statusId = null
        }

    }

    for (Double currentLoop = ${loops}; currentLoop >=0 ; currentLoop ++) {
        logInfo("receiveInventoryProduct Looping and creating inventory info - ${currentLoop}")

        // if there is an inventoryItemId, update it (this will happen when receiving serialized inventory already in the system, like for returns); if not create one
        Map serviceInMap = null
        currentInventoryItemId = null

        // Set supplier partyId, if inventory received by purchase order
        if (parameters.orderId) {
            List orderRoles = from("OrderRole")
                    .where(orderId: parameters.orderId, roleTypeId: "SUPPLIER_AGENT")
                    .queryList()
            if (orderRoles) {
                GenericValue orderRole = orderRoles[0]
                parameters.partyId = orderRole.partyId
            }
        }
        if (!parameters.currentInventoryItemId) {
            Map serviceResult = run service:"createInventoryItem", with: parameters
            String currentInventoryItemId = serviceResult.inventoryItemId
        } else {
            if (parameters.currentInventoryItemId) {
                parameters.inventoryItemId = parameters.currentInventoryItemId

            }
            run service:"updateInventoryItem", with: parameters
            currentInventoryItemId = parameters.currentInventoryItemId
        }

        // do this only for non-serialized inventory
        if (parameters.inventoryItemTypeId != "SERIALIZED_INV_ITEM") {
            serviceInMap = null
            Map serviceInMap = parameters
            serviceInMap.inventoryItemId = currentInventoryItemId
            run service:"createInventoryItemDetail", with: serviceInMap
            String inventoryItemDetailSeqId = parameters.inventoryItemDetailSeqId
        }
        serviceInMap = null
        serviceInMap = parameters
        serviceInMap.inventoryItemId = currentInventoryItemId
        run service:"createShipmentReceipt", with: serviceInMap

        //update serialized items to AVAILABLE (only if this is not a return), which then triggers other SECA chains
        if (parameters.inventoryItemTypeId == "SERIALIZED_INV_ITEM" && !parameters.returnId) {
            // Retrieve the new inventoryItem
            GenericValue inventoryItem = from("InventoryItem").where(inventoryItemId: currentInventoryItemId).queryOne()

            // Don't reset the status if it's already set to INV_PROMISED or INV_ON_HOLD
            if (inventoryItem.statusId != "INV_PROMISED" && inventoryItem.statusId != "INV_ON_HOLD") {
                serviceInMap = null
                serviceInMap.inventoryItemId = currentInventoryItemId
                serviceInMap.statusId = "INV_AVAILABLE" // XXX set to returned instead
                run service:"updateInventoryItem", with: serviceInMap
            }
        }
        serviceInMap = null
        serviceInMap = parameters
        serviceInMap.inventoryItemId = currentInventoryItemId
        run service:"balanceInventoryItems", with: serviceInMap

        List successMessageList =[]
        successMessageList << "Received ${parameters.quantityAccepted} of ${parameters.productId} in inventory item ${currentInventoryItemId}"
    }
    // return the last inventory item received
    result.inventoryItemId = currentInventoryItemId

    return result
}

/**
 * Quick Receive Entire Return
 * @return
 */
def quickReceiveReturn() {
    Map result = success()
    GenericValue returnHeader = from("ReturnHeader").where(returnId: parameters.returnId).queryOne()
    if (returnHeader.needsInventoryReceive == "Y") {
        // before receiving inventory, check to see if there is inventory information in this database
        Integer iiCount = from("InventoryItem").where(facilityId: returnHeader.destinationFacilityId).queryCount()

        if (iiCount > 0) {
            // create a return shipment for this return
            Map shipmentCtx = [returnId: parameters.returnId]
            Map serviceResult = run service:"createShipmentForReturn", with: shipmentCtx
            String shipmentId = serviceResult.shipmentId
            logInfo("Created new shipment ${shipmentId}")

            List returnItems = from("ReturnItem").where(returnId: returnHeader.returnId).queryList()

            // if no inventory item type specified, get default from facility
            if(!parameters.inventoryItemTypeId) {
                GenericValue facility = returnHeader.getRelatedOne("Facility", false)
                parameters.inventoryItemTypeId = facility.defaultInventoryItemTypeId ?: "NON_SERIAL_INV_ITEM"
            }
            Timestamp nowTimestamp = UtilDateTime.nowTimestamp()

            GenericValue returnItemCount = from("ReturnItem").where(returnId: returnHeader.returnId).queryCount()
            Long nonProductItems = (Long) 0

            for (GenericValue returnItem : returnItems) {
                // record this return item on the return shipment as well.  not sure if this is actually necessary...
                Map shipItemCtx = null
                shipItemCtx = [shipmentId: shipmentId, productId: returnItem.productId, quantity: returnItem.returnQuantity]
                logInfo("calling create shipment item with ${shipItemCtx}")
                Map serviceCSI = run service:"createShipmentItem", with: shipItemCtx
                String shipmentItemSeqId = serviceCSI.shipmentItemSeqId
            }
            for (GenericValue returnItem : returnItems) {
                Map receiveCtx = null
                if (!returnItem.expectedItemStatus) {
                    returnItem.expectedItemStatus = "INV_RETURNED"
                }
                GenericValue orderItem = returnItem.getRelatedOne("OrderItem", false)
                if (orderItem.productId) {
                    Map costCtx = [returnItemSeqId: returnItem.returnItemSeqId, returnId: returnItem.returnId]
                    Map serviceGRIIC = run service:"getReturnItemInitialCost", with: costCtx
                    receiveCtx.unitCost = serviceGRIIC.initialItemCost

                    // check if the items already have SERIALIZED inventory. If so, it still puts them back as SERIALIZED with status "Accepted."
                    GenericValue serializedItemCount = from("InventoryItem")
                            .where(productId: returnItem.productId, facilityId: returnHeader.destinationFacilityId, inventoryItemTypeId: "SERIALIZED_INV_ITEM")
                            .queryCount()
                    Boolean setNonSerial = false
                    if (parameters.inventoryItemTypeId == "NON_SERIAL_INV_ITEM") {
                        if (serializedItemCount == 0) {
                            parameters.inventoryItemTypeId = "NON_SERIAL_INV_ITEM"
                            setNonSerial = true
                        }
                    }
                    if (!setNonSerial) {
                        parameters.inventoryItemTypeId = "SERIALIZED_INV_ITEM"
                        returnItem.returnQuantity = (BigDecimal) 1
                    }
                    receiveCtx =[inventoryItemTypeId: parameters.inventoryItemTypeId,
                        statusId: returnItem.expectedItemStatus,
                        productId: returnItem.productId,
                        returnItemSeqId: returnItem.returnItemSeqId,
                        returnId: returnItem.returnId,
                        quantityAccepted: returnItem.returnQuantity,
                        facilityId: returnHeader.destinationFacilityId,
                        shipmentId: shipmentId, // important: associate ShipmentReceipt with return shipment created
                        comments: "Returned Item RA# ${returnItem.returnId}",
                        datetimeReceived: nowTimestamp,
                        quantityRejected: (BigDecimal) 0
                    ]
                    run service:"receiveInventoryProduct", with: receiveCtx
                } else {
                    (Long) nonProductItems ++
                }
            }
            // now that the receive is done; set the need flag to N
            returnHeader.refresh()
            returnHeader.needsInventoryReceive = "N"
            returnHeader.store()

            // always check/update the ReturnHeader status, even though it might have been from the receiving above, just make sure
            if (returnHeader.statusId != "RETURN_RECEIVED") {
                Map retStCtx = [returnId: returnHeader.returnId, statusId: "RETURN_RECEIVED"]
                run service:"updateReturnHeader", with: retStCtx
            }
        } else {
            logInfo("Not receiving inventory for returnId ${returnHeader.returnId}, no inventory information available.")
        }
    }
    return result
}

/**
 * Issues order item quantity specified to the shipment, then receives inventory for that item and quantity
 * @return
 */
def issueOrderItemToShipmentAndReceiveAgainstPO() {
    Map result = success()
    String shipmentItemSeqId
    GenericValue shipmentItem
    // get orderItem
    GenericValue orderItem = from("OrderItem").where(parameters).queryOne()
    // get orderItemShipGroupAssoc
    GenericValue orderItemShipGroupAssoc = from("OrderItemShipGroupAssoc").where(parameters).queryOne()
    // get shipment
    GenericValue shipment = from("Shipment").where(parameters).queryOne()

    // try to find an existing shipmentItem and attach to it, if none found create a new shipmentItem
    // if there is NO productId on the orderItem, ALWAYS create a new shipmentItem
    if (orderItem.productId) {
        EntityCondition condition = EntityCondition.makeCondition([
            EntityCondition.makeCondition(productId: orderItem.productId),
            EntityCondition.makeCondition(shipmentId: shipment.shipmentId)
        ])
        if (parameters.shipmentItemSeqId) {
            condition = EntityCondition.makeCondition([
                EntityCondition.makeCondition(shipmentItemSeqId: parameters.shipmentItemSeqId),
                condition
            ])
        }
        List shipmentItems = from("ShipmentItem")
                .where(condition)
                .orderBy("shipmentItemSeqId")
                .queryList()
        shipmentItem = shipmentItems[0]
    }
    if (!shipmentItem) {
        Map shipmentItemCreate = [productId: orderItem.productId, shipmentId: parameters.shipmentId, quantity: parameters.quantity]
        Map serviceResult = run service:"createShipmentItem", with: shipmentItemCreate
        Map shipmentItemLookupPk = [shipmentItemSeqId: serviceResult.shipmentItemSeqId, shipmentId: parameters.shipmentId]
        shipmentItem = from("ShipmentItem").where(shipmentItemLookupPk).queryOne()

        // Create OrderShipment for this ShipmentItem
        Map orderShipmentCreate =[quantity: parameters.quantity,
            shipmentId: shipmentItem.shipmentId,
            shipmentItemSeqId: shipmentItem.shipmentItemSeqId,
            orderId: orderItem.orderId,
            orderItemSeqId: orderItem.orderItemSeqId]
        if (orderItemShipGroupAssoc) {
            // If we have a ShipGroup Assoc for this Item to focus on, set that; this is mostly the case for purchase orders and such
            orderShipmentCreate.shipGroupSeqId = orderItemShipGroupAssoc.shipGroupSeqId

        }
        run service:"createOrderShipment", with: orderShipmentCreate
    } else {
        BigDecimal totalIssuedQuantity = getTotalIssuedQuantityForOrderItem()
        BigDecimal receivedQuantity = getReceivedQuantityForOrderItem(orderItem)
        List orderShipments = from("OrderShipment")
                .where(orderId: orderItem.orderId,
                orderItemSeqId: orderItem.orderItemSeqId,
                shipmentId: shipmentItem.shipmentId,
                shipmentItemSeqId: shipmentItem.shipmentItemSeqId,
                shipGroupSeqId: orderItemShipGroupAssoc.shipGroupSeqId)
                .queryList()
        GenericValue orderShipment = orderShipments[0]
        if ((BigDecimal) totalIssuedQuantity < receivedQuantity) {
            BigDecimal quantityToAdd = receivedQuantity - totalIssuedQuantity
            shipmentItem.quantity = shipmentItem.quantity + quantityToAdd
            shipmentItem.store()
            String shipmentItemSeqId = shipmentItem.shipmentItemSeqId

            orderShipment.quantity = orderShipment.quantity + quantityToAdd
            orderShipment.store()
        }
    }
    // TODO: if we want to record the role of the facility operation we have to re-implement this using ShipmentReceiptRole
    // <call-simple-method method-name="associateIssueRoles" xml-resource="component://product/minilang/shipment/issuance/IssuanceServices.xml"/>

    Map receiveInventoryProductCtx = parameters
    receiveInventoryProductCtx.shipmentItemSeqId = shipmentItemSeqId
    Map serviceResult = run service:"receiveInventoryProduct", with:receiveInventoryProductCtx
    result.inventoryItemId = serviceResult.inventoryItemId

    return result
}

/**
 * Computes the till now received quantity from all ShipmentReceipts
 * @return
 */
def getReceivedQuantityForOrderItem (GenericValue orderItem) {
    BigDecimal receivedQuantity = 0
    List shipmentReceipts = from("ShipmentReceipt").where(orderId: orderItem.orderId, orderItemSeqId: orderItem.orderItemSeqId)
    for (GenericValue shipmentReceipt : shipmentReceipts) {
        receivedQuantity = receivedQuantity + shipmentReceipt.quantityAccepted
    }
    return receivedQuantity
}

/**
 * Update issuance, shipment and order items if quantity received is higher than quantity on purchase order
 * @return
 */
def updateIssuanceShipmentAndPoOnReceiveInventory() {
    GenericValue orderItem = from("OrderItem").where(parameters).queryOne()
    if (parameters.orderCurrencyUnitPrice) {
        if (parameters.orderCurrencyUnitPrice != orderItem.unitPrice) {
            orderItem.unitPrice = parameters.orderCurrencyUnitPrice
            orderItem.store()
        }
    } else {
        if (parameters.unitCost != orderItem.unitPrice) {
            orderItem.unitPrice = parameters.unitCost
            orderItem.store()
        }
    }
    BigDecimal receivedQuantity = getReceivedQuantityForOrderItem(orderItem)
    if (orderItem.quantity < receivedQuantity) {
        List orderItemShipGroupAssocs = from("OrderItemShipGroupAssoc").where(orderId: orderItem.orderId, orderItemSeqId: orderItem.orderItemSeqId).queryList()
        BigDecimal quantityVariance = (receivedQuantity-orderItem.quantity).setScale(2, BigDecimal.ROUND_HALF_UP)
        GenericValue orderItemShipGroupAssoc = orderItemShipGroupAssocs[0]
        BigDecimal oisgaQuantity = (orderItemShipGroupAssoc.quantity + quantityVariance).setScale(2, BigDecimal.ROUND_HALF_UP)
        orderItemShipGroupAssoc.quantity = oisgaQuantity
        orderItem.quantity = receivedQuantity
        orderItemShipGroupAssoc.store()
        orderItem.store()
    }
    if (parameters.shipmentId) {
        if (orderItem.productId) {
            BigDecimal totalIssuedQuantity = getTotalIssuedQuantityForOrderItem()//component://product/minilang/shipment/issuance/IssuanceServices.xml)
            if (totalIssuedQuantity < receivedQuantity) {
                BigDecimal quantityToAdd = receivedQuantity - totalIssuedQuantity
                EntityCondition condition = EntityCondition.makeCondition([
                    EntityCondition.makeCondition(productId: orderItem.productId),
                    EntityCondition.makeCondition(shipmentId: parameters.shipmentId)
                ])
                if (parameters.shipmentItemSeqId) {
                    condition = EntityCondition.makeCondition([
                        EntityCondition.makeCondition(shipmentItemSeqId: parameters.shipmentItemSeqId),
                        condition
                    ])
                }
                List shipmentItems = from("ShipmentItem").where(condition).orderBy("shipmentItemSeqId").queryList()
                GenericValue shipmentItem = shipmentItems[0]
                shipmentItem.quantity = shipmentItem.quantity + quantityToAdd
                shipmentItem.store()

                GenericValue orderShipment = from("OrderShipment").where(orderId: parameters.orderId, orderItemSeqId: parameters.orderItemSeqId, shipmentId: parameters.shipmentId, shipmentItemSeqId: shipmentItem.shipmentItemSeqId).queryFirst()
                orderShipment.quantity = orderShipment.quantity + quantityToAdd
                orderShipment.store()
                // TODO: if we want to record the role of the facility operation we have to re-implement this using ShipmentReceiptRole
                // <set field="itemIssuanceId" from-field="itemIssuance.itemIssuanceId"/>
                // <call-simple-method method-name="associateIssueRoles" xml-resource="component://product/minilang/shipment/issuance/IssuanceServices.xml"/>
            }
        }
    }
    return success()
}


/**
 * Cancel Received Items against a purchase order if received something incorrectly
 * @return
 */
def cancelReceivedItems() {
    GenericValue orderItem
    // TODO: When items are received against a Purchase Order, service listed below changes certain things in the system. Changes done by these
    // services also need to be reverted and missing logic can be added later.
    // 1. addProductsBackToCategory
    // 2. setUnitPriceAsLastPrice
    // 3. createAcctgTransForShipmentReceipt
    // 4. updateProductIfAvailableFromShipment

    // update the accepted and received quantity to zero in ShipmentReceipt entity
    GenericValue shipmentReceipt = from("ShipmentReceipt").where(parameters).queryOne()
    shipmentReceipt.quantityAccepted = new BigDecimal 0
    shipmentReceipt.quantityRejected = (BigDecimal) 0
    shipmentReceipt.store()

    // create record for InventoryItemDetail entity
    GenericValue inventoryItem = shipmentReceipt.getRelatedOne("InventoryItem", false)
    Map inventoryItemDetailMap = [inventoryItemId: inventoryItem.inventoryItemId]
    inventoryItemDetailMap.quantityOnHandDiff = inventoryItem.quantityOnHandTotal * (-1)
    inventoryItemDetailMap.availableToPromiseDiff = inventoryItem.availableToPromiseTotal *(-1)
    run service:"createInventoryItemDetail", with: inventoryItemDetailMap

    // Balance the inventory item
    Map balanceInventoryItemMap = [inventoryItemId: inventoryItem.inventoryItemId, priorityOrderId: shipmentReceipt.orderId, priorityOrderItemSeqId: shipmentReceipt.orderItemSeqId]
    run service:("balanceInventoryItems"), with: balanceInventoryItemMap

    // update the shipment status, if shipment was received
    GenericValue shipment = shipmentReceipt.getRelatedOne("Shipment", false)
    if (orderItem.statusId == "ITEM_COMPLETED") {
        // update the order item status
        orderItem.statusId = "ITEM_APPROVED"
        Map orderItemCtx = orderItem
        orderItemCtx.fromStatusId = "ITEM_COMPLETED"
        run service:"changeOrderItemStatus", with: orderItemCtx
        GenericValue orderHeader = orderItem.getRelatedOne("OrderHeader", false)
        // cancel the invoice
        List orderItemBillings = from("OrderItemBilling").where(orderId: orderItem.orderId).queryList()
        if (orderItemBillings) {
            GenericValue orderItemBilling = orderItemBillings[0]
            Map invoiceStatusMap = [invoiceId: orderItemBilling.invoiceId, statusId: "INVOICE_CANCELLED"]
            run service:"setInvoiceStatus", with: invoiceStatusMap
        }
    }
    return success()
}






















