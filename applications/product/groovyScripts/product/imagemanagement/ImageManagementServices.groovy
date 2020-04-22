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

import org.apache.ofbiz.base.util.ScriptUtil
import org.apache.ofbiz.base.util.StringUtil
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.GenericValue

/**
 * Method to upload multiple images for product
 * @return
 */
def UploadProductImages() {
    Map addAdditionalViewForProductMap = parameters
    if (parameters._additionalImageOne_fileName) {
        addAdditionalViewForProductMap.productId = parameters.productId
        addAdditionalViewForProductMap.imageResize = parameters.imageResize
        addAdditionalViewForProductMap.uploadedFile = parameters.additionalImageOne
        addAdditionalViewForProductMap.productContentTypeId = "IMAGE"
        addAdditionalViewForProductMap._uploadedFile_fileName = parameters._additionalImageOne_fileName
        addAdditionalViewForProductMap._uploadedFile_contentType = parameters._additionalImageOne_contentType
        run service: "addMultipleuploadForProduct", with: addAdditionalViewForProductMap
        addAdditionalViewForProductMap.clear()
    }
    if (parameters._additionalImageTwo_fileName) {
        addAdditionalViewForProductMap.productId = parameters.productId
        addAdditionalViewForProductMap.imageResize = parameters.imageResize
        addAdditionalViewForProductMap.uploadedFile = parameters.additionalImageTwo
        addAdditionalViewForProductMap.productContentTypeId = "IMAGE"
        addAdditionalViewForProductMap._uploadedFile_fileName = parameters._additionalImageTwo_fileName
        addAdditionalViewForProductMap._uploadedFile_contentType = parameters._additionalImageTwo_contentType
        run service: "addMultipleuploadForProduct", with: addAdditionalViewForProductMap
        addAdditionalViewForProductMap.clear()
    }
    if (parameters._additionalImageThree_fileName) {
        addAdditionalViewForProductMap.productId = parameters.productId
        addAdditionalViewForProductMap.imageResize = parameters.imageResize
        addAdditionalViewForProductMap.uploadedFile = parameters.additionalImageThree
        addAdditionalViewForProductMap.productContentTypeId = "IMAGE"
        addAdditionalViewForProductMap._uploadedFile_fileName = parameters._additionalImageThree_fileName
        addAdditionalViewForProductMap._uploadedFile_contentType = parameters._additionalImageThree_contentType
        run service: "addMultipleuploadForProduct", with: addAdditionalViewForProductMap
        addAdditionalViewForProductMap.clear()
    }
    if (parameters._additionalImageFour_fileName) {
        addAdditionalViewForProductMap.productId = parameters.productId
        addAdditionalViewForProductMap.imageResize = parameters.imageResize
        addAdditionalViewForProductMap.uploadedFile = parameters.additionalImageFour
        addAdditionalViewForProductMap.productContentTypeId = "IMAGE"
        addAdditionalViewForProductMap._uploadedFile_fileName = parameters._additionalImageFour_fileName
        addAdditionalViewForProductMap._uploadedFile_contentType = parameters._additionalImageFour_contentType
        run service: "addMultipleuploadForProduct", with: addAdditionalViewForProductMap
        addAdditionalViewForProductMap.clear()
    }
    if (parameters._additionalImageFive_fileName) {
        addAdditionalViewForProductMap.productId = parameters.productId
        addAdditionalViewForProductMap.imageResize = parameters.imageResize
        addAdditionalViewForProductMap.uploadedFile = parameters.additionalImageFive
        addAdditionalViewForProductMap.productContentTypeId = "IMAGE"
        addAdditionalViewForProductMap._uploadedFile_fileName = parameters._additionalImageFive_fileName
        addAdditionalViewForProductMap._uploadedFile_contentType = parameters._additionalImageFive_contentType
        run service: "addMultipleuploadForProduct", with: addAdditionalViewForProductMap
        addAdditionalViewForProductMap.clear()
    }
    if (parameters._additionalImageSix_fileName) {
        addAdditionalViewForProductMap.productId = parameters.productId
        addAdditionalViewForProductMap.imageResize = parameters.imageResize
        addAdditionalViewForProductMap.uploadedFile = parameters.additionalImageSix
        addAdditionalViewForProductMap.productContentTypeId = "IMAGE"
        addAdditionalViewForProductMap._uploadedFile_fileName = parameters._additionalImageSix_fileName
        addAdditionalViewForProductMap._uploadedFile_contentType = parameters._additionalImageSix_contentType
        run service: "addMultipleuploadForProduct", with: addAdditionalViewForProductMap
        addAdditionalViewForProductMap.clear()
    }
    if (parameters._additionalImageSeven_fileName) {
        addAdditionalViewForProductMap.productId = parameters.productId
        addAdditionalViewForProductMap.imageResize = parameters.imageResize
        addAdditionalViewForProductMap.uploadedFile = parameters.additionalImageSeven
        addAdditionalViewForProductMap.productContentTypeId = "IMAGE"
        addAdditionalViewForProductMap._uploadedFile_fileName = parameters._additionalImageSeven_fileName
        addAdditionalViewForProductMap._uploadedFile_contentType = parameters._additionalImageSeven_contentType
        run service: "addMultipleuploadForProduct", with: addAdditionalViewForProductMap
        addAdditionalViewForProductMap.clear()
    }
    if (parameters._additionalImageEight_fileName) {
        addAdditionalViewForProductMap.productId = parameters.productId
        addAdditionalViewForProductMap.imageResize = parameters.imageResize
        addAdditionalViewForProductMap.uploadedFile = parameters.additionalImageEight
        addAdditionalViewForProductMap.productContentTypeId = "IMAGE"
        addAdditionalViewForProductMap._uploadedFile_fileName = parameters._additionalImageEight_fileName
        addAdditionalViewForProductMap._uploadedFile_contentType = parameters._additionalImageEight_contentType
        run service: "addMultipleuploadForProduct", with: addAdditionalViewForProductMap
        addAdditionalViewForProductMap.clear()
    }
    if (parameters._additionalImageNine_fileName) {
        addAdditionalViewForProductMap.productId = parameters.productId
        addAdditionalViewForProductMap.imageResize = parameters.imageResize
        addAdditionalViewForProductMap.uploadedFile = parameters.additionalImageNine
        addAdditionalViewForProductMap.productContentTypeId = "IMAGE"
        addAdditionalViewForProductMap._uploadedFile_fileName = parameters._additionalImageNine_fileName
        addAdditionalViewForProductMap._uploadedFile_contentType = parameters._additionalImageNine_contentType
        run service: "addMultipleuploadForProduct", with: addAdditionalViewForProductMap
        addAdditionalViewForProductMap.clear()
    }
    if (parameters._additionalImageTen_fileName) {
        addAdditionalViewForProductMap.productId = parameters.productId
        addAdditionalViewForProductMap.imageResize = parameters.imageResize
        addAdditionalViewForProductMap.uploadedFile = parameters.additionalImageTen
        addAdditionalViewForProductMap.productContentTypeId = "IMAGE"
        addAdditionalViewForProductMap._uploadedFile_fileName = parameters._additionalImageTen_fileName
        addAdditionalViewForProductMap._uploadedFile_contentType = parameters._additionalImageTen_contentType
        run service: "addMultipleuploadForProduct", with: addAdditionalViewForProductMap
        addAdditionalViewForProductMap.clear()
    }
}

