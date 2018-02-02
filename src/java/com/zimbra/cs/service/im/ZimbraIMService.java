package com.zimbra.cs.service.im;

import java.util.Arrays;

import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.XmppException;
import rocks.xmpp.core.session.XmppClient;
import rocks.xmpp.core.session.XmppSessionConfiguration;
import rocks.xmpp.extensions.httpbind.BoshConnectionConfiguration;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AuthToken;
import com.zimbra.cs.account.AuthTokenException;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Server;
import com.zimbra.cs.im.ZimbraIMExtension;
import com.zimbra.soap.DocumentDispatcher;
import com.zimbra.soap.DocumentService;
/**
 * @author Greg Solovyev
 */
public class ZimbraIMService implements DocumentService {

    public ZimbraIMService() {
        // TODO Auto-generated constructor stub
    }

    public void registerHandlers(DocumentDispatcher dispatcher) {
        dispatcher.registerHandler(ZimbraIMExtension.GET_BOSH_SESSION_REQUEST, new GetBOSHSession());
    }

    public static XmppClient preBind(AuthToken authToken) throws ServiceException {
        XmppClient xmppSession = null;
        try {
            Provisioning prov = Provisioning.getInstance();
            Server localServer = prov.getLocalServer();
            BoshConnectionConfiguration.Builder boshConnectionConfigurationBuilder = BoshConnectionConfiguration
                    .builder().hostname(localServer.getReverseProxyXmppBoshHostname())
                    .port(localServer.getReverseProxyXmppBoshPort()).secure(false);
            if (localServer.getReverseProxyXmppBoshRemoteHttpBindURL() != null
                    && localServer.getReverseProxyXmppBoshRemoteHttpBindURL().length() > 0) {
                boshConnectionConfigurationBuilder = boshConnectionConfigurationBuilder.file(localServer
                        .getReverseProxyXmppBoshRemoteHttpBindURL());
            }
            BoshConnectionConfiguration boshConnectionConfiguration = boshConnectionConfigurationBuilder.build();

            Class<?>[] extensions = new Class<?>[0];
            Arrays.asList(extensions, XmppClient.class);
            XmppSessionConfiguration configuration = XmppSessionConfiguration.builder().defaultResponseTimeout(5000)
                    .build();
            Account acc = authToken.getAccount();
            xmppSession = new XmppClient(acc.getDomainName(), configuration,
                    boshConnectionConfiguration);
            xmppSession.connect(new Jid(acc.getUid(),acc.getDomainName()));
            // Login
            xmppSession.login(authToken.getAccount().getUid(),
                    String.format("__zmauth__%s", authToken.getEncoded()));
        } catch (AuthTokenException | XmppException e) {
            throw ServiceException.FAILURE(
                    "Caught an exception trying to authenticate to XMPP server " + e.getLocalizedMessage(), e);
        }
        return xmppSession;
    }
}
