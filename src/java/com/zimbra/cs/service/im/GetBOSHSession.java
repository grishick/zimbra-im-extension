package com.zimbra.cs.service.im;

import java.util.Map;

import rocks.xmpp.core.XmppException;
import rocks.xmpp.core.session.XmppClient;
import rocks.xmpp.extensions.httpbind.BoshConnection;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.account.AuthTokenException;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Server;
import com.zimbra.cs.im.ZimbraIMExtension;
import com.zimbra.cs.service.account.AccountDocumentHandler;
import com.zimbra.soap.ZimbraSoapContext;
/**
 * @author Greg Solovyev
 */
public class GetBOSHSession extends AccountDocumentHandler {

    @Override
    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Element response = zsc.createElement(ZimbraIMExtension.GET_BOSH_SESSION_RESPONSE);
        // Connect
        XmppClient xmppSession = null;
        try {
            Provisioning prov = Provisioning.getInstance();
            Server localServer = prov.getLocalServer();
                        // Login
            xmppSession = ZimbraIMService.preBind(zsc.getAuthToken());
            xmppSession.login(zsc.getAuthToken().getAccount().getUid(),
                    String.format("__zmauth__%s", zsc.getAuthToken().getEncoded()));
            BoshConnection connection = (BoshConnection) (xmppSession.getActiveConnection());
            String sid = connection.getSessionId();
            String jid = xmppSession.getConnectedResource().toString();
            long rid = connection.detach();
            response.addUniqueElement("XMPPSession").addAttribute("sid", sid).addAttribute("rid", rid)
                    .addAttribute("jid", jid)
                    .addAttribute("url", localServer.getReverseProxyXmppBoshLocalHttpBindURL());
        } catch (AuthTokenException | XmppException e) {
            throw ServiceException.FAILURE(
                    "Caught an exception trying to authenticate to XMPP server " + e.getLocalizedMessage(), e);
        } finally {
            if (xmppSession != null) {
                try {
                    xmppSession.close();
                } catch (XmppException e) {
                    throw ServiceException.FAILURE("Caught an exception trying to close to XMPP session", e);
                }
            }
        }
        return response;
    }

}