/**
 * Remove Content From Product and Image File
 * @return
 */
def removeProductContentAndImageFile() {
    Map removeContent
    List checkDefaultImage = from("ProductContent").where(productId: parameters.productId, contentId: parameters.contentId, productContentTypeId: "DEFAULT_IMAGE").queryList()
    if (!checkDefaultImage) {
        List contentAssocs = from("ContentAssoc").where(contentId: parameters.contentId, contentAssocTypeId: "IMAGE_THUMBNAIL").queryList()
        if (contentAssocs) {
            for (GenericValue contentAssoc : contentAssocs) {
                contentAssoc.remove()
                removeContent = [contentId: contentAssoc.contentIdTo, productId: parameters.productId]
                run service: "removeProductContentForImageManagement", with: removeContent
            }
        }
        GenericValue lookedUpValue = from("ProductContent").where(parameters).queryOne()
        lookedUpValue.remove()
        removeContent = [contentId: parameters.contentId, productId: parameters.productId]
        run service: "removeProductContentForImageManagement", with: removeContent
    } else {
        String errorMessage = UtilProperties.getMessage("ProductErrorUiLabels", "ImageManagementErrorRmoveDefaultImage", locale)
        logError("Cannot remove image contentId ${parameters.contentId}")
        return error(errorMessage)
    }
    return success()
}

