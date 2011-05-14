package Utils;

import static Utils.Redis.newConnection;

import models.User;
import models.oauthclient.Credentials;
import play.Play;
import play.libs.WS;
import play.libs.XML;
import play.modules.oauthclient.OAuthClient;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisShardInfo;

/**
 * @author Jean-Baptiste lemée
 */
public class Twitter {

   private static OAuthClient connector = null;

   public static OAuthClient getConnector() {
      if (connector == null) {
         connector = new OAuthClient(
                 "http://twitter.com/oauth/request_token",
                 "http://twitter.com/oauth/access_token",
                 "http://twitter.com/oauth/authorize",
                 Play.configuration.getProperty("twitter.apikey"),
                 Play.configuration.getProperty("twitter.apisecret"));
      }
      return connector;
   }

   public static User oauthCallback(String oauth_verifier) throws Exception {
      Credentials creds = new Credentials();
      getConnector().retrieveAccessToken(creds, oauth_verifier);

      // Get the screen name
      String url = "http://api.twitter.com/1/account/verify_credentials.xml";
      String xml = Twitter.getConnector().sign(creds, WS.url(url), "GET").get().getString();
      String twittername = XML.getDocument(xml).getElementsByTagName("screen_name").item(0).getTextContent();
      return User.findByTwitterOrCreate(twittername, newConnection());
   }
}
