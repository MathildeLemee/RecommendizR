package controllers;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import models.Liked;
import models.User;
import org.apache.lucene.queryParser.ParseException;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.model.BooleanUserPreferenceArray;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import play.Logger;
import play.db.jpa.JPA;
import play.mvc.Controller;
import redis.clients.jedis.Jedis;
import services.SearchService;

import javax.annotation.Nullable;
import javax.persistence.Query;
import java.io.IOException;
import java.util.*;

import static Utils.Redis.newConnection;
import static org.apache.commons.collections.CollectionUtils.isEmpty;

public class Application extends Controller {

   private static int DEFAULT_START;
   private static int DEFAULT_STOP;

   public static void index() {
      render();
   }

   public static void home() {
      render();
   }

   public static void add() {
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

   public static void recommendFromLiked(int limit, Long likedId) throws TasteException {
      if (likedId == null) {
         renderJSON(Sets.<Object>newHashSet());
      }
      Jedis jedis = newConnection();
      int trainUsersLimit = 100;
      Long userId = 0l; // fake user.
      FastByIDMap<PreferenceArray> usersData = Reco.usersData(jedis, trainUsersLimit, new HashSet<String>());
      BooleanUserPreferenceArray preferenceArray = new BooleanUserPreferenceArray(1);
      preferenceArray.setUserID(0, userId);
      preferenceArray.setItemID(0, likedId);
      usersData.put(userId, preferenceArray);
      List<RecommendedItem> recommendedItems = Reco._internalRecommend(limit, userId, usersData);
      Set<Liked> likedSet = new HashSet<Liked>(recommendedItems.size());
      for (RecommendedItem item : recommendedItems) {
         Liked liked = findLiked(item.getItemID());
         if (liked != null) {
            likedSet.add(liked);
         }
      }
      Liked.fill(likedSet, Security.connectedUser(), jedis);
      renderJSON(likedSet);
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

   public static void recentUserLiked(int start, int stop) {
      User user = Security.connectedUser();
      if (user != null) {
         Jedis jedis = newConnection();
         DEFAULT_START = 0;
         DEFAULT_STOP = 10;
         List<Liked> list = Lists.newArrayList(likedFifoList(user, jedis, "user2:" + user.getId() + ":recents", start, stop));
         Collections.reverse(list);
         renderJSON(list);
      }
   }

   static Collection<Liked> likedList(User user, Jedis jedis, String listName, Integer start, Integer stop) {
      final Map<String, String> relevantLikedMap = jedis.hgetAll(listName);
      if (relevantLikedMap == null || relevantLikedMap.size() == 0) {
         return new ArrayList<Liked>();
      } else {
         List<Liked> likedList = likedFromRelevantIds(relevantLikedMap.keySet());
         removeIgnored(likedList, user, jedis);
         Liked.fill(likedList, user, jedis);
         sortRelevantList(relevantLikedMap, likedList);
         return likedList;
      }
   }

   static Collection<Liked> likedList(User user, Jedis jedis, String listName) {
      final Map<String, String> relevantLikedMap = jedis.hgetAll(listName);
      if (relevantLikedMap == null || relevantLikedMap.size() == 0) {
         return new ArrayList<Liked>();
      } else {
         List<Liked> likedList = likedFromRelevantIds(relevantLikedMap.keySet());
         removeIgnored(likedList, user, jedis);
         Liked.fill(likedList, user, jedis);
         sortRelevantList(relevantLikedMap, likedList);
         return likedList;
      }
   }

   /**
    * Be careful, stop is included : 0-3 will return 4 elements (0 1 2 3).
    *
    * @param user
    * @param jedis
    * @param listName
    * @param start
    * @param stop
    * @return
    */
   static Collection<Liked> likedFifoList(User user, Jedis jedis, String listName, int start, int stop) {
      final List<String> relevantLikedMap = jedis.lrange(listName, start, stop);
      if (relevantLikedMap == null || relevantLikedMap.size() == 0) {
         return new ArrayList<Liked>();
      } else {
         List<Liked> likedList = likedFromRelevantIds(relevantLikedMap);
         removeIgnored(likedList, user, jedis);
         Liked.fill(likedList, user, jedis);
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


   private static List<Liked> likedFromRelevantIds(Collection<String> relevantLikedList) {
      Query query = JPA.em().createQuery("from Liked where id in (:list)");
      Collection<Long> ids = Collections2.transform(relevantLikedList, new Function<String, Long>() {
         public Long apply(@Nullable String s) {
            return Long.valueOf(s);
         }
      });
      query.setParameter("list", ids);
      return query.getResultList();
   }

   static Liked findLiked(long itemID) {
      return Liked.findById(itemID);
   }
}