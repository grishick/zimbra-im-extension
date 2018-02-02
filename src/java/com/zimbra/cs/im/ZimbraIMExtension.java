/*
 * ***** BEGIN LICENSE BLOCK *****
 *
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2014, 2015, 2016 Synacor, Inc.
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
package com.zimbra.cs.im;

import org.dom4j.QName;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.extension.ExtensionDispatcherServlet;
import com.zimbra.cs.extension.ExtensionException;
import com.zimbra.cs.extension.ZimbraExtension;
import com.zimbra.cs.im.test.TestIMService;
import com.zimbra.cs.service.im.BOSHPreBindRESTHandler;
import com.zimbra.qa.unittest.ZimbraSuite;

/**
 * @author Greg Solovyev
 *
 */
public class ZimbraIMExtension implements ZimbraExtension {
    public static final String EXTENSION_NAME = "zimbraim";
    public static final String E_GET_BOSH_SESSION_REQUEST = "GetBOSHSessionRequest"; //TODO: deprecate once we switch to ConverseJS 0.9.4
    public static final String E_GET_BOSH_SESSION_RESPONSE = "GetBOSHSessionResponse"; //TODO: deprecate once we switch to ConverseJS 0.9.4
    public static final QName GET_BOSH_SESSION_REQUEST = QName.get(E_GET_BOSH_SESSION_REQUEST, MailConstants.NAMESPACE);
    public static final QName GET_BOSH_SESSION_RESPONSE = QName.get(E_GET_BOSH_SESSION_RESPONSE, MailConstants.NAMESPACE);
    public ZimbraIMExtension() {
        // TODO Auto-generated constructor stub
    }

    /* (non-Javadoc)
     * @see com.zimbra.cs.extension.ZimbraExtension#getName()
     */
    @Override
    public String getName() {
        return EXTENSION_NAME;
    }

    @Override
    public void init() throws ExtensionException, ServiceException {
        ExtensionDispatcherServlet.register(this, new BOSHPreBindRESTHandler());
        try {
            ZimbraSuite.addTest(TestIMService.class);
        } catch (NoClassDefFoundError e) {
            // Expected in production, because JUnit is not available.
            ZimbraLog.test.debug("Unable to load TestIMService unit tests.", e);
        }

    }

    @Override
    public void destroy() {
        ExtensionDispatcherServlet.unregister(this);
    }

}
