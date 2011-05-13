package Utils;

import static Utils.Redis.newConnection;

import com.restfb.FacebookClient;
import models.User;
import models.oauthclient.Credentials;
import play.Play;
import play.libs.WS;
import play.libs.XML;
import play.modules.facebook.FbGraph;
import play.modules.oauthclient.OAuthClient;

/**
 * @author Jean-Baptiste lemée
 */
public class FaceBook {

   private static OAuthClient connector = null;

   public static OAuthClient getConnector() {
      if (connector == null) {
         connector = new OAuthClient(
                 "https://www.facebook.com/dialog/oauth/request_token",
                 "https://www.facebook.com/dialog/oauth/access_token",
                 "https://www.facebook.com/dialog/oauth/authorize",
                 Play.configuration.getProperty("fbg.appId"),
                 Play.configuration.getProperty("fbg.appSecret"));
      }
      return connector;
   }

   public static User oauthCallback(String oauth_verifier) throws Exception {
      FacebookClient fbClient = FbGraph.getFacebookClient();
      com.restfb.types.User profile = fbClient.fetchObject("me", com.restfb.types.User.class);
      String email = profile.getEmail();
      return User.findByMailOrCreate(email, newConnection());
   }
}
