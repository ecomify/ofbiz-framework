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

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.order.order.OrderReadHelper
import org.apache.ofbiz.product.product.ProductWorker
import org.apache.ofbiz.service.ServiceUtil




/**
 * Create Shipment
 * @return
 */
def createShipment() {
    Map result = success()
    GenericValue newEntity = makeValue("Shipment")
    newEntity.setNonPKFields(parameters)
    
    if (parameters.shipmentId) {
        newEntity.setPKFields(parameters)
    } else {
        newEntity.shipmentId = delegator.getNextSeqId("Shipment")
    }
    result.shipmentId = newEntity.shipmentId
    String shipmentTypeId = parameters.shipmentTypeId
    // set the created and lastModified info
    newEntity.createdDate = UtilDateTime.nowTimestamp()
    newEntity.createdByUserLogin = userLogin.userLoginId
    newEntity.lastModifiedDate = UtilDateTime.nowTimestamp()
    newEntity.lastModifiedByUserLogin = userLogin.userLoginId
    /*
     * if needed create some WorkEfforts and remember their IDs:
     * estimatedShipDate: estimatedShipWorkEffId
     * estimatedArrivalDate: estimatedArrivalWorkEffId
     */
    if (parameters.estimatedShipDate) {
        Map shipWorkEffortMap = [workEffortName: "Shipment #${newEntity.shipmentId} ${newEntity.primaryOrderId} Ship"]
        if ((shipmentTypeId == "OUTGOING_SHIPMENT") || (shipmentTypeId == "SALES_SHIPMENT") || (shipmentTypeId == "PURCHASE_RETURN")) {
            shipWorkEffortMap.workEffortTypeId = "SHIPMENT_OUTBOUND"
        }
        shipWorkEffortMap.currentStatusId = "CAL_TENTATIVE"
        shipWorkEffortMap.workEffortPurposeTypeId = "WEPT_WAREHOUSING"
        shipWorkEffortMap.estimatedStartDate = parameters.estimatedShipDate
        shipWorkEffortMap.estimatedCompletionDate = parameters.estimatedShipDate
        shipWorkEffortMap.facilityId = parameters.originFacilityId
        shipWorkEffortMap.quickAssignPartyId = userLogin.partyId
        Map serviceResultSD = run service: "createWorkEffort", with: shipWorkEffortMap
        newEntity.estimatedShipWorkEffId = serviceResultSD.workEffortId
        if (newEntity.partyIdFrom) {
            Map assignPartyToWorkEffortShip = [workEffortId: newEntity.estimatedShipWorkEffId, partyId: newEntity.partyIdFrom, roleTypeId: "CAL_ATTENDEE", statusId: "CAL_SENT"]
            run service: "assignPartyToWorkEffort", with: assignPartyToWorkEffortShip
        }
    }
    if (parameters.estimatedArrivalDate) {
        Map arrivalWorkEffortMap = [workEffortName: "Shipment #${newEntity.shipmentId} ${newEntity.primaryOrderId} Arrival"]
        if ((shipmentTypeId == "INCOMING_SHIPMENT") || (shipmentTypeId == "PURCHASE_SHIPMENT") || (shipmentTypeId == "SALES_RETURN")) {
            arrivalWorkEffortMap.workEffortTypeId = "SHIPMENT_INBOUND"
        }
        arrivalWorkEffortMap.currentStatusId = "CAL_TENTATIVE"
        arrivalWorkEffortMap.workEffortPurposeTypeId = "WEPT_WAREHOUSING"
        arrivalWorkEffortMap.estimatedStartDate = parameters.estimatedArrivalDate
        arrivalWorkEffortMap.estimatedCompletionDate = parameters.estimatedArrivalDate
        arrivalWorkEffortMap.facilityId = parameters.destinationFacilityId
        arrivalWorkEffortMap.quickAssignPartyId = userLogin.partyId
        Map serviceResultAD = run service: "createWorkEffort", with: arrivalWorkEffortMap
        newEntity.estimatedArrivalWorkEffId = serviceResultAD.workEffortId
        if (newEntity.partyIdTo) {
            Map assignPartyToWorkEffortArrival = [workEffortId: newEntity.estimatedArrivalWorkEffId, partyId: newEntity.partyIdTo, roleTypeId: "CAL_ATTENDEE", statusId: "CAL_SENT"]
            run service: "assignPartyToWorkEffort", with: assignPartyToWorkEffortArrival
        }
    }
    newEntity.create()
    
    // get the ShipmentStatus history started
    if (newEntity.statusId) {
        Map createShipmentStatusMap = [shipmentId: newEntity.shipmentId, statusId: newEntity.statusId]
        run service: "createShipmentStatus", with: createShipmentStatusMap
    }
    return result
}

/**
 * Update Shipment
 * @return
 */
