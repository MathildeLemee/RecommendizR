package Utils;

/**
 * @author Jean-Baptiste lemée
 */
public class ObjectUtils {

   public static <T> T defaultIfNull(T obj, T defaut) {
      if (obj == null) return defaut;
      else return obj;
   }
}
