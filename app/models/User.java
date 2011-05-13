package models;

import static Utils.Redis.newConnection;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.data.validation.Email;
import play.data.validation.Equals;
import play.data.validation.IsTrue;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.db.jpa.Model;
import redis.clients.jedis.Jedis;

/**
 * @author Jean-Baptiste Lemée
 */
@Entity
public class User extends Model {

   @Email
   public String email;

   public String twitter;

   public User(Long id) {
      super();
      this.id = id;
   }

   public User() {
      super();
   }

   /**
    * Recherche par email.
    */
   public static User findByMail(String mail) {
      if (mail == null) {
         return null;
      }
      return User.find("from User z where email=:mail").bind("mail", mail.trim().toLowerCase()).first();
   }

   public static User findByTwitter(String twitter) {
      if (twitter == null) {
         return null;
      }
      return User.find("from User z where twitter=:twitter").bind("twitter", twitter.trim().toLowerCase()).first();
   }

   public static User findByMailOrCreate(String userEmail, Jedis jedis) {
      User user = findByMail(userEmail);
      if (user == null) {
         Logger.info("User creation for email : " + userEmail);
         user = new User();
         user.email = userEmail;
         create(jedis, user);
      }
      return user;
   }

   public static User findByTwitterOrCreate(String twitterName, Jedis jedis) {
      User user = findByTwitter(twitterName);
      if (user == null) {
         Logger.info("User creation for twitter : " + twitterName);
         user = new User();
         user.twitter = twitterName;
         user.email="@"+twitterName;
         create(jedis, user);
      }
      return user;
   }

   public static void create(Jedis jedis, User user) {
      user.save();
      jedis.sadd("users", String.valueOf(user.id));
   }

   @Override
   public String toString() {
      return email;
   }
}