def updateShipment() {
    Map result = success()
    List errorList = []
    GenericValue lookedUpValue = from("Shipment").where(parameters).queryOne()
    // put the type in return map so that service consumer knows what type of shipment was updated
    result.shipmentTypeId = lookedUpValue.shipmentTypeId
    
    if (parameters.statusId) {
        if (parameters.statusId != lookedUpValue.statusId) {
            // make sure a StatusValidChange record exists, if not return error
            GenericValue checkStatusValidChange = from("StatusValidChange").where(statusId: lookedUpValue.statusId, statusIdTo: parameters.statusId).queryOne()
            if (!checkStatusValidChange) {
                errorList.add("ERROR: Changing the status from ${lookedUpValue.statusId} to ${parameters.statusId} is not allowed.")
            }
            Map createShipmentStatusMap = [shipmentId: parameters.shipmentId, statusId: parameters.statusId]
            if (parameters.eventDate) {
                createShipmentStatusMap.statusDate = parameters.eventDate
            }
            run service: "createShipmentStatus", with: createShipmentStatusMap
        }
    }
    // now finally check for errors
    if (errorList) {
        logError(errorList)
        return error(errorList)
    }
    // Check the pickup and delivery dates for changes and update the corresponding WorkEfforts
    if (((parameters.estimatedShipDate) && (parameters.estimatedShipDate != lookedUpValue.estimatedShipDate)) 
        || ((parameters.originFacilityId) && (parameters.originFacilityId != lookedUpValue.originFacilityId)) 
        || ((parameters.statusId) && (parameters.statusId != lookedUpValue.statusId) 
            && ((parameters.statusId == "SHIPMENT_CANCELLED") || (parameters.statusId == "SHIPMENT_PACKED") || (parameters.statusId == "SHIPMENT_SHIPPED")))) {
        GenericValue estShipWe = from("WorkEffort").where(workEffortId: lookedUpValue.estimatedShipWorkEffId).queryOne()
        if (estShipWe) {
            estShipWe.estimatedStartDate = parameters.estimatedShipDate
            estShipWe.estimatedCompletionDate = parameters.estimatedShipDate
            estShipWe.facilityId = parameters.originFacilityId
            if ((parameters.statusId) && (parameters.statusId != lookedUpValue.statusId)) {
                if (parameters.statusId == "SHIPMENT_CANCELLED") {
                    estShipWe.currentStatusId = "CAL_CANCELLED"
                }
                if (parameters.statusId == "SHIPMENT_PACKED") {
                    estShipWe.currentStatusId = "CAL_CONFIRMED"
                }
                if (parameters.statusId == "SHIPMENT_SHIPPED") {
                    estShipWe.currentStatusId = "CAL_COMPLETED"
                }
            }
            Map estShipWeUpdMap = [:]
            estShipWeUpdMap << estShipWe
            run service: "updateWorkEffort", with: estShipWeUpdMap
        }
    }
    if (((parameters.estimatedArrivalDate) && (parameters.estimatedArrivalDate != lookedUpValue.estimatedArrivalDate)) 
        || ((parameters.destinationFacilityId) && (parameters.destinationFacilityId != lookedUpValue.destinationFacilityId))) {
        GenericValue estimatedArrivalWorkEffort = from("WorkEffort").where(workEffortId: lookedUpValue.estimatedArrivalWorkEffId).queryOne()
        if (estimatedArrivalWorkEffort) {
            estimatedArrivalWorkEffort.estimatedStartDate = parameters.estimatedArrivalDate
            estimatedArrivalWorkEffort.estimatedCompletionDate = parameters.estimatedArrivalDate
            estimatedArrivalWorkEffort.facilityId = parameters.destinationFacilityId
            Map estimatedArrivalWorkEffortUpdMap = [:]
            estimatedArrivalWorkEffortUpdMap << estimatedArrivalWorkEffort
            run service: "updateWorkEffort", with: estimatedArrivalWorkEffortUpdMap
        }
    }
    // if the partyIdTo or partyIdFrom has changed, add WEPAs
    if ((parameters.partyIdFrom) && (parameters.partyIdFrom != lookedUpValue.partyIdFrom) && (lookedUpValue.estimatedShipWorkEffId)) {
        Map assignPartyToWorkEffortShip = [workEffortId: lookedUpValue.estimatedShipWorkEffId, partyId: parameters.partyIdFrom]
        List existingShipWepas = from("WorkEffortPartyAssignment").where(assignPartyToWorkEffortShip).filterByDate().queryList()
        if (!existingShipWepas) {
            assignPartyToWorkEffortShip.roleTypeId = "CAL_ATTENDEE"
            assignPartyToWorkEffortShip.statusId = "CAL_SENT"
            run service: "assignPartyToWorkEffort", with: assignPartyToWorkEffortShip
        }
    }
    if ((parameters.partyIdTo) && (parameters.partyIdTo != lookedUpValue.partyIdTo) && (lookedUpValue.estimatedArrivalWorkEffId)) {
        Map assignPartyToWorkEffortArrival = [workEffortId: lookedUpValue.estimatedArrivalWorkEffId, partyId: parameters.partyIdTo]
        List existingArrivalWepas = from("WorkEffortPartyAssignment").where(assignPartyToWorkEffortArrival).filterByDate().queryList()
        if (!existingArrivalWepas) {
            assignPartyToWorkEffortArrival.roleTypeId = "CAL_ATTENDEE"
            assignPartyToWorkEffortArrival.statusId = "CAL_SENT"
            run service: "assignPartyToWorkEffort", with: assignPartyToWorkEffortArrival
        }
    }
    // finally before setting nonpk fields, set the oldStatusId, oldPrimaryOrderId, oldOriginFacilityId, oldDestinationFacilityId
    result.oldStatusId = lookedUpValue.statusId
    result.oldPrimaryOrderId = lookedUpValue.primaryOrderId
    result.oldOriginFacilityId = lookedUpValue.originFacilityId
    result.oldDestinationFacilityId = lookedUpValue.destinationFacilityId
    
    // now that all changes have been checked, set the nonpks
    lookedUpValue.setNonPKFields(parameters)
    lookedUpValue.lastModifiedDate = UtilDateTime.nowTimestamp()
    lookedUpValue.lastModifiedByUserLogin = userLogin.userLoginId
    result.shipmentId = lookedUpValue.shipmentId
    lookedUpValue.store()
    return result
}

