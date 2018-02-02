/*
 * ***** BEGIN LICENSE BLOCK *****
 *
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2015, 2016 Synacor, Inc.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software Foundation,
 * version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.im.test;

import java.net.URI;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;

import com.zimbra.client.ZGetInfoResult;
import com.zimbra.client.ZMailbox;
import com.zimbra.common.httpclient.HttpClientUtil;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.httpclient.URLUtil;
import com.zimbra.qa.unittest.TestUtil;

import junit.framework.TestCase;
public class TestIMService extends TestCase {
    private static final String USER_NAME = "user1";

    @Test
    public void testPreBindJSON() throws Exception {
        Provisioning prov = Provisioning.getInstance();
        //Until we figure out how to create a simple mock XMPP server, an instance of XMPP server is required to run this test
        Assume.assumeNotNull(prov.getLocalServer().getReverseProxyXmppBoshLocalHttpBindURL());
        Assume.assumeNotNull(prov.getLocalServer().getReverseProxyXmppBoshRemoteHttpBindURL());
        Assume.assumeNotNull(prov.getLocalServer().getReverseProxyXmppBoshPort());
        Assume.assumeNotNull(prov.getLocalServer().getReverseProxyXmppBoshHostname());

        Account acc = TestUtil.getAccount(USER_NAME);
        acc.setFeatureChatEnabled(true);
        ZMailbox mbox = TestUtil.getZMailbox(USER_NAME);
        String zimbraIMURL = URLUtil.getPublicURLForDomain(prov.getLocalServer(), prov.getDomain(acc), "/service/extension/zimbraim", false);
        URI zimbraImURI = new URI(zimbraIMURL);
        HttpClient client = mbox.getHttpClient(zimbraImURI);
        GetMethod get = new GetMethod(zimbraIMURL);
        int statusCode = HttpClientUtil.executeMethod(client, get);
        assertEquals(200, statusCode);
        String resp = get.getResponseBodyAsString();
        assertNotNull(resp);
        JSONObject jsonResponse = new JSONObject(resp);
        assertNotNull(jsonResponse);
        assertNotNull(jsonResponse.get("jid"));
        assertNotNull(jsonResponse.get("rid"));
        assertNotNull(jsonResponse.get("sid"));
        assertEquals("jid should start with user's name", 0, jsonResponse.getString("jid").indexOf(USER_NAME));
    }
}