/**
 * Remove Content From Product
 * @return
 */
def removeProductContentForImageManagement() {
    List contentRoles = from("ContentRole").where(contentId: parameters.contentId).queryList()
    if (contentRoles) {
        contentRoles.get(0).remove()
    }
    List contentApprovals = from("ContentApproval").where(contentId: parameters.contentId, roleTypeId: "IMAGEAPPROVER").queryList()
    for (GenericValue contentApproval : contentApprovals) {
        contentApproval.remove()
    }
    List contentKeywords = from("ContentKeyword").where(contentId: parameters.contentId).queryList()
    for (GenericValue contentKeyword : contentKeywords) {
        contentKeyword.remove()
    }
    GenericValue content = from("Content").where(contentId: parameters.contentId).queryOne()
    Map removeContentPKMap = [contentId: parameters.contentId]
    run service: "removeContent", with: removeContentPKMap

    String dataResourceId = content.dataResourceId
    List dataResourceRoles = from("DataResourceRole").where(dataResourceId: dataResourceId).queryList()
    if (dataResourceRoles) {
        dataResourceRoles.get(0).remove()
    }
    GenericValue dataResource = from("DataResource").where(dataResourceId: dataResourceId).queryOne()
    Map removeImageFile = [productId: parameters.productId, contentId: parameters.contentId, objectInfo: dataResource.objectInfo, dataResourceName: dataResource.dataResourceName]
    run service: "removeImageFileForImageManagement", with: removeImageFile

    Map removeDataResourcePKMap = [dataResourceId: dataResourceId]
    run service: "removeDataResource", with: removeDataResourcePKMap
    return success()
}

/**
 * Set Image Detail
 * @return
 */
def setImageDetail() {
    GenericValue productContent = from("ProductContent").where(parameters).queryOne()
    productContent.sequenceNum = parameters.sequenceNum
    productContent.store()
    if (parameters.sequenceNum) {
        ScriptUtil.executeScript("component://product/groovyScripts/catalog/imagemanagement/SortSequenceNum.groovy", null, context)
        productContent.sequenceNum = parameters.sequenceNum
        productContent.store()
    }
    // set caption
    GenericValue content = from("Content").where(parameters).queryOne()
    content.description = parameters.description
    if (content.statusId == "IM_APPROVED") {
        GenericValue dataResource = from("DataResource").where(dataResourceId: content.dataResourceId).queryOne()
        dataResource.isPublic = parameters.drIsPublic
        dataResource.store()
    }
    return success()
}

/**
 * Update Status Image Management
 * @return
 */
