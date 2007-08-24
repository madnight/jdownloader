package jd.controlling.interaction;

import java.io.IOException;
import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;

import jd.plugins.Plugin;
import jd.router.RouterData;

/**
 * Diese Klasse führt einen Reconnect durch
 * 
 * @author astaldo
 */
public class HTTPReconnect extends Interaction{

    /**
     * serialVersionUID
     */
    private transient static final long serialVersionUID = 1332164738388120767L;

    @Override
    public boolean interact() {
        logger.info("Starting HTTPReconnect");
        String ipBefore;
        String ipAfter;
        RouterData routerData = configuration.getRouterData();
        String routerIP       = configuration.getRouterIP();
        String routerUsername = configuration.getRouterUsername();
        String routerPassword = configuration.getRouterPassword();
        int routerPort        = configuration.getRouterPort();
        String disconnect     = routerData.getConnectionDisconnect();
        String connect        = routerData.getConnectionConnect();
        if(routerUsername != null && routerPassword != null)
            Authenticator.setDefault(new InternalAuthenticator(routerUsername, routerPassword));

        //IP auslesen
//        ipBefore = getIPAddress(routerData);

        //Trennen
        logger.fine("disconnecting router");
        String routerPage;
        
        if(routerPort<=0)
            routerPage = "http://"+routerIP+"/";
        else
            routerPage = "http://"+routerIP+":"+routerPort+"/";

        if(routerData.getLoginType() == RouterData.LOGIN_TYPE_WEB_POST){
            routerPage +=disconnect;
            logger.fine("Router page:"+routerPage);
            try {
                Plugin.postRequest(
                        new URL(routerPage),
                        null,
                        null,
                        routerData.getDisconnectRequestProperties(), 
                        routerData.getDisconnectPostParams(),
                        true);
            }
            catch (MalformedURLException e) { e.printStackTrace(); }
            catch (IOException e)           { e.printStackTrace(); }
        }
        else{
            try {
                routerPage +=disconnect;
                logger.fine("Router page:"+routerPage);
                Plugin.getRequest(new URL(routerPage));
            }
            catch (MalformedURLException e) { e.printStackTrace(); }
            catch (IOException e)           { e.printStackTrace(); }
        }

        // Verbindung wiederaufbauen
        logger.fine("building connection");
        try {
            Plugin.getRequest(new URL(connect));
        }
        catch (MalformedURLException e) { e.printStackTrace(); }
        catch (IOException e)           { e.printStackTrace(); }

        // IP check
//        ipAfter = getIPAddress(routerData);
//        if(ipBefore.equals(ipAfter)){
//            logger.severe("IP address did not change");
//            return false;
//        }

        return true;
    }
//    private String getIPAddress(RouterData routerData){
//        try {
//            String urlForIPAddress = routerData.getIpAddressSite();
//            RequestInfo requestInfo = Plugin.getRequest(new URL(urlForIPAddress));
//            return routerData.getIPAdress(requestInfo.getHtmlCode());
//        }
//        catch (IOException e1) { e1.printStackTrace(); }
//        return null;
//
//    }
    @Override
    public String toString() { return "HTTPReconnect "+configuration.getRouterData(); }

    private class InternalAuthenticator extends Authenticator {
        private String username, password;

        public InternalAuthenticator(String user, String pass) {
            username = user;
            password = pass;
        }

        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(username, password.toCharArray());
        }
    }
}
