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


/**
 * create Account Group
 */
def createAccount() {

    Map result = success()
    parameters.roleTypeId = "ACCOUNT"
    
    Map serviceResult = run service:"createPartyGroupRoleAndContactMechs", with:parameters
    String partyId = serviceResult.partyId
    result.partyId = partyId
    result.successMessage = serviceResult.successMessage

    Map ensurePartyRoleCtx = [partyId: userLogin.partyId, roleTypeId: "OWNER"]
    Map serviceResultEPR = run service: "ensurePartyRole", with: ensurePartyRoleCtx

    Map partyRelationshipCtx = [partyIdFrom: userLogin.partyId, partyIdTo: partyId, roleTypeIdFrom: "OWNER", roleTypeIdTo: "ACCOUNT", partyRelationshipTypeId: "ACCOUNT"]
    run service: "createPartyRelationship", with: partyRelationshipCtx

    return result
}