/**
 * Create Shipment based on ReturnHeader
 * @return
 */
def createShipmentForReturn() {
    Map result = success()
    GenericValue returnHeader = from("ReturnHeader").where(returnId: parameters.returnId).queryOne()
    Map shipmentCtx = [partyIdFrom: returnHeader.fromPartyId, partyIdTo: returnHeader.toPartyId, originContactMechId: returnHeader.originContactMechId, destinationFacilityId: returnHeader.destinationFacilityId, primaryReturnId: returnHeader.returnId]
    // later different behavior for customer vs. returns would happen here
    if (returnHeader.returnHeaderTypeId.contains("CUStoMER")) {
        shipmentCtx.shipmentTypeId = "SALES_RETURN"
        shipmentCtx.statusId = "PURCH_SHIP_CREATED" // we may later need different status codes for return shipments
    } else if (returnHeader.returnHeaderTypeId == "VENDOR_RETURN") {
        shipmentCtx.shipmentTypeId = "PURCHASE_RETURN"
        shipmentCtx.statusId = "SHIPMENT_INPUT"
    } else {
        String errorMessage = UtilProperties.getMessage("ProductUiLabels", "FacilityReturnHeaderTypeNotSupported", locale)
        logError(errorMessage)
        return error(errorMessage)
    }
    Map serviceResult = run service: "createShipment", with: shipmentCtx
    result.shipmentId = serviceResult.shipmentId
    return result
}

/**
 * Create Shipment and ShipmentItems based on ReturnHeader and ReturnItems
 * @return
 */
def createShipmentAndItemsForReturn() {
    Map result = success()
    List returnItems = from("ReturnItem").where(returnId: parameters.returnId).queryList()
    
    // The return shipment is created if the return contains one or more physical products
    Boolean isPhysicalProductAvailable = false
    for (GenericValue returnItem : returnItems) {
        GenericValue product = delegator.getRelatedOne("Product", returnItem, false)
        if (product) {
            ProductWorker productWorker = new ProductWorker()
            Boolean isPhysicalProduct = productWorker.isPhysical(product)
            if (isPhysicalProduct) {
                isPhysicalProductAvailable = true
            }
        }
    }
    if (isPhysicalProductAvailable) {
        Map serviceResult = run service: "createShipmentForReturn", with: parameters
        if (!ServiceUtil.isSuccess(serviceResult)) {
            return serviceResult
        }
        String shipmentId = serviceResult.shipmentId
        logInfo("Created new shipment " + shipmentId)
        
        for (GenericValue returnItem : returnItems) {
            // Shipment items are created only for physical products
            Boolean isPhysicalProduct = false
            GenericValue product = delegator.getRelatedOne("Product", returnItem, false)
            ProductWorker productWorker = new ProductWorker()
            isPhysicalProduct = productWorker.isPhysical(product)
            
            if (isPhysicalProduct) {
                Map shipItemCtx = [shipmentId: shipmentId, productId: returnItem.productId, quantity: returnItem.returnQuantity]
                logInfo("calling create shipment item with ${shipItemCtx}")
                Map serviceResultCSI = run service: "createShipmentItem", with: shipItemCtx
                String shipmentItemSeqId = serviceResultCSI.shipmentItemSeqId
                shipItemCtx = null
                shipItemCtx.shipmentId = shipmentId
                shipItemCtx.shipmentItemSeqId = shipmentItemSeqId
                shipItemCtx.returnId = returnItem.returnId
                shipItemCtx.returnItemSeqId = returnItem.returnItemSeqId
                shipItemCtx.quantity = returnItem.returnQuantity
                run service: "createReturnItemShipment", with: shipItemCtx
            }
        }
        result.shipmentId = shipmentId
    }
    return result
}

/**
 * Create Shipment and ShipmentItems based on primaryReturnId for Vendor return
 * @return
 */
