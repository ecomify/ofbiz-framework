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
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.GenericValue

/**
 * Create a new Blog Entry
 * @return
 */
def createBlogEntry() {
    Map result = success()
    String contentAssocTypeId = "PUBLISH_LINK"
    String ownerContentId = parameters.blogContentId
    String contentIdFrom = parameters.blogContentId
    if (!parameters.statusId) {
        parameters.statusId = "CTNT_INITIAL_DRAFT"
    }
    if (!parameters.templateDataResourceId) {
        parameters.templateDataResourceId = "BLOG_TPL_TOPLEFT"
    }

    //determine of we need to create complex template structure or simple content structure
    if (!parameters.contentName) {
        return error(UtilProperties.getMessage("ContentUiLabels", "ContentArticleNameIsMissing", parameters.locale))
    }

    // complex template structure (image & text)
    Map createMain = [dataResourceId: parameters.templateDataResourceId,
        contentAssocTypeId: contentAssocTypeId,
        contentName: parameters.contentName,
        description: parameters.description,
        statusId: parameters.statusId,
        contentIdFrom: contentIdFrom,
        partyId: userLogin.partyId,
        ownerContentId: ownerContentId,
        dataTemplateTypeId: "SCREEN_COMBINED",
        mapKey: "MAIN"
    ]
    Map serviceResult = run service:"createContent", with: createMain
    String contentId = serviceResult.contentId

    // reset contentIdFrom to new contentId
    contentAssocTypeId = "SUB_CONTENT"
    contentIdFrom = contentId

    if (parameters._uploadedFile_fileName) {
        // upload a picture
        Map createImage = [dataResourceTypeId: "LOCAL_FILE",
            dataTemplateTypeId: "NONE",
            mapKey: "IMAGE",
            ownerContentId: ownerContentId,
            contentName: parameters.contentName,
            description: parameters.description,
            statusId: parameters.statusId,
            contentAssocTypeId: contentAssocTypeId,
            contentIdFrom: contentIdFrom,
            partyId: userLogin.partyId,
            isPublic: "Y",
            uploadedFile: parameters.uploadedFile,
            _uploadedFile_fileName: parameters._uploadedFile_fileName,
            _uploadedFile_contentType: parameters._uploadedFile_contentType,
        ]
        Map serviceResultCCFUF = run service:"createContentFromUploadedFile", with: createImage
        String imageContentId = serviceResultCCFUF.contentId
    }

    if (parameters.articleData) {
        // create text data
        Map createText = [dataResourceTypeId: "ELECTRONIC_TEXT",
            contentPurposeTypeId: "ARTICLE",
            dataTemplateTypeId: "NONE",
            mapKey: "MAIN",
            ownerContentId: ownerContentId,
            contentName: parameters.contentName,
            description: parameters.description,
            statusId: parameters.statusId,
            contentAssocTypeId: contentAssocTypeId,
            textData: parameters.articleData,
            contentIdFrom: contentIdFrom,
            partyId: userLogin.partyId,
            mapKey: "ARTICLE"
        ]
        logInfo("calling createTextContent with map: ${createText}")
        Map serviceResultCTC = run service:"createTextContent", with: createText
        String textContentId = serviceResultCTC.contentId
    }

    if (contentId) {
        if (parameters.summaryData) {
            // create the summary data
            Map createSummary = [dataResourceTypeId: "ELECTRONIC_TEXT",
                contentPurposeTypeId: "ARTICLE",
                dataTemplateTypeId: "NONE",
                mapKey: "SUMMARY",
                ownerContentId: ownerContentId,
                contentName: parameters.contentName,
                description: parameters.description,
                statusId: parameters.statusId,
                contentAssocTypeId: contentAssocTypeId,
                textData: parameters.summaryData,
                contentIdFrom: contentIdFrom,
                partyId: userLogin.partyId
            ]
            run service:"createTextContent", with: createSummary
        }
    }

    result.contentId = contentIdFrom
    result.blogContentId = parameters.blogContentId
    return result
}

/**
 * Update a existing Blog Entry
 * @return
 */
