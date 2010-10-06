package nollettnoll.time.cron.executables;

/**
 * Just holds some nifty string funtions
 * 
 * @author ola
 *
 */
class StringUtils {

   static String oneSpace(String input) {
      input = input.trim();
      return input.replaceAll("( )+", " ");
   }

   static String escape(String input) {
      final int len = input.length();
      int index = 0;
      StringBuilder ret = new StringBuilder();
      while (index < len) {
         if (input.charAt(index) == '\\') {
            ret.append("\\\\");
         } else {
            ret.append(input.charAt(index));
         }
         index++;
      }
      return ret.toString();
   }
}