def createShipmentAndItemsForVendorReturn() {
    Map result = success()
    Map serviceResult = run service: "createShipment", with: parameters
    if (!ServiceUtil.isSuccess(serviceResult)) {
        return serviceResult
    }
    String shipmentId = serviceResult.shipmentId
    logInfo("Created new shipment ${shipmentId}")
    List returnItems = from("ReturnItem").where(returnId: parameters.primaryReturnId).queryList()
    for (GenericValue returnItem : returnItems) {
        Map shipItemCtx = [shipmentId: shipmentId, productId: returnItem.productId, quantity: returnItem.returnQuantity]
        logInfo("calling create shipment item with ${shipItemCtx}")
        Map serviceResultCSI = run service: "createShipmentItem", with: shipItemCtx
        String shipmentItemSeqId = serviceResultCSI.shipmentItemSeqId
        shipItemCtx = null
        shipItemCtx.shipmentId = shipmentId
        shipItemCtx.shipmentItemSeqId = shipmentItemSeqId
        shipItemCtx.returnId =returnItem.returnId
        shipItemCtx.returnItemSeqId = returnItem.returnItemSeqId
        shipItemCtx.quantity = returnItem.returnQuantity
        run service: "createReturnItemShipment", with: shipItemCtx
    }
    result.shipmentId = shipmentId
    return result
}

/**
 * Set Shipment Settings From Primary Order
 * @return
 */
