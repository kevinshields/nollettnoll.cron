package nollettnoll.time.cron;

/**
 * Interface to implement for a cron executable
 * 
 * @author ola@nollettnoll.net
 *
 */
public interface CronExecutable {

   /**
    * Shall be called upon scheduled execution time/(s)
    */
   void executeOnToc();

   /**
    * Returning the title of this cronjob
    */
   String cronTitle();

}
