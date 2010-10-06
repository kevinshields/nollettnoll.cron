package nollettnoll.time.cron.schedule;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nollettnoll.generic.log.Logger;
import nollettnoll.generic.log.impl.LoggerImpl;
import nollettnoll.time.cron.CronExecutable;

/**
 * Responsible for running crontabs. Either you call main with an array of file names matching crontab
 * files, or null or an empty array for initializing a crontab file found at CronSchedule.DEFAULT_CRONTAB 
 * <br/><p/>
 * Study the Internet on the excellent UNIX crontab format if you are unfamiliar with this ingenious syntax.
 *
 * @author ola@nollettnoll.net 
 * @see nollettnoll.time.cron.schedule.CronSchedule#DEFAULT_CRONTAB on the filepath
 * @see nollettnoll.time.cron.schedule.CronSchedule#scheduleJob(String, CronExecutable) on the format of your file 
 */
public class CronTabRunner {

   // We are singleton
   private static CronTabRunner runner;

   // holding the schedule 
   private CronSchedule schedule;

   // Holding the knows sets of crontab files;
   // they will be re-scanned every second
   private List<File> cronTabs;

   // The updater
   private static CronTabUpdateChecker updater;

   // The logger
   private static LoggerImpl logger;

   // Singleton   
   private CronTabRunner() {
      this.schedule = new CronSchedule();
      this.cronTabs = new ArrayList<File>();
   }

   private static CronTabRunner getInstance() {
      if (runner == null) {
         runner = new CronTabRunner();
         runner.schedule.initLogger();
         logger = new LoggerImpl(CronSchedule.class.getName());
         logger.setIOUsage(Logger.IO_STD_OUT);
         logger.setLogLevel(Logger.INFO);
         runner.schedule.setLogLevel(Logger.INFO);
         updater = new CronTabUpdateChecker();
      }
      return runner;
   }

   /**
    * Method for scheduling jobs via your default cron tab
    * 
    * @param args not sent or an array of Strings matching crontab file paths
    * @see nollettnoll.time.cron.schedule.CronSchedule#scheduleJob(String schedule, CronExecutable executable)
    */
   public static final void main(String[] args) {

      // fetch instance
      runner = getInstance();

      // if we got args, assume them to be file
      // paths to crontabs
      if (args != null && args.length > 0) {
         for (String cronTabs : args) {
            try {
               final File cronTabFile = new File(cronTabs);
               runner.schedule.scheduleJob(cronTabFile);
               runner.cronTabs.add(cronTabFile);
            } catch (FileNotFoundException e) {
               e.printStackTrace();
            } catch (CronExecutableNotExecutableException e) {
               e.printStackTrace();
            }
         }
      } else {
         try {
            runner.schedule.scheduleJob(CronSchedule.DEFAULT_CRONTAB);
            runner.cronTabs.add(CronSchedule.DEFAULT_CRONTAB);
         } catch (FileNotFoundException e1) {
            e1.printStackTrace();
         } catch (CronExecutableNotExecutableException e) {
            e.printStackTrace();
         }
      }

      // start up the scheduler
      runner.schedule.start();

      // trigger the update-thread
      updater.start();

      // Adding a shutdown hook
      MyShutdown sh = new MyShutdown();
      Runtime.getRuntime().addShutdownHook(sh);
   }

   public void stop() {
      if (runner != null && runner.schedule != null) {
         runner.schedule.stop();
      }
      runner.schedule = null;
      runner = null;

      updater.stopThread();
   }

   // Simple shutdown hook class
   private final static class MyShutdown extends Thread {

      public void run() {
         getInstance().stop();
      }
   }

   /**
    * Checks the status 
    * 
    * @author ola@nollettnoll.net
    */
   private final static class CronTabUpdateChecker extends Thread {

      // File-last modified hash
      private Map<File, Long> tabs;

      // Sleep in between crontab checks
      private final static int SLEEP_INTERVAL_IN_BETWEEN_CHECKING_CRON_FILES = 2000;

      // Simple constructor
      private CronTabUpdateChecker() {
         this.tabs = new HashMap<File, Long>();
      }

      public void run() {

         for (File file : runner.cronTabs) {
            if (!tabs.containsKey(file)) {
               this.tabs.put(file, file.lastModified());
            }
         }

         while (runner != null && runner.schedule != null) {
            final Set<File> files = tabs.keySet();
            for (File f : files) {
               final long last = f.lastModified();
               if (last != tabs.get(f)) { // modified!
                  try {
                     logger.logInfo("Crontab " + f.getAbsolutePath() + " has changed!");
                     //runner.schedule.removeJob(f);
                     runner.schedule.removeAllJobs();
                     runner.schedule.scheduleJob(f);
                     tabs.put(f, last);
                  } catch (FileNotFoundException e) {
                     e.printStackTrace();
                  } catch (CronExecutableNotExecutableException e) {
                     e.printStackTrace();
                  }
               }
            }

            try {
               Thread.sleep(SLEEP_INTERVAL_IN_BETWEEN_CHECKING_CRON_FILES);
            } catch (InterruptedException e) {
               e.printStackTrace();
            }

         }

      }

      private void stopThread() {
         tabs.clear();
         tabs = null;
      }
   }
}