def setSthipmentSettingsFromPrimaryOrder() {
    GenericValue orderItemShipGroup
    // on Shipment set partyIdFrom, partyIdTo (vendorPartyId), originContactMechId, destinationContactMechId, estimatedShipCost
    GenericValue shipment = from("Shipment").where(parameters).queryOne()
    if (!shipment?.primaryOrderId) {
        // No primaryOrderId specified, don't do anything
        logInfo("Not running setShipmentSettingsFromPrimaryOrder, primaryOrderId is empty for shipmentId [${shipment.shipmentId}]")
        return success()
    }
    // TODO: we may not want to check this if, for example, Purchase Orders don't have any OrderItemShipGroups
    if (!shipment.primaryShipGrouSeqId) {
        // No primaryShipGroupSeqId specified, don't do anything
        logInfo("Not running setShipmentSettingsFromPrimaryOrder, primaryShipGroupSeqId is empty for shipmentId [${parameters.shipmentId}]")
        return success()
    }
    GenericValue orderHeader = from("OrderHeader").where(orderId: shipment.primaryOrderId).queryOne()
    if (shipment.primaryShipGroupSeqId) {
        orderItemShipGroup = from("OrderItemShipGroup").where(orderId: shipment.primaryOrderId, shipGroupSeqId: shipment.primaryShipGroupSeqId).queryOne()
    }
    if (orderHeader.orderTypeId == "SALES_ORDER") {
        shipment.shipmentTypeId = "SALES_SHIPMENT"
    }
    if (orderHeader.orderTypeId == "PURCHASE_ORDER") {
        if (shipment.shipmentTypeId != "DROP_SHIPMENT") {
            shipment.shipmentTypeId = "PRUCHASE_SHIPMENT"
        }
    }
    // set the facility if we are from a store with a single facility
    if ((!shipment.originFacilityId) && (shipment.shipmentTypeId == "SALES_SHIPMENt") && (orderHeader.productStoreId)) {
        GenericValue productStore = from("ProductStore").where(productStoreId: orderHeader.productStoreId).queryOne()
        if (productStore.oneInventoryFacility == "Y") {
            shipment.originFacilityId = productStore.inventoryFacilityId
        }
    }
    // partyIdFrom, partyIdTo (vendorPartyId) - NOTE: these work the same for Purchase and Sales Orders...
    List orderRoles = from("OrderRole").where(orderId: shipment.primaryOrderId).queryList()
    Map limitRoleMap
    List limitOrderRoles
    GenericValue limitOrderRole
    // From: SHIP_FROM_VENDOR
    if (!shipment.partyIdFrom) {
        limitRoleMap = [roleTypeId: "SHIP_FROM_VENDOR"]
        limitOrderRoles = EntityUtil.filterByAnd(orderRoles, limitRoleMap)
        limitOrderRole = limitOrderRoles.get(0)
        if (limitOrderRole) {
            shipment.partyIdFrom = limitOrderRole.partyId
        }
        limitRoleMap = null
        limitOrderRoles = null
        limitOrderRole = null
    }
    // SHIP_TO_CUSTOMER
    if (!shipment.partyIdTo) {
        limitRoleMap.roleTypeId = "SHIP_TO_CUSTOMER"
        limitOrderRoles = EntityUtil.filterByAnd(orderRoles, limitRoleMap)
        limitOrderRole = limitOrderRoles.get(0)
        if (limitOrderRole) {
            shipment.partyIdTo = limitOrderRole.partyId
        }
        limitRoleMap = null
        limitOrderRoles = null
        limitOrderRole = null
    }
    // To: CUSTOMER
    if (!shipment.partyIdTo) {
        limitRoleMap.roleTypeId = "CUSTOMER"
        limitOrderRoles = EntityUtil.filterByAnd(orderRoles, limitRoleMap)
        limitOrderRole = limitOrderRoles.get(0)
        if (limitOrderRole) {
            shipment.partyIdTo = limitOrderRole.partyId
        }
        limitRoleMap = null
        limitOrderRoles = null
        limitOrderRole = null
    }
    List orderContactMechs = from("OrderContactMech").where(orderId: shipment.primaryOrderId).queryList()
    // destinationContactMechId
    if (!shipment.destinationContactMechId) {
        // first try from orderContactMechs
        Map destinationContactMap = [contactMechPurposeTypeId: "SHIPPING_LOCATION"]
        List destinationOrderContactMechs = EntityUtil.filterByAnd(orderContactMechs, destinationContactMap)
        GenericValue destinationOrderContactMech = destinationOrderContactMechs.get(0)
        if (destinationOrderContactMech) {
            shipment.destinationContactMechId = destinationOrderContactMech.contactMechId
        } else {
            logWarning("Cannot find a shipping destination address for ${shipment.primaryOrderId}")
        }
    }
    // originContactMechId.  Only do this if it is not a purchase shipment
    if (shipment.shipmentTypeId != "PURCHASE_SHIPMENT") {
        if (!shipment.originContactMechId) {
            Mp originContactMap = [contactMechPurposeTypeId: "SHIP_ORIG_LOCATION"]
            List originOrderContactMechs = EntityUtil.filterByAnd(orderContactMechs, originContactMap)
            GenericValue originOrderContactMech = originOrderContactMechs.get(0)
            if (originOrderContactMech) {
                shipment.originContactMechId = originOrderContactMech.contactMechId
            } else {
                logWarning("Cannot find a shipping origin address for ${shipment.primaryOrderId}")
            }
        }
    }
    // destinationTelecomNumberId
    if (!shipment.destinationTelecomNumberId) {
        Map destTelecomOrderContactMechMap = [contactMechPurposeTypeId: "PHONE_SHIPPING"]
        List destTelecomOrdercontactMechs = EntityUtil.filterByAnd(orderContactMechs, destTelecomOrderContactMechMap)
        GenericValue destTelecomOrderContactMech = destTelecomOrdercontactMechs.get(0)
        if (destTelecomOrderContactMech) {
            shipment.destinationTeelcomNumberId = destTelecomOrderContactMech.contactMechId
        } else {
            // use the first unexpired phone number of the shipment partyIdTo
            GenericValue phoneNumber = from("PartyAndTelecomNumber").where(partyId: shipment.partyIdTo).filterByDate().queryFirst()
            if (phoneNumber) {
                shipment.destinationTelecomNumberId = phoneNumber.contactMechId
            } else {
                logWarning("Cannot find a shipping destination phone number for ${shipment.primaryOrderId}")
            }
        }
    }
    // originTelecomNumberId
    if (!shipment.originTelecomNumberId) {
        Map originTelecomOrderContactMechMap = [contactMechPurposeTypeId: "PHONE_SHIP_ORIG"]
        List originTelecomOrderContactMechs = EntityUtil.filterByAnd(orderContactMechs, originTelecomOrderContactMechMap)
        GenericValue originTelecomOrderContactMech = originTelecomOrderContactMechs.get(0)
        if (originTelecomOrderContactMech) {
            shipment.originTelecomNumberId = originTelecomOrderContactMech.contactMechId
        } else {
            logWarning("Cannot find a shipping origin phone number for ${shipment.primaryOrderId}")
        }
    }
    // set the destination facility if it is a purchase order
    if (!shipment.destinationFacility) {
        if (shipment.shipmentTypeId == "PURCHASE_SHIPMENT") {
            Map facilityLookup = [contactMechId: shipment.destinationContactMechId]
            GenericValue destinationFacility = from("FacilityContactMech").where(facilityLookup).queryFirst()
            shipment.destinationFacilityId = destinationFacility.facilityId
        }
    }
    /*
     * NOTE: use new place to find source/destination location/addresses for new OrderItemShipGroup.contactMechId (destination address for sales orders, source address for purchase orders)
     * do this second so it will override the orderContactMech
     * TODO: maybe we should add a new entity for OrderItemShipGroup ContactMechs?
     */
    if (orderItemShipGroup) {
        if (orderHeader.orderTypeId == "SALES_ORDER") {
            shipment.destinationContactMechId = orderItemShipGroup.contactMechId
            shipment.destinationTelecomNumberId = orderItemShipGroup.telecomContactMechId
        }
    }
    if (!shipment.estimatedShipCost) {
        OrderReadHelper orderReadHelper = new OrderReadHelper(orderHeader)
        List orderItems = orderReadHelper.getValidOrderItems()
        List orderAdjustments = orderReadHelper.getAdjustments()
        List orderHeaderAdjustments = orderReadHelper.getOrderHeaderAdjustments()
        BigDecimal orderSubTotal = orderReadHelper.getOrderItemsSubTotal()

        BigDecimal shippingAmount = OrderReadHelper.getAllOrderItemsAdjustmentsTotal(orderItems, orderAdjustments, false, false, true)
        shippingAmount = shippingAmount.add(OrderReadHelper.calcOrderAdjustments(orderHeaderAdjustments, orderSubTotal, false, false, true))
        //org.apache.ofbiz.base.util.Debug.log("shippingAmmount=" + shippingAmount)
        shipment.estimatedShipCost = shippingAmount
        shipment.put("estimatedShipCost", shippingAmount)
    }
    // create a ShipmentRouteSegment with originFacilityId (if set on Shipment), destContactMechId,
    // and from OrderItemShipGroup shipmentMethodTypeId, carrierPartyId, etc
    Map shipmentRouteSegmentMap = [shipmentId: shipment.shipmentId]
    List shipmentRouteSegments = from("ShipmentRouteSegment").where(shipmentRouteSegmentMap).queryList()
    if (!shipmentRouteSegments) {
        // estimatedShipDate, estimatedArrivalDate
        shipmentRouteSegmentMap.estimatedstartDate = shipment.estimatedShipDate
        shipmentRouteSegmentMap.estimatedarrivalDate = shipment.estimatedArrivalDate
        shipmentRouteSegmentMap.originFacilityId = shipment.originFacilityId
        shipmentRouteSegmentMap.originContactMechId = shipment.originContactMechId
        shipmentRouteSegmentMap.originTelecomNumberId = shipment.originTelecomNumberId
        shipmentRouteSegmentMap.destFacilityId = shipment.destinationFacilityId
        shipmentRouteSegmentMap.destContactMechId = shipment.destinationContactMechId
        shipmentRouteSegmentMap.destTelecomNumberId = shipment.destinationTelecomNumberId
        
        orderItemShipGroup = from("OrderItemShipGroup").where(orderId: shipment.primaryOrderId, shipGroupSeqId: shipment.primaryShipGroupSeqId).queryOne()
        if (orderItemShipGroup) {
            shipmentRouteSegmentMap.carrierPartyId = orderItemShipGroup.carrierPartyId
            shipmentRouteSegmentMap.shipmentMethodTypeId = orderItemShipGroup.shipmentMethodTypeId
        }
        run service: "createShipmentRouteSegment", with: shipmentRouteSegmentMap
    }
    Map shipmentUpdateMap = [:]
    shipmentUpdateMap << shipment
    run service: "updateShipment", with: shipmentUpdateMap
    return success()
}