def updateStatusImageManagement() {
    Timestamp nowTimestamp = UtilDateTime.nowTimestamp()
    String checkStatusVal = parameters.checkStatusId
    List statusId = StringUtil.split(checkStatusVal, "/")
    if (statusId) {
        parameters.checkStatusId = statusId.get(0)
    }
    String autoApproveImage = UtilProperties.getPropertyValue("catalog.properties", "image.management.autoApproveImage")
    String multipleApproval = UtilProperties.getPropertyValue("catalog.properties", "image.management.multipleApproval")
    if (autoApproveImage == "Y") {
        List contentApprovals = from("ContentApproval").where(contentId: parameters.contentId, roleTypeId: "IMAGEAPPROVER").queryList()
        for (GenericValue contentApproval : contentApprovals) {
            contentApproval.approvalStatusId = parameters.checkStatusId
            contentApproval.store()
        }
    } else {
        GenericValue contentApproval = from("ContentApproval").where(partyId: userLogin.partyId, contentId: parameters.contentId, roleTypeId: "IMAGEAPPROVER").queryFirst()
        contentApproval.approvalStatusId = parameters.checkStatusId
        contentApproval.store()
    }
    if (parameters.checkStatusId == "IM_REJECTED") {
        List checkRejects = from("ContentApproval").where(contentId: parameters.contentId, roleTypeId: "IMAGEAPPROVER").queryList()
        for (GenericValue checkReject : checkRejects) {
            checkReject.statusId = "IM_REJECTED"
            checkReject.store()
        }
        GenericValue content = from("Content").where(parameters).queryOne()
        content.statusId = "IM_REJECTED"
        content.createdByUserLogin = userLogin.userLoginId
        content.store()
    } else {
        if (parameters.checkStatusId == "IM_APPROVED") {
            if (multipleApproval == "Y") {
                Long countParty = from("ContentApproval").where(contentId: parameters.contentId, roleTypeId: "IMAGEAPPROVER").queryCount()
                if (countParty == (Long) 1) {
                    GenericValue content = from("Content").where(parameters).queryOne()
                    content.statusId = "IM_APPROVED"
                    content.store()

                    GenericValue productContent = from("ProductContent").where(contentId: parameters.contentId, productContentTypeId: "IMAGE").queryFirst()
                    productContent.purchaseFromDate = nowTimestamp
                    productContent.store()
                } else {
                    Long countApprove = from("ContentApproval").where(contentId: parameters.contentId, roleTypeId: "IMAGEAPPROVER", approvalStatusId: "IM_APPROVED").queryCount()
                    if (countApprove >= (Long) 2) {
                        GenericValue content = from("Content").where(parameters).queryOne()
                        content.statusId = "IM_APPROVED"
                        content.store()

                        GenericValue productContent = from("ProductContent").where(contentId: parameters.contentId, productContentTypeId: "IMAGE").queryFirst()
                        productContent.purchaseFromDate = nowTimestamp
                        productContent.store()

                        List checkApproveList = from("ContentApproval").where(contentId: parameters.contentId, roleTypeId: "IMAGEAPPROVER").queryList()
                        for (GenericValue checkApprove : checkApproveList) {
                            checkApprove.approvalStatusId = "IM_APPROVED"
                            checkApprove.store()
                        }
                    }
                }
            } else {
                GenericValue content = from("Content").where(parameters).queryOne()
                content.statusId = "IM_APPROVED"
                content.store()

                GenericValue productContent = from("ProductContent").where(contentId: parameters.contentId, productContentTypeId: "IMAGE").queryFirst()
                productContent.purchaseFromDate = nowTimestamp
                productContent.store()

                List checkApproveList = from("ContentApproval").where(contentId: parameters.contentId, roleTypeId: "IMAGEAPPROVER").queryList()
                for (GenericValue checkApprove : checkApproveList) {
                    checkApprove.approvalStatusId = "IM_APPROVED"
                    checkApprove.store()
                }
            }
        }
    }
    return success()
}

/**
 * Add Rejected Reason Image Management
 * @return
 */
