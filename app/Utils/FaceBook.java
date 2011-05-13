package Utils;

import static Utils.Redis.newConnection;

import models.User;
import models.oauthclient.Credentials;
import play.Play;
import play.libs.WS;
import play.libs.XML;
import play.modules.oauthclient.OAuthClient;

/**
 * @author Jean-Baptiste lemée
 */
public class FaceBook {

   private static OAuthClient connector = null;

   public static OAuthClient getConnector() {
      if (connector == null) {
         connector = new OAuthClient(
                 "http://twitter.com/oauth/request_token",
                 "http://twitter.com/oauth/access_token",
                 "http://twitter.com/oauth/authorize",
                 Play.configuration.getProperty("facebook.apikey"),
                 Play.configuration.getProperty("facebook.apisecret"));
      }
      return connector;
   }

   public static User oauthCallback(String oauth_verifier) throws Exception {
      Credentials creds = new Credentials();
      getConnector().retrieveAccessToken(creds, oauth_verifier);

      // Get the facebook name
      /*String url = "http://api.twitter.com/1/account/verify_credentials.xml";
      String xml = FaceBook.getConnector().sign(creds, WS.url(url), "GET").get().getString();
      String email = XML.getDocument(xml).getElementsByTagName("screen_name").item(0).getTextContent();
      return User.findByMailOrCreate(email, newConnection()); */
      return null;
   }
}
