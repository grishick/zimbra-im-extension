--[[
 ***** BEGIN LICENSE BLOCK *****
 Zimbra Collaboration Suite Server
 Copyright (C) 2015, 2016 Synacor, Inc.

 This program is free software: you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software Foundation,
 version 2 of the License.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 See the GNU General Public License for more details.
 You should have received a copy of the GNU General Public License along with this program.
 If not, see <https://www.gnu.org/licenses/>.
 * ***** END LICENSE BLOCK *****
--]]
-- Compatibility: Prosody 0.8

local new_sasl = require "util.sasl".new
local nodeprep = require "util.encodings".stringprep.nodeprep
local soap_encoder = require "soap"
local zimbra_client = require "soap.client"
local zimbra_admin_client = require "soap.client"
zimbra_admin_client.https = require "ssl.https"

local zimbra_admin = assert(module:get_option_string("zimbra_admin"), "zimbra_admin is a required option for auth_zimbra")
local zimbra_admin_pw = assert(module:get_option_string("zimbra_admin_pw"),"zimbra_admin_pw is a required option for auth_zimbra")
local zimbra_host_port = module:get_option_string("zimbra_host_port") or "localhost"
local zimbra_admin_host_port = module:get_option_string("zimbra_admin_host_port") or "localhost:7071"
local zimbra_proto = module:get_option_string("zimbra_proto") or "https"
local domain = module:get_option_string("zimbra_domain") or module.host

local account_ns = "urn:zimbraAccount"
local admin_ns = "urn:zimbraAdmin"
local zimbra_url = zimbra_proto .. "://" .. zimbra_host_port .. "/service/soap/"
local zimbra_admin_url = "https://" .. zimbra_admin_host_port .. "/service/admin/soap/"

rawset(_G, "PROXY", false)

if zimbra_proto=="https" then
    zimbra_client.https = require "ssl.https"
end

local provider = { name = "zimbra" }

function provider.get_admin_token()
    local authToken =  get_auth_token(zimbra_admin, zimbra_admin_pw, admin_ns, zimbra_admin_url, zimbra_admin_client)
    module:log ("debug", "obtained admin auth token %s ", authToken)
    return authToken
end

function provider.test_password(username, password)
    local pwdPrefix = string.sub(password,1,10)
    if(pwdPrefix == "__zmauth__") then
        -- test auth_token cookie
        module:log("debug", "validating auth token for %s", username)
        return validate_auth_token(username .. "@" .. domain, string.sub(password,11), account_ns, zimbra_url, zimbra_client) 
    else
        -- test login/password
        module:log("debug", "validating password for %s", username)
        local authToken = get_auth_token(username .. "@" .. domain, password, account_ns, zimbra_url, zimbra_client)
        if authToken ~= nil then
            return true
        else
            return nil, "Auth failed. Invalid username or password."
        end
    end
end

function validate_auth_token(username, authToken, namespace, url, client)
    module:log("debug", "Validating token %s", authToken)
    local ns, meth, ent = client.call({
        namespace = namespace,
        soapversion = 1.2,
        method = "GetInfoRequest",
        url = url,
        header = {
            tag = "context",
            attr = {"xmlns", ["xmlns"] = "urn:zimbra"},
            {
                tag = "authToken",
                authToken
            },
            {
                tag = "nosession"
            },
            {
                tag = "userAgent",
                attr = {"name", ["name"] =  "prosody_zimbra_module"}
            }
        },
        attr = {"sections", ["sections"] = "mbox"},
        entries = {}
    })

    module:log("debug", "sent a SOAP request to Zimbra")
    if  ent ~= nil then
        for k, v in pairs(ent) do
            module:log("debug", "checking Zimbra SOAP Response %s %s", tostring(k), tostring(v[1]))
            if v.tag == "name" then
                module:log("debug", "Found user %s in Zimbra SOAP response for login %s", tostring(v[1]) or "", username)
                return v[1] == username
            else
                module:log("debug", "Zimbra SOAP Response %s = %s ", v[1].tag or "undefined-tag", tostring(v[1][1]))
            end
        end
    else
        module:log("debug", "Zimbra SOAP response is empty")
    end
    return false
end

function get_auth_token(username, password, namespace, url, client)
    local ns, meth, ent = client.call({
        namespace = namespace,
        soapversion = 1.2,
        method = "AuthRequest",
        url = url,
        header = {
            tag = "context",
                attr = {"namespace", ["namespace"] = "urn:zimbra"},
                {
                    tag = "authToken"
                },
                {
                    tag = "nosession"
                },
                {
                    tag = "userAgent",
                    attr = {"name", ["name"] =  "zmsoap"}
                }
        },
        entries = {
                    {
                        tag = "account",
                        attr = {"by", ["by"] = "name"},
                        username
                    },
                    {
                        tag = "password",
                        password
                    }
        }
    })
    local authToken = nil 
    if  ent ~= nil then
        for k, v in  pairs(ent) do
            if v.tag == "authToken" then
                authToken = v[1]
                break
            end 
        end
    end
    return authToken
end

function provider.set_password(username, password)
    return nil, "Changing passwords not supported"
end

function provider.user_exists(username)
    module:log("debug","checking if user %s exists", username)
    local authToken =  provider.get_admin_token()
    local ns, meth, ent = zimbra_admin_client.call({
        namespace = admin_ns,
        soapversion = 1.2,
        method = "GetAccountInfoRequest",
        url = zimbra_admin_url,
        header = {
            tag = "context",
            attr = {"xmlns", ["xmlns"] = "urn:zimbra"},
            {
                tag = "authToken",
                authToken
            },
            {
                tag = "nosession"
            },
            {
                tag = "userAgent",
                attr = {"name", ["name"] =  "prosody_zimbra_module"}
            }
        },
        entries = {
            {
                tag = "account",
                attr = {"by", ["by"] = "name"},
                username .. "@" .. domain
            }
        }
    }) 

    if  ent ~= nil then
        for k, v in  pairs(ent) do
            if v.tag == "name" then
                return true
            else
                module:log("debug", "Zimbra SOAP Response %s = %s ", v[1], v[1][1])
            end
        end
    end
    return false
end

function provider.create_user(username, password)
    return nil, "User creation not supported"
end

function provider.delete_user(username)
    return nil , "User deletion not supported"
end

function provider.get_sasl_handler()
    local testpass_authentication_profile = {
        plain_test = function(sasl, username, password, realm)
            local stripped_name = string.gsub(username, "@" .. domain, "") 
            local prepped_username = nodeprep(stripped_name)
            if not prepped_username then
                module:log("debug", "NODEprep failed on stripped username: %s", stripped_name)
                return "", nil
            end
            return provider.test_password(stripped_name, password), true
        end
    }
    return new_sasl(module.host, testpass_authentication_profile)
end

module:log("debug", "loading zimbra auth module")
module:add_item("auth-provider", provider)