def addRejectedReasonImageManagement() {
    Timestamp nowTimestamp = UtilDateTime.nowTimestamp()
    GenericValue content = from("Content").where(parameters).queryOne()
    if (parameters.description) {
        if (parameters.description == "RETAKE_PHOTO") {
            content.description = "Re-take Photo"
        }
        if (parameters.description == "REMOVE_LOGO") {
            content.description = "Remove Logo"
        }
        if (parameters.description == "OTHER") {
            content.description = "Other"
        }
    } else {
        content.description = "Other"
    }
    content.store()
    GenericValue productContent = from("ProductContent").where(contentId: parameters.contentId, productContentTypeId: "IMAGE").queryFirst()
    productContent.thruDate = nowTimestamp
    productContent.store()
    return success()
}

/**
 * Create Content Approval of Image
 * @return
 */
def createImageContentApproval() {
    Timestamp nowTimestamp = UtilDateTime.nowTimestamp()
    List partyRoles = from("PartyRole").where(roleTypeId: "IMAGEAPPROVER").queryList()
    for (GenericValue partyRole : partyRoles) {
        Map contentApproval = [partyId: partyRole.partyId, contentId: parameters.contentId, roleTypeId: "IMAGEAPPROVER", approvalDate: nowTimestamp, approvalStatusId: "IM_PENDING"]
        run service: "createContentApproval", with: contentApproval
    }
    return success()
}

/**
 * Remove Content Approval of Image
 * @return
 */
def removeImageContentApproval() {
    List contentApprovals = from("ContentApproval").where(partyId: parameters.partyId, roleTypeId: "IMAGEAPPROVER").queryList()
    for (GenericValue contentApproval : contentApprovals) {
        contentApproval.remove()
    }
    return success()
}

/**
 * Resize Images
 * @return
 */
def resizeImages() {
    if (parameters.resizeOption == "resizeAllImages") {
        List productContentAndInfos = from("ProductContentAndInfo").where(productId: parameters.productId, productContentTypeId: "IMAGE").queryList()
        // <field-map field-name="statusId" value="IM_APPROVED"/>
        for (GenericValue productContentAndInfo : productContentAndInfos) {
            Map resizeImageMap = [productId: productContentAndInfo.productId, dataResourceName: productContentAndInfo.drDataResourceName, resizeWidth: parameters.size]
            run service: "resizeImageOfProduct", with: resizeImageMap
        }
    }
    if (parameters.resizeOption == "createNewThumbnail") {
        Map removeImageBySizeMap = [productId: parameters.productId, mapKey: parameters.size]
        run service: "removeImageBySize", with: removeImageBySizeMap

        List productContentAndInfos = from("ProductContentAndInfo").where(productId: parameters.productId, productContentTypeId: "IMAGE").queryList()
        // <field-map field-name="statusId" value="IM_APPROVED"/>
        for (GenericValue productContentAndInfo : productContentAndInfos) {
            Map createNewImageThumbnailMap = [productId: productContentAndInfo.productId, contentId: productContentAndInfo.contentId, dataResourceName: productContentAndInfo.drDataResourceName, drObjectInfo: productContentAndInfo.drObjectInfo, sizeWidth: parameters.size]
            run service: "createNewImageThumbnail", with: createNewImageThumbnailMap
        }
    }
    return success()
}

/**
 * Remove Image By Size
 * @return
 */
def removeImageBySize() {
    List productContentAndInfos = from("ProductContentAndInfo").where(productId: parameters.productId, productContentTypeId: "IMAGE").queryList()
    // <field-map field-name="statusId" value="IM_APPROVED"/>
    for (GenericValue productContentAndInfo : productContentAndInfos) {
        List contentAssocs = from("ContentAssoc").where(contentId: productContentAndInfo.contentId, contentAssocTypeId: "IMAGE_THUMBNAIL", mapKey: parameters.mapKey).queryList()
        for (GenericValue contentAssoc : contentAssocs) {
            Map removeContent = [contentId: contentAssoc.contentIdTo, productId: parameters.productId]
            run service: "removeProductContentForImageManagement", with: removeContent
        }
    }
    return success()
}
