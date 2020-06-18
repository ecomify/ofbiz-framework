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
import org.apache.ofbiz.entity.util.EntityUtil

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
    Map createMain = [contentAssocTypeId: contentAssocTypeId,
        contentIdFrom: contentIdFrom,
        ownerContentId: ownerContentId,
        dataTemplateTypeId: "SCREEN_COMBINED",
        mapKey: "MAIN"]
    if (parameters.templateDataResourceId) {
        createMain.dataResourceId = parameters.templateDataResourceId
    }
    if (parameters.contentName) {
        createMain.contentName = parameters.contentName
    }
    if (parameters.description) {
        createMain.description = parameters.description
    }
    if (parameters.statusId) {
        createMain.statusId = parameters.statusId
    }
    if (userLogin.partyId) {
        createMain.partyId = userLogin.partyId
    }

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
            contentAssocTypeId: contentAssocTypeId,
            contentIdFrom: contentIdFrom,
            isPublic: "Y"
        ]
        if (parameters.contentName){
            createImage.contentName = parameters.contentName
        }
        if (parameters.description) {
            createImage.description = parameters.description
        }
        if (parameters.statusId) {
            createImage.statusId = parameters.statusId
        }
        if (userLogin.partyId) {
            createImage.partyId = userLogin.partyId
        }
        if (parameters.uploadedFile) {
            createImage.uploadedFile = parameters.uploadedFile
        }
        if (parameters._uploadedFile_fileName) {
            createImage._uploadedFile_fileName = parameters._uploadedFile_fileName
        }
        if (parameters._uploadedFile_contentType) {
            createImage._uploadedFile_contentType = parameters._uploadedFile_contentType
        }

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
            contentAssocTypeId: contentAssocTypeId,
            contentIdFrom: contentIdFrom,
            mapKey: "ARTICLE"
        ]
        if (parameters.contentName) {
            createText.contentName = parameters.contentName
        }
        if (parameters.description) {
            createText.description = parameters.description
        }
        if (parameters.statusId) {
            createText.statusId = parameters.statusId
        }
        if (parameters.articleData) {
            createText.textData = parameters.articleData
        }
        if (userLogin.partyId) {
            createText.partyId = userLogin.partyId
        }

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
                contentAssocTypeId: contentAssocTypeId,
                textData: parameters.summaryData,
                contentIdFrom: contentIdFrom
            ]
            if (parameters.contentName) {
                createSummary.contentName = parameters.contentName
            }
            if (parameters.description) {
                createSummary.description = parameters.description
            }
            if (parameters.statusId) {
                createSummary.statusId = parameters.statusId
            }
            if (parameters.summaryData) {
                createSummary.textData = parameters.summaryData
            }
            if (userLogin.partyId) {
                createSummary.partyId = userLogin.partyId
            }

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
    GenericValue summaryText = serviceResult.summaryText

    if (parameters.contentName != contentName ||
    parameters.description !=  description ||
    parameters.summaryData != summaryData ||
    parameters.templateDataResourceId != templateDataResourceId ||
    parameters.statusId != statusId) {
        Map updContent = parameters
        updContent.dataResourceId = parameters.templateDataResourceId
        run service:"updateContent" , with: updContent
        if (parameters.statusId != statusId) {
            if (imageContent) {
                GenericValue status = imageContent.status
                status.Id = parameters.statusId
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
            contentAssocTypeId: contentAssocTypeId,
            contentIdFrom: contentIdFrom
        ]
        if (parameters.contentName) {
            createText.contentName = parameters.contentName
        }
        if (parameters.description) {
            createText.description = parameters.description
        }
        if (parameters.statusId) {
            createText.statusId = parameters.statusId
        }
        if (parameters.articleData) {
            createText.textData = parameters.articleData
        }
        if (userLogin.partyId) {
            createText.partyId = userLogin.partyId
        }

        run service:"createTextContent", with: createText
    }

    // update article text
    if (articleText && (parameters.articleData != articleData)) {
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
            contentAssocTypeId: contentAssocTypeId,
            contentIdFrom: contentIdFrom
        ]
        if (parameters.contentName) {
            createSummary.contentName = parameters.contentName
        }
        if (parameters.description) {
            createSummary.description = parameters.description
        }
        if (parameters.statusId) {
            createSummary.statusId = parameters.statusId
        }
        if (parameters.summaryData) {
            createSummary.textData = parameters.summaryData
        }
        if (userLogin.partyId) {
            createSummary.partyId = userLogin.partyId
        }

        run service:"createTextContent", with: createSummary
    }

    // update summary text
    if (summaryData && (parameters.summaryData != summaryData)) {
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
            contentAssocTypeId: "SUB_CONTENT",
            isPublic: "Y"
        ]
        if (parameters.contentId) {
            createImage.contentIdFrom = parameters.contentId
            createImage.ownerContentId = parameters.contentId
        }
        if (userLogin.partyId) {
            createImage.partyId = userLogin.partyId
        }
        if (parameters.uploadedFile) {
            createImage.uploadedFile = parameters.uploadedFile
        }
        if (parameters._uploadedFile_fileName) {
            createImage._uploadedFile_fileName = parameters._uploadedFile_fileName
        }
        if (parameters._uploadedFile_contentType) {
            createImage._uploadedFile_contentType = parameters._uploadedFile_contentType
        }

        createImage.contentName = parameters.contentName ?: contentName
        createImage.description = parameters.description ?: description
        createImage.statusId = parameters.statusId ?: statusId
        run service:"createContentFromUploadedFile", with: createImage
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
        result.contentId = parameters?.blogContentId
        return result
    }

    GenericValue content = from("Content").where(parameters).queryOne()
    List rawAssocs = delegator.getRelated("FromContentAssoc", null, null, content, false)
    List assocs = EntityUtil.filterByDate(rawAssocs)
    for (GenericValue assoc : assocs) {
        if (assoc.mapKey == "ARTICLE") {
            mainContent = delegator.getRelatedOne("ToContent", assoc, false)
            dataResource = delegator.getRelatedOne("DataResource", mainContent, false)
            articleText = delegator.getRelatedOne("ElectronicText", dataResource, false)
        }
        if (assoc.mapKey == "SUMMARY") {
            summaryContent = delegator.getRelatedOne("ToContent", assoc, false)
            dataResource = delegator.getRelatedOne("DataResource", summaryContent, false)
            summaryText = delegator.getRelatedOne("ElectronicText", dataResource, false)
        }
        if (assoc.mapKey == "IMAGE") {
            imageContent = delegator.getRelatedOne("ToContent", assoc, false)

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
            result.articleText = articleText
            result.summaryText = summaryText
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
