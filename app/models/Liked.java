package models;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
public class Liked extends Model implements Comparable<Liked> {

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

   public int compareTo(Liked o) {
      if (liked == null) return 1;
      else {
         return ObjectUtils.<Long>defaultIfNull(this.like, 0l).compareTo(ObjectUtils.<Long>defaultIfNull(o.like, 0l));
      }
   }

   public void transformPlainUrlToHtml() {
      description = description.replaceAll(">", "&gt;");
      description = description.replaceAll("<", "&lt;");
      String str = "(?i)\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:\'\".,<>?«»“”‘’]))";
      Pattern patt = Pattern.compile(str);
      Matcher matcher = patt.matcher(description);
      description = matcher.replaceAll("<a href=\"$1\">$1</a>");
   }
}
