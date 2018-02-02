/**
 * 
 */
package com.zimbra.cs.service.im;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONObject;

import rocks.xmpp.core.XmppException;
import rocks.xmpp.core.session.XmppClient;
import rocks.xmpp.core.session.XmppSession;
import rocks.xmpp.extensions.httpbind.BoshConnection;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.AuthToken;
import com.zimbra.cs.extension.ExtensionHttpHandler;
import com.zimbra.cs.servlet.ZimbraServlet;

/**
 * @author Greg Solovyev
 *
 */
public class BOSHPreBindRESTHandler extends ExtensionHttpHandler {
    /**
     * Processes HTTP GET requests. Returns a JSON object with RID, JID and SID as expected by ConverseJS
     * See ConverseJS documentation for more information: https://conversejs.org/docs/html/configuration.html#prebind-url
     {
        "jid": "me@example.com/resource",
        "sid": "346234623462",
        "rid": "876987608760"
     }
     * @param req
     * @param resp
     * @throws IOException
     * @throws ServletException
     */
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws
        IOException, ServletException {
        AuthToken token = ZimbraServlet.getAuthTokenFromCookie(req, resp);
        XmppClient xmppSession = null;
        try {
            xmppSession = ZimbraIMService.preBind(token);
            BoshConnection connection = (BoshConnection) (xmppSession.getActiveConnection());
            String sid = connection.getSessionId();
            String jid = xmppSession.getConnectedResource().toString();
            long rid = connection.detach();
            JSONObject response = new JSONObject();
            response.put("jid", jid);
            response.put("sid", sid);
            response.put("rid", rid);
            byte[] content = response.toString().getBytes();
            resp.setContentLength(content.length);
            if (content != null && content.length > 0) {
                resp.setContentType("application/json");
                resp.getOutputStream().write(content);//write to output
            }
        } catch (ServiceException | JSONException e) {
            ZimbraLog.extensions.error("Caught an exception trying to close to XMPP session", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "ServiceException thrown trying to close to XMPP session");
        } finally {
            if (xmppSession != null) {
                try {
                    xmppSession.close();
                } catch (XmppException e) {
                    ZimbraLog.extensions.error("Caught an exception trying to close to XMPP session", e);
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "IOException thrown trying to close to XMPP session");
                }
            }
        }
    }
    
    /**
     * Processes HTTP POST requests.
     * @param req
     * @param resp
     * @throws IOException
     * @throws ServletException
     */
    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        throw new ServletException("HTTP POST requests are not supported");
    }
}
