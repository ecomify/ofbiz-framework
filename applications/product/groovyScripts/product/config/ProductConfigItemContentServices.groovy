import java.sql.Timestamp
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.service.ServiceUtil

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

// ProductConfigItemContent
/**
 * Create Content For ProductConfigItem
 */
def createProductConfigItemContent() {
    GenericValue newEntity = makeValue("ProdConfItemContent", parameters)
    if (!newEntity.fromDate) {
        Timestamp nowTimestamp = UtilDateTime.nowTimestamp()
        newEntity.fromDate = nowTimestamp
    }
    newEntity.create()

    run service: "updateContent", with: parameters

    Map result = success()
    result.contentId = newEntity.contentId
    result.configItemId = newEntity.configItemId
    result.confItemContentTypeId = newEntity.confItemContentTypeId

    return result
}

/**
 * Update Content For ProductConfigItem
 */
def updateProductConfigItemContent() {
    GenericValue lookedUpValue = from("ProdConfItemContent").where(parameters).queryOne()
    lookedUpValue.setNonPKFields(parameters)
    lookedUpValue.store()

    run service: "updateContent", with: parameters

    return success()
}

// Specialized
/**
 * Create Simple Text Content For Product
 */
def createSimpleTextContentForProductConfigItem() {
    Map createProductConfigItemContent = parameters
    Map serviceResult = run service: "createSimpleTextContent", with: parameters
    if (!ServiceUtil.isSuccess(serviceResult)) {
        return serviceResult
    }
    createProductConfigItemContent.contentId = serviceResult.contentId
    run service: "createProductConfigItemContent", with: createProductConfigItemContent

    return success()
}