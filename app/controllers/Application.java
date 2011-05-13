package controllers;

import com.google.common.collect.Lists;
import models.User;
import play.db.jpa.JPA;
import redis.clients.jedis.Jedis;
import services.SearchService;
import com.google.common.collect.Sets;
import models.Liked;
import play.Logger;
import play.mvc.Controller;

import java.io.IOException;
import java.util.*;

import static Utils.Redis.newConnection;
import static org.apache.commons.collections.CollectionUtils.isEmpty;

import org.apache.commons.cli2.util.Comparators;
import org.apache.lucene.queryParser.ParseException;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;

import javax.persistence.Query;

public class Application extends Controller {

   public static void index() {
      render();
   }

   public static void home() {
      render();
   }

   public static void liked(Long id) {
      Liked liked = findLiked(id);
      User user = Security.connectedUser();
      Liked.fill(liked, user, newConnection());
      render(liked);
   }

   public static void search(String text) {
      Set<Liked> likedSet = null;
      try {
         List<Liked> likedList = SearchService.search(text);
         if (isEmpty(likedList)) {
            likedSet = Sets.newHashSet();
         } else {
            likedSet = Sets.newHashSet(likedList);
         }
      } catch (IOException e) {
         Logger.error(e, e.getMessage());
         error(e.getMessage());
      } catch (ParseException e) {
         error(e.getMessage());
      }
      Liked.fill(likedSet, Security.connectedUser(), newConnection());
      renderJSON(likedSet);
   }

   public static void lastAdded(int howMany) {
      User user = Security.connectedUser();
      Jedis jedis = newConnection();
      Collection<Liked> list = likedList(user, jedis, "recents");
      renderJSON(list);
   }

   static <T extends Collection<Liked>> T removeIgnored(T likedCol, User user, Jedis jedis) {
      if (user == null || likedCol == null) {
         return likedCol;
      } else {
         Map<String, String> ignoreList = jedis.hgetAll("ignore:u" + user.id);
         for (Iterator<Liked> iter = likedCol.iterator(); iter.hasNext();) {
            Liked liked = iter.next();
            if (ignoreList.containsKey("like:l" + liked.id)) {
               iter.remove();
            }
         }
         return likedCol;
      }
   }

   public static void mostLiked(int howMany) {
      User user = Security.connectedUser();
      Jedis jedis = newConnection();
      List<Liked> list = Lists.newArrayList(likedList(user, jedis, "popular"));
      Collections.sort(list, Collections.<Liked>reverseOrder());
      renderJSON(list);
   }

   static Collection<Liked> likedList(User user, Jedis jedis, String listName) {
      final Map<String, String> relevantLikedMap = jedis.hgetAll(listName);
      if (relevantLikedMap == null || relevantLikedMap.size() == 0) {
         return new ArrayList<Liked>();
      } else {
         List<Liked> likedList = likedFromRelevantMapIds(relevantLikedMap);
         removeIgnored(likedList, user, jedis);
         Liked.fill(likedList, user, jedis);
         sortRelevantList(relevantLikedMap, likedList);
         return likedList;
      }
   }

   private static void sortRelevantList(final Map<String, String> relevantLikedMap, List<Liked> likedList) {
      Collections.sort(likedList, new Comparator<Liked>() {
         public int compare(Liked l1, Liked l2) {
            Long scoreL1 = Long.valueOf(relevantLikedMap.get(String.valueOf(l1.id)));
            Long scoreL2 = Long.valueOf(relevantLikedMap.get(String.valueOf(l2.id)));
            return scoreL1 == scoreL2 ? 0 : scoreL1 > scoreL2 ? -1 : 1;
         }
      });
   }

   private static List<Liked> likedFromRelevantMapIds(Map<String, String> relevantLikedMap) {
      Query query = JPA.em().createQuery("from Liked where id in (:list)");
      Set<Long> ids = new HashSet<Long>();
      for (String s : relevantLikedMap.keySet()) {
         ids.add(Long.valueOf(s));
      }
      query.setParameter("list", ids);
      return query.getResultList();
   }

   static Liked findLiked(long itemID) {
      return Liked.findById(itemID);
   }
}