def updateBlogEntry() {
    Map result = success()
    String ownerContentId
    String contentAssocTypeId
    String contentIdFrom
    String showNoResult = "Y"
    parameters.showNoResult = showNoResult

    Map serviceResult = run service:"getBlogEntry", with: parameters
    String contentId = serviceResult.contentId
    String contentName = serviceResult.contentName
    String statusId = serviceResult.statusId
    String description = serviceResult.description
    String templateDataResourceId = serviceResult.templateDataResourceId
    String summaryData = serviceResult.summaryData
    String articleData = serviceResult.articleData
    String imageContentId = serviceResult.imageContentId
    String articleContentId = serviceResult.articleContentId
    String summaryContentId = serviceResult.summaryContentId
    GenericValue imageContent = serviceResult.imageContent
    GenericValue articleText = serviceResult.articleText

    if (parameters.contentName != contentName ||
    parameters.description !=  description ||
    parameters.summaryData != summaryData ||
    parameters.templateDataResourceId != templateDataResourceId ||
    parameters.statusId != statusId) {
        Map updContent = [:]
        updContent << parameters
        updContent.dataResourceId = parameters.templateDataResourceId
        run service:"updateContent" , with: updContent
        if (parameters.statusId != statusId) {
            if (imageContent) {
                imageContent.status.Id = parameters.statusId
                imageContent.store()
            }
        }
    }

    // new article text
    if (!articleText && parameters.articleData) {
        ownerContentId = parameters.blogContentId
        contentAssocTypeId = "SUB_CONTENT"
        contentIdFrom = contentId
        Map createText = [dataResourceTypeId: "ELECTRONIC_TEXT",
            contentPurposeTypeId: "ARTICLE",
            dataTemplateTypeId: "NONE",
            mapKey: "ARTICLE",
            ownerContentId: ownerContentId,
            contentName: parameters.contentName,
            description: parameters.description,
            statusId: parameters.statusId,
            contentAssocTypeId: contentAssocTypeId,
            textData: parameters.articleData,
            contentIdFrom: contentIdFrom,
            partyId: userLogin.partyId
        ]
        run service:"createTextContent", with: createText
    }

    // update article text
    if (articleText && parameters.articleData != articleData) {
        articleText.textData = parameters.articleData
        articleText.store()
    }

    // create summary text
    if (!summaryData && parameters.summaryData) {
        // create the summary data
        ownerContentId = parameters.blogContentId
        contentAssocTypeId = "SUB_CONTENT"
        contentIdFrom = contentId
        Map createSummary = [dataResourceTypeId: "ELECTRONIC_TEXT",
            contentPurposeTypeId: "ARTICLE",
            dataTemplateTypeId: "NONE",
            mapKey: "SUMMARY",
            ownerContentId: ownerContentId,
            contentName: parameters.contentName,
            description: parameters.description,
            statusId: parameters.statusId,
            contentAssocTypeId: contentAssocTypeId,
            textData: parameters.summaryData,
            contentIdFrom: contentIdFrom,
            partyId: userLogin.partyId
        ]
        run service:"createTextContent", with: createSummary
    }

    // update summary text
    if (summaryData && parameters.summaryData != summaryData) {
        GenericValue summaryText = new GenericValue()
        summaryText.textData = parameters.summaryData
        summaryText.store()
    }

    if (parameters._uploadedFile_fileName) {
        if (imageContent) {
            GenericValue oldAssoc = from("ContentAssoc")
                    .where(contentId: contentId, contentIdTo: imageContent.contentId, mapKey: "IMAGE")
                    .filterByDate()
                    .queryFirst()
            Timestamp nowTimestamp = UtilDateTime.nowTimestamp()
            oldAssoc.thruDate = nowTimestamp
            oldAssoc.store()
        }
        // upload a picture
        Map createImage = [dataResourceTypeId: "LOCAL_FILE",
            dataTemplateTypeId: "NONE",
            mapKey: "IMAGE",
            ownerContentId: parameters.contentId,
            contentAssocTypeId: "SUB_CONTENT",
            contentIdFrom: parameters.contentId,
            partyId: userLogin.partyId,
            isPublic: "Y",
            uploadedFile: parameters.uploadedFile,
            _uploadedFile_fileName: parameters._uploadedFile_fileName,
            _uploadedFile_contentType: parameters._uploadedFile_contentType
        ]
        createImage.contentName = parameters.contentName ?: "${contentName}"
        createImage.description = parameters.description ?: "${description}"
        createImage.statusId = parameters.statusId ?: "${statusId}"
        run service:"createContentFromUploadedFile", with:createImage
    }

    result.contentId = parameters.contentId
    result.blogContentId = parameters.blogContentId

    return result
}

