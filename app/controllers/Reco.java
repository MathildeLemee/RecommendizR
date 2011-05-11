package controllers;

import services.SearchService;
import models.Category;
import models.Liked;
import models.User;

import org.apache.commons.lang.StringUtils;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.model.BooleanUserPreferenceArray;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;

import play.Logger;
import play.mvc.Controller;
import play.mvc.With;
import redis.clients.jedis.Jedis;
import services.CrossingBooleanRecommenderBuilder;
import services.CrossingDataModelBuilder;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static Utils.Redis.newConnection;

@With(Secure.class)
public class Reco extends Controller {

   public static void like(Long likedId) {
      User user = Security.connectedUser();
      Jedis jedis = newConnection();
      doLike(likedId, user, jedis);
   }

    public static void ignore(Long likedId) {
      User user = Security.connectedUser();
      Jedis jedis = newConnection();
      doIgnore(likedId, user, jedis);
   }

   private static void doLike(Long likedId, User user, Jedis jedis) {
      String count = String.valueOf(jedis.hincrBy("l" + likedId, "count", 1));
      managePopularList(likedId, count, jedis);
      jedis.hset("u" + user.id, "like:l" + likedId, String.valueOf(likedId));
      jedis.hset("l" + likedId, "user:u" + user.id, String.valueOf(user.id));
   }

    private static void doIgnore(Long likedId, User user, Jedis jedis) {
      jedis.hincrBy("l" + likedId, "countIgnore", 1);
      jedis.hset("ignore:u" + user.id, "like:l" + likedId, String.valueOf(likedId));
    }

   private static void managePopularList(Long likedId, String count, Jedis jedis) {
      Map<String, String> mostPopulars = jedis.hgetAll("popular");
      if (mostPopulars == null || mostPopulars.size() < 10) {
         jedis.hset("popular", String.valueOf(likedId), count);
      } else {
         Map.Entry<String, String> smaller = getLessLiked(mostPopulars, jedis);
         if (getLesserXThanY(smaller.getValue(), count)) {
            jedis.hdel("popular", smaller.getKey());
            jedis.hset("popular", String.valueOf(likedId), count);
         }
      }
   }

   private static Map.Entry<String, String> getLessLiked(Map<String, String> mostPopulars, Jedis jedis) {
      Map.Entry<String, String> lessLiked = null;
      for (Map.Entry<String, String> entry : mostPopulars.entrySet()) {
         if (lessLiked == null || getLesserXThanY(entry.getValue(), lessLiked.getValue())) {
            lessLiked = entry;
         }
      }
      return lessLiked;
   }

   private static boolean getLesserXThanY(String x, String y) {
      if (Long.valueOf(x) < Long.valueOf(y)) {
         return Boolean.TRUE;
      }
      return Boolean.FALSE;
   }

   public static void unlike(Long likedId) {
      User user = Security.connectedUser();
      Jedis jedis = newConnection();
      jedis.hincrBy("l" + likedId, "count", -1);
      jedis.hdel("u" + user.id, "like:l" + likedId);
      jedis.hdel("l" + likedId, "user:u" + user.id);
   }

   public static boolean switchlike(Long likedId) {
      if (isLiked(likedId)) {
         unlike(likedId);
         return false;
      } else {
         like(likedId);
         return true;
      }
   }

   public static void addLiked(Liked liked) {
      if (StringUtils.isEmpty(liked.name) || StringUtils.isEmpty(liked.description)) {
         badRequest();
      }
      liked.save();
      User user = Security.connectedUser();
      Jedis jedis = newConnection();
      doLike(liked.id, user, jedis);
      try {
         SearchService.addToIndex(liked);
      } catch (IOException e) {
         Logger.error(e, e.getMessage());
      }
      renderJSON(liked);
   }

   public static boolean isLiked(Long likedId) {
      User user = Security.connectedUser();
      return Liked.isLiked(likedId, user, newConnection());
   }

   public static void recommend(int limit) throws TasteException {
      User user = Security.connectedUser();
      Jedis jedis = newConnection();
      int trainUsersLimit = 100;
      Map<String, String> ignoreList = jedis.hgetAll("ignore:u" + user.id);
      FastByIDMap<PreferenceArray> usersData = usersData(jedis, null, trainUsersLimit, ignoreList.keySet());
      usersData.put(user.id, getPreferences(jedis, trainUsersLimit++, user.id, new HashSet<String>()));
      List<RecommendedItem> recommendedItems = _internalRecommend(limit, user, usersData);
      Set<Liked> likedSet = new HashSet<Liked>(recommendedItems.size());
      for (RecommendedItem item : recommendedItems) {
         Liked liked = findLiked(item.getItemID());
         if (liked != null) {
            likedSet.add(liked);
         }
      }
      Liked.fill(likedSet, user, jedis);
      renderJSON(likedSet);
   }

   public static List<RecommendedItem> _internalRecommend(int howMany, User user, FastByIDMap<PreferenceArray> usersData) throws TasteException {
      RecommenderBuilder recommenderBuilder = new CrossingBooleanRecommenderBuilder();
      DataModel trainingModel = new CrossingDataModelBuilder().buildDataModel(usersData);
      Recommender recommender = recommenderBuilder.buildRecommender(trainingModel);
      return recommender.recommend(user.id, howMany, null);
   }

   private static Liked findLiked(long itemID) {
      return Liked.findById(itemID);
   }

   private static FastByIDMap<PreferenceArray> usersData(Jedis jedis, Category category, int limit, Set<String> ignoredKeys) {
      FastByIDMap<PreferenceArray> result = new FastByIDMap<PreferenceArray>();
      Set<String> usersIds = jedis.smembers("users");
      int numUser = 0;
      for (String userId : usersIds) {
         if (numUser > limit) {
            break;
         }
         BooleanUserPreferenceArray preferenceArray = getPreferences(jedis, numUser, Long.valueOf(userId), ignoredKeys);
         result.put(Long.valueOf(userId), preferenceArray);
         numUser++;
      }

      return result;
   }

   private static BooleanUserPreferenceArray getPreferences(Jedis jedis, int numUser, Long userId,  Set<String> ignoredKeys) {
      Map<String, String> likedIds = jedis.hgetAll("u" + userId);
      BooleanUserPreferenceArray preferenceArray = new BooleanUserPreferenceArray(likedIds.size());
      preferenceArray.setUserID(numUser, userId);
      int numLiked = 0;
      for (Map.Entry<String, String> likedEntry : likedIds.entrySet()) {
         if(!ignoredKeys.contains("like:l"+likedEntry.getValue())){
            preferenceArray.setItemID(numLiked++, Long.valueOf(likedEntry.getValue()));
         }
      }
      return preferenceArray;
   }


   protected static boolean userAlreadyExists(User user) {
      return (null != Security.findUser(user.email));
   }

}