/**
 * Set Shipment Settings From Facilities
 * @return
 */
def setShipmentSettingsFromFacilities() {
    GenericValue facilityContactMech
    GenericValue shipment = from("Shipment").where(parameters).queryOne()
    GenericValue shipmentCopy = shipment.clone()
    List descendingFromDateOrder = ["-fromDate"]
    if (shipment?.originFacilityId) {
        if (!shipment.originContactMechId) {
            facilityContactMech = org.apache.ofbiz.party.contact.ContactMechWorker.getFacilityContactMechByPurpose(
                    delegator, shipment.get("originFacilityId"),
                    org.apache.ofbiz.base.util.UtilMisc.toList("SHIP_ORIG_LOCATION", "PRIMARY_LOCATION"))
            if (facilityContactMech != null) {
                shipment.put("originContactMechId", facilityContactMech.get("contactMechId"));
            }
        }
        if (!shipment.originTelecomNumberId) {
            facilityContactMech = org.apache.ofbiz.party.contact.ContactMechWorker.getFacilityContactMechByPurpose(
                    delegator, shipment.get("originFacilityId"),
                    org.apache.ofbiz.base.util.UtilMisc.toList("PHONE_SHIP_ORIG", "PRIMARY_PHONE"))
            if (facilityContactMech != null) {
                shipment.put("originTelecomNumberId", facilityContactMech.get("contactMechId"))
            }
        }
    }
    if (shipment.destinationFacilityId) {
        if (!shipment.destinationContactMechId) {
            facilityContactMech = org.apache.ofbiz.party.contact.ContactMechWorker.getFacilityContactMechByPurpose(
                    delegator, shipment.get("destinationFacilityId"),
                    org.apache.ofbiz.base.util.UtilMisc.toList("SHIPPING_LOCATION", "PRIMARY_LOCATION"))
            if (facilityContactMech != null) {
                shipment.put("destinationContactMechId", facilityContactMech.get("contactMechId"))
            }
        }
        if (!shipment.destinationTelecomNumberId) {
            facilityContactMech = org.apache.ofbiz.party.contact.ContactMechWorker.getFacilityContactMechByPurpose(
                    delegator, shipment.get("destinationFacilityId"),
                    org.apache.ofbiz.base.util.UtilMisc.toList("PHONE_SHIPPING", "PRIMARY_PHONE"))
            if (facilityContactMech != null) {
                shipment.put("destinationTelecomNumberId", facilityContactMech.get("contactMechId"))
            }
        }
    }
    if (shipment != shipmentCopy) {
        Map shipmentUpdateMap = [:]
        shipmentUpdateMap << shipment
        run service: "updateShipment", with: shipmentUpdateMap
    }
    return success()
}