/**
 * Get blog entries that the user owns or are published
 * @return
 */
def getOwnedOrPublishedBlogEntries() {
    Map result = success()
    List blogItems = from("ContentAssocViewTo")
            .where(contentIdStart: parameters.contentId, caContentAssocTypeId: "PUBLISH_LINK")
            .orderBy("caFromDate DESC")
            .filterByDate()
            .queryList()
    List blogList = []
    for (GenericValue blogItem : blogItems) {
        Map mapIn = [:]
        mapIn << blogItem
        mapIn.ownerContentId = parameters.contentId
        mapIn.mainAction = "VIEW"
        Map serviceResult = run service:"genericContentPermission", with: mapIn
        Boolean hasPermission = serviceResult.hasPermission
        if (hasPermission) {
            blogList.add(blogItem)
        }
        else {
            mapIn.mainAction = "UPDATE"
            Map serviceResultGCP = run service:"genericContentPermission", with: mapIn
            hasPermission = serviceResultGCP.hasPermission
            if (hasPermission) {
                blogList.add(blogItem)
            }
        }
    }

    result.blogList = blogList
    result.blogContentId = parameters.blogContentId

    return result
}

/**
 * Get all the info for a blog article
 * @return
 */
def getBlogEntry() {
    Map result = success()
    GenericValue mainContent
    GenericValue dataResource
    GenericValue articleText
    GenericValue summaryContent
    GenericValue summaryText
    String summaryData
    GenericValue imageContent
    String contentId
    String contentName
    String description
    String statusId
    String showNoResult = parameters.showNoResult
    String articleData

    if (!parameters.contentId) {
        result.blogContentId = parameters.contentId
        return result
    }

    GenericValue content = from("Content").where(parameters).queryOne()
    List orderBy = ["createdDate"]
    List assocs = content.getRelated("FromContentAssoc", null, orderBy, false)
    for (GenericValue assoc : assocs) {
        String test = assoc.mapKey
        if (assoc.mapKey == "ARTICLE") {
            mainContent = assoc.getRelatedOne("ToContent", false)
            dataResource = mainContent.getRelatedOne("DataResource", false)
            articleText = dataResource.getRelatedOne("ElectronicText", false)
        }
        if (assoc.mapKey == "SUMMARY") {
            summaryContent = assoc.getRelatedOne("ToContent", false)
            dataResource = summaryContent.getRelatedOne("DataResource", false)
            summaryText = dataResource.getRelatedOne("ElectronicText", false)
        }
        if (assoc.mapKey == "assoc.mapKey") {
            imageContent = assoc.getRelatedOne("ToContent", false)
            
        }
        if (!showNoResult) {
            result.contentId = content?.contentId
            result.contentName = content?.contentName
            result.description = content?.description
            result.statusId = content?.statusId

            if(imageContent) {
                result.templateDataResourceId = content.dataResourceId
                result.imageContent = imageContent
            }
            result.articleData = articleText?.textData
            result.summaryData = summaryText?.textData
            result.imageContentId = imageContent?.contentId
            result.articleContentId = mainContent?.contentId
            result.summaryContentId = summaryContent?.contentId
            result.blogContentId = parameters?.blogContentId
            result.articleText = articleText?.textData
        } else {
            contentId = content?.contentId
            contentName = content?.contentName
            description = content?.description
            statusId = content?.statusId
            String templateDataResourceId = content?.dataResourceId
            articleData = articleText?.textData
            summaryData = summaryText?.textData
            String imageDataResourceId = imageContent?.dataResourceId
        }
    }
    return result
}
