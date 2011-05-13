package models;

import java.util.Collection;

import javax.persistence.Entity;
import javax.persistence.Transient;

import Utils.ObjectUtils;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.db.jpa.Model;
import redis.clients.jedis.Jedis;

/**
 * @author Jean-Baptiste Lemée
 */
@Entity
public class Liked extends Model {

   @Required
   @MinSize(3)
   @MaxSize(100)
   public String name;

   @MaxSize(255)
   @Required
   public String description;
   @Transient
   public Boolean liked;
   @Transient
   public Boolean ignored;

   @Transient
   public Long like;

   @Transient
   public Long ignore;

   public String toString() {
      return name;
   }

   public static boolean isLiked(Long likedId, User user, Jedis jedis) {

      boolean r = null != jedis.hget("u" + user.id, "like:l" + likedId);
      return r;
   }

   public static Collection<Liked> fill(Collection<Liked> likedList, User user, Jedis jedis) {
      if (user != null) {
         for (Liked item : likedList) {
            fill(item, user, jedis);
         }
      }
      return likedList;
   }

   public static Liked fill(Liked liked, User user, Jedis jedis) {
      if (user != null) {
         liked.liked = isLiked(liked.getId(), user, jedis);
         liked.ignored = isIgnored(liked.getId(), user, jedis);
      }
      liked.like = Long.valueOf(ObjectUtils.<String>defaultIfNull(jedis.hget("l" + liked.id, "count"), "0"));
      liked.ignore = Long.valueOf(ObjectUtils.<String>defaultIfNull(jedis.hget("l" + liked.id, "countIgnore"), "0"));
      return liked;
   }

   public static boolean isIgnored(Long likedId, User user, Jedis jedis) {
      boolean r = null != jedis.hget("ignore:u" + user.id, "like:l" + likedId);
      return r;
   }
}