/**
 * Send Shipment Scheduled Notification
 * @return
 */
def sendShipmentScheduleNotification() {
    GenericValue shipment = from("Shipment").where(parameters).queryOne()
    // find email address for currently logged in user, set as sendFrom
    GenericValue curUserPartyAndContactMech = from("PartyAndContactMech").where(partyId: userLogin.partyId, contactMechTypeId: "EMAIL_ADDRESS").queryFirst()
    Map sendEmailMap = [sendFrom: ("," + curUserPartyAndContactMech.infoString)]

    // find email addresses of partyIdFrom, set as sendTo
    Map sendToPartyIdMap = [:]
    sendToPartyIdMap."${shipment.partyIdFrom}"
    // find email addresses of all parties not equal to partyIdFrom in SUPPLIER_AGENT roleTypeId associated with primary order, set as sendTo
    List supplierAgentOrderRoles = from("OrderRole").where(orderId: shipment.primaryOrderId, roleTypeId: "SUPPLIER_AGENT").queryList()
    for (GenericValue supplierAgentOrderRole : supplierAgentOrderRoles) {
        sendToPartyIdMap[supplierAgentOrderRole.partyId] = supplierAgentOrderRole.partyId
    }
    // go through all send to parties and get email addresses
    for (Map.Entry entry : sendToPartyIdMap) {
        List sendToPartyPartyAndContactMechs = from("PartyAndContactMech").where(partyId: entry.getKey(), contactMechTypeId: "EMAIL_ADDRESS").queryList()
        for (GenericValue sendToPartyPartyAndContactMech : sendToPartyPartyAndContactMechs) {
            StringBuilder newContact = new StringBuilder();
            if (sendEmailMap.sendTo) {
                newContact.append(sendEmailMap.sendTo)
            }
            newContact.append(",").append(sendToPartyPartyAndContactMech.infoString)
            sendEmailMap.sendTo = newContact.toString()
        }
    }
    // set subject, contentType, templateName, templateData
    sendEmailMap.subject = "Scheduled Notification for Shipment " + shipment.shipmentId
    if (shipment.primaryOrderId) {
        sendEmailMap.subject = sendEmailMap.subject + " for Primary Order " + shipment.primaryOrderId
    }
    sendEmailMap.contentType = "text/html"
    sendEmailMap.templateName = "component://order/template/email/OrderDeliveryUpdatedNotice.ftl"
    Map templateData = [shipment: shipment]
    sendEmailMap.templateData = templateData
    
    // call sendGenericNotificationEmail service, if enough information was found
    logInfo("Sending generic notification email (if all info is in place): ${sendEmailMap}")
    if ((sendEmailMap.sendTo) && (sendEmailMap.sendFrom)) {
        run service: "sendGenericNotificationEmail", with: sendEmailMap
    } else {
        logError("Insufficient data to send notice email: ${sendEmailMap}")
    }
    return success()
}

/**
 * Release the purchase order's items assigned to the shipment but not actually received
 * @return
 */
def balanceItemIssuancesForShipment() {
    GenericValue shipment = from("Shipment").where(parameters).queryOne()
    List issuances = delegator.getRelated("ItemIssuance", null, null, shipment, false)
    for (GenericValue issuance : issuances) {
        List receipts = from("ShipmentReceipt").where(shipmentId: shipment.shipmentId, orderId: issuance.orderId, orderItemSeqId: issuance.orderItemSeqId).queryList()
        BigDecimal issuanceQuantity = (BigDecimal) 0
        for (GenericValue receipt : receipts) {
            issuanceQuantity = issuanceQuantity + receipt.quantityAccepted + receipt.quantityRejected
        }
        issuance.quantity = issuanceQuantity
        issuance.store()
    }
    return success()
}

// ShipmentItem services

/**
 * Delete ShipmentItem
 * @return
 */
def deleteShipmentItem() {
    //  If there is any Shipment Package Content available for this Shipment Item then it cannot be deleted as it require Shipment Package content to be deleted first
    List shipmentPackageContents = from("ShipmentPackageContent").where(shipmentId: parameters.shipmentId, shipmentItemSeqId: parameters.shipmentItemSeqId).queryList()
    if (shipmentPackageContents) {
        String errorMessage = UtilProperties.getMessage("ProductErrorUiLables", "ProductErrorShipmentItemCannotBeDeleted", locale)
        logError(errorMessage)
        return error(errorMessage)
    } else {
        GenericValue lookedUpValue = from("ShipmentItem").where(parameters).queryOne()
        lookedUpValue.remove()
    }
    return success()
}

/**
 * splitShipmentItemByQuantity
 * @return
 */
