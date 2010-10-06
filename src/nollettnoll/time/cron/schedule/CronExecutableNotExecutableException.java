package nollettnoll.time.cron.schedule;

/**
 * 
 * Exception thrown if given path was not executable
 * 
 * @author ola@nollettnoll.net
 *
 */
public final class CronExecutableNotExecutableException extends Exception {

   protected CronExecutableNotExecutableException(String path) {
      super("Executable " + path + " is NOT executable!");
   }
}