def splitShipmentItemByQuantity() {
    Map result = success()
    GenericValue originalShipmentItem = from("ShipmentItem").where(parameters).queryOne()
    // create new ShipmentItem
    Map inputMap = [shipmentId: originalShipmentItem.shipmentId, productId: originalShipmentItem.productId, quantity: parameters.newItemQuantity]
    Map serviceResult = run service: "createShipmentItem", with: inputMap
    String newShipmentItemSeqId = serviceResult.shipmentItemSeqId
    // reduce the originalShipmentItem.quantity
    originalShipmentItem.quantity = originalShipmentItem.quantity - parameters.newItemQuantity
    // update the original ShipmentItem
    Map updateOriginalShipmentItemMap = [:]
    updateOriginalShipmentItemMap << originalShipmentItem
    run service: "updateShipmentItem", with: updateOriginalShipmentItemMap
    
    // split the OrderShipment record(s) as well for the new quantities,
    // from originalShipmentItem.shipmentItemSeqId to newShipmentItemSeqId
    List itemOrderShipmentList = from("OrderShipment").where(shipmentId: originalShipmentItem.shipmentId, shipmentItemSeqId: originalShipmentItem.shipmentItemSeqId).queryList()
    BigDecimal orderShipmentQuantityLeft = parameters.newItemQuantity
    for (GenericValue itemOrderShipment : itemOrderShipmentList) {
        if (orderShipmentQuantityLeft > (BigDecimal) 0) {
            if (itemOrderShipment.quantity > orderShipmentQuantityLeft) {
                // there is enough in this OrderShipment record, so just adjust it and move on
                Map updateOrderShipmentMap = [:]
                updateOrderShipmentMap << itemOrderShipment
                updateOrderShipmentMap.quantity = updateOrderShipmentMap.quantity - orderShipmentQuantityLeft
                run service: "updateOrderShipment", with: updateOrderShipmentMap
                
                Map createOrderShipmentMap = [orderId: itemOrderShipment.orderId, orderItemSeqId: itemOrderShipment.orderItemSeqId, shipmentId: itemOrderShipment.shipmentId, shipmentItemSeqId: newShipmentItemSeqId, quantity: orderShipmentQuantityLeft]
                run service: "createOrderShipment", with: createOrderShipmentMap
                orderShipmentQuantityLeft = (BigDecimal) 0
            } else {
                // not enough on this one, create a new one for the new item and delete this one
                Map deleteOrderShipmentMap = [:]
                deleteOrderShipmentMap << itemOrderShipment
                run service: "deleteOrderShipment", with: deleteOrderShipmentMap
                
                Map createOrderShipmentMap = [orderId: itemOrderShipment.orderId, orderItemSeqId: itemOrderShipment.orderItemSeqId, shipmentId: itemOrderShipment.shipmentId, shipmentItemSeqId: newShipmentItemSeqId, quantity: itemOrderShipment.quantity]
                run service: "createOrderShipment", with: createOrderShipmentMap
                
                orderShipmentQuantityLeft = orderShipmentQuantityLeft - itemOrderShipment.quantity
            }
        }
    }
    result.newShipmentItemSeqId = newShipmentItemSeqId
    return result
}

// ShipmentPackage services

/**
 * Create ShipmentPackage
 * @return
 */
def createShipmentPackage() {
    Map result = success()
    GenericValue newEntity = makeValue("ShipmentPackage", parameters)
    if ("New" == newEntity.shipmentPackageSeqId) {
        newEntity.shipmentPackageSeqId = null
    }
    // if no shipmentPackageSeqId, generate one based on existing items, ie one greater than the current higher number
    delegator.setNextSubSeqId(newEntity, "shipmentPackageSeqId", 5, 1)
    result.shipmentPackageSeqId = newEntity.shipmentPackageSeqId
    newEntity.dateCreated = UtilDateTime.nowTimestamp()
    newEntity.create()
    String shipmentId = newEntity.shipmentId
    String shipmentPackageSeqId = newEntity.shipmentPackageSeqId
    ensurePackageRouteSeq()
    return result
}

/**
 * Update ShipmentPackage
 * @return
 */
def updateShipmentPackage() {
    GenericValue lookedUpValue = from("ShipmentPackage").where(parameters).queryOne()
    lookedUpValue.setPKFields(parameters)
    lookedUpValue.store()
    String shipmentId = lookedUpValue.shipmentId
    String shipmentPackageSeqId = lookedUpValue.shipmentPackageSeqId
    ensurePackageRouteSeq()
    return success()
}

/**
 * Delete ShipmentPackage
 * @return
 */
def deleteShipmentPackage() {
    // If there is any Shipment Package Content available for this shipment than Shipment Package cannot 
    // be deleted as it require Shipment Package Content to be deleted first
    List shipmentPackageContents = from("ShipmentPackageContent").where(shipmentId: parameters.shipmentId, shipmentPackageSeqId: parameters.shipmentPackageSeqId).queryList()
    if (shipmentPackageContents) {
        String errorMessage = UtilProperties.getMessage("ProductErrorUiLabels", "ProductErrorShipmentPackageCannotBeDeleted", locale)
        logError(errorMessage)
        return error(errorMessage)
    } else {
        GenericValue lookedUpValue = from("ShipmentPackage").where(parameters).queryOne()
        lookedUpValue.remove()
    }
    return success()
}








































