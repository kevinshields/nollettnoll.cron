package nollettnoll.time.cron.schedule;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import nollettnoll.generic.log.LogListener;
import nollettnoll.generic.log.Logger;
import nollettnoll.generic.log.impl.LoggerImpl;
import nollettnoll.time.cron.CronExecutable;
import nollettnoll.time.cron.executables.SystemExecutable;

/**
 * Utility for scheduling Cron jobs.
 * 
 * @author ola@nollettnoll.net
 */
public class CronSchedule implements LogListener {

   /**
    * Matching your system equivalent of $HOME/.cron/crontab
    */
   public static final File DEFAULT_CRONTAB = new File(new StringBuffer().append(System.getProperty("user.home"))
         .append(File.separator).append(".cron").append(File.separator).append("crontab").toString());

   // The cronrunner - responsible for executing on time(/s)
   private CronRunner runner;

   // The UNIX cron format indeces
   private static final int INDEX_MINUTES = 0;
   private static final int INDEX_HOURS = 1;
   private static final int DAYS_OF_MONTH = 2;
   private static final int INDEX_MONTHS = 3;
   private static final int INDEX_DAYS_IN_WEEK = 4;

   /**
    * In accordance with the cron format "*", signifying EVERY instance of given time unit 
    */
   public static final String EVERY = "*";

   /**
    * In accordance with the cron format, "," separator within every cron time unit interval
    */
   public static final String SEPARATOR_GLUE = ",";

   /**
    * In accordance with the cron format, " " is the separator between different cron time unit intervals
    */
   public static final String SEPARATOR = " ";

   private static String SEPARATOR_TO_EXEC = "@-:.@";

   // Stipulated number for signifying every instance
   private static final int EVERY_NUMBER = 7311;

   // My very own logger
   private Logger logger;

   // Jobs connected to specific file
   private Map<File, List<CronJob>> theJobs;

   /**
   * 
   * Method for scheduling execution on the classical UNIX cron format. The syntax for the scheduling is<br/>
   * 
   * <pre>
   * {@code
   *[   m     h     dm  m    dw <path and args to system executable> ]
   *     -     -     -   -    -
   *     |     |     |   |    |
   *     |     |     |   |    +----- day of week (0 - 6) (Sunday=0)
   *     |     |     |   +------- month (1 - 12)
   *     |     |     +--------- day of        month (1 - 31)
   *     |     +----------- hour (0 - 23)
   *     +------------- min (0 - 59)
   * }
   * </pre>
   * 
   * Example :
   *
   * <pre>
   * {@code
   * CronSchedule sched = new CronSchedule();
   * sched.scheduleJob("0,10,20,30,40,50 * * 9 0", new TestExecutable());
   * }
   * </pre> 
   * 
   * meaning
   * <br/><p/>
   * 
   * executable TestExecutable (implementing the CronExecutable interface) 
   * will execute every 10th minute on Sundays in September
   * 
   * @param schedule the schedule expressed on above format
   * @param executable the executable
   * @see nollettnoll.time.cron.CronExecutable
   */
   public void scheduleJob(String schedule, CronExecutable executable) {
      final CronTimeUnit scheme = parseSchemeStringToCronTimeUnit(schedule);
      if (scheme != null) {
         if (logger == null) {
            initLogger();
         }
         scheduleJob(scheme, executable);
      }
   }

   /**
    * Method to schedules a job running given executable according to sent cron scheme
    * 
    * @param scheme the cron scheme to use
    * @param executable the executable
    * @see nollettnoll.time.cron.CronExecutable    
    */
   public void scheduleJob(CronTimeUnit scheme, CronExecutable executable) {

      runner = CronRunner.getInstance();

      if (logger == null) {
         initLogger();
      }

      if (logger != null) {
         runner.addJob(executable, scheme, logger);
      } else {
         runner.addJob(executable, scheme, null);
      }
   }

   /**
    * Method to schedule jobs from a crontab file. The file should
    * be a readable file containing lines on the above described format.
    *
    * @param scheduleFile your crontab file
    * @throws FileNotFoundException
    * @throws CronExecutableNotExecutableException
    * @see #scheduleJob(String, CronExecutable) on the format of your file
    */
   public void scheduleJob(File scheduleFile) throws FileNotFoundException, CronExecutableNotExecutableException {

      final Map<String, String> potentialJobs = getJobsFromFile(scheduleFile);
      if (potentialJobs != null) {

         final List<CronJob> jobs = new ArrayList<CronJob>();
         final Set<String> execTimes = potentialJobs.keySet();
         for (String execStr : execTimes) {

            execStr = StringUtils.oneSpace(execStr);
            final String schemat = execStr.split(SEPARATOR_TO_EXEC)[0];
            final String execPath = potentialJobs.get(execStr);

            // Test if the executable exists and is executable
            final String[] cmdAndArgs = execPath.split(" ");
            File toExec = new File(cmdAndArgs[0]);
            if (!toExec.canExecute()) {
               throw new CronExecutableNotExecutableException(execPath);
            }

            if (logger == null) {
               initLogger();
            }

            SystemExecutable exec = null;
            if (logger == null) {
               exec = new SystemExecutable(cmdAndArgs, null);
            } else {
               exec = new SystemExecutable(cmdAndArgs, logger);
            }

            CronTimeUnit scheme = null;
            try {
               scheme = parseSchemeStringToCronTimeUnit(schemat);
            } catch (IllegalArgumentException e) {
               if (logger != null) {
                  logger.logErr(e.getMessage() + " - skipping job, bad format : " + schemat);
               }
            }
            if (scheme != null && exec != null) {
               scheduleJob(scheme, exec);
               jobs.add(new CronJob(scheme, exec));
            }
         }

         if (theJobs == null) {
            theJobs = new HashMap<File, List<CronJob>>();
         }
         if (jobs.size() > 0) {
            theJobs.put(scheduleFile, jobs);
         }
      }
   }

   /**
    * Method to startup the cron execution
    */
   public void start() {
      runner.start();
   }

   /**
    * Method to stop the cron execution
    */
   public void stop() {
      runner.stopThread();
   }

   /**
    * Method to activate the default logger
    * <br/><p/>
    * <ul>
    * <li>log to file only ($USERHOME/.nollettnoll/CronSchedule.class.getName())</li>
    * <li>log level will be info</i>
    * <li>cycle the log file after 10000 entries</i>
    * </ul> 
    * <br/>
    * 
    * @see nollettnoll.generic.log.LogListener    
    * 
    */
   public void initLogger() {
      logger = new LoggerImpl(CronSchedule.class.getName());
      setLoggerDefaults();
   }

   /**
    * Method to activate logging given a known master logger (for
    * instance in order to collect your loggings in the same file as some other
    * class, in this sense acting a "log master") - what you send in will be the 
    * file name for this nollettnoll logfile. Default it will
    * <br/><p/>
    * <ul>
    * <li>log only to file ($USERHOME/.nollettnoll/[sent masterLogger name]</li>
    * <li>log level is info</i>
    * <li>cycle the log file after 10000 entries</i>
    * </ul> 
    * <p/><br/>
    * 
    * @param givenmasterLogger givenmasterLogger the master logger
    * @see nollettnoll.generic.log.LogListener
    *  
    */
   public void initLogger(String givenmasterLogger) {
      logger = new LoggerImpl(givenmasterLogger);
      setLoggerDefaults();
   }

   /**
    * Method to activate logging sending stdout to a file
    * designated by your absolute path. Default it will
    * <br/><p/>
    * <ul>
    * <li>log only to file designated by given path</li>
    * <li>log level is info</i>
    * <li>cycle the log file after 10000 entries</i>
    * </ul> 
    * <p/><br/>
    * 
    * @param fName givenmasterLogger the master logger
    * @return true on success, false if we could not init the false
    * @see nollettnoll.generic.log.LogListener
    *  
    */
   public boolean initLoggerAbsolutePath(String fName) {
      logger = new LoggerImpl();
      try {
         logger.setLogFileAbsolutePath(fName);
      } catch (IllegalArgumentException e) {
         e.printStackTrace(); // best we can do..
         return false;
      }
      setLoggerDefaults();
      return true;
   }

   /**
    * Method to remove a cron job crontab file (internal only!)
    * 
    * @param scheduleFile the cron file
    */
   protected void removeJob(File scheduleFile) {
      if (theJobs.containsKey(scheduleFile)) {
         final List<CronJob> jobs = theJobs.get(scheduleFile);
         for (CronJob j : jobs) {
            runner.removeJob(j.executable, j.scheme);
         }
         theJobs.remove(scheduleFile);
      }
   }

   /**
    * Method to remove a job exactly matched by sent args
    * 
    * @param executable the executable
    * @param scheme the scheme
    */
   public void removeJob(CronExecutable executable, CronTimeUnit scheme) {
      runner.removeJob(executable, scheme);
   }

   /**
    * Method for removing all present jobs
    */
   public void removeAllJobs() {
      runner.removeAllJobs();
   }

   /**
    * Helper fetching jobs from a cronfile
    * 
    * @param cronFile
    * @return
    * @throws FileNotFoundException
    */
   private static Map<String, String> getJobsFromFile(File cronFile) throws FileNotFoundException {
      final Map<String, String> foundJobs = new HashMap<String, String>();
      Scanner scanner = new Scanner(cronFile, "UTF-8");
      try {
         while (scanner.hasNextLine()) {

            final String line = scanner.nextLine();
            int index = 0;

            if (line.length() > 11) {

               if (isCronScheduleChar(line, index)) {
                  StringBuilder theSchemePart = new StringBuilder();
                  theSchemePart.append(line.charAt(index));
                  index++;
                  boolean doneParsing = false;
                  final int len = line.length();
                  while (index < len && !doneParsing) {
                     if (isCronScheduleChar(line, index)) {
                        theSchemePart.append(line.charAt(index));
                     } else {
                        doneParsing = true;
                     }
                     index++;
                  }

                  String execPath = line.substring(index - 1, line.length());
                  execPath = StringUtils.oneSpace(execPath);
                  execPath = StringUtils.escape(execPath);
                  foundJobs.put(theSchemePart.toString() + SEPARATOR_TO_EXEC + execPath, execPath);
               }
            }
         }
      } finally {
         scanner.close();
      }
      if (foundJobs.size() > 0) {
         return foundJobs;
      } else {
         return null;
      }
   }

   // Helper to test if a character at given position in sent string is a valid crontab 
   // constituent
   private static boolean isCronScheduleChar(String line, int index) {

      final char theChar = line.charAt(index);

      if (index > 0) {
         if (theChar == ' ') {
            return true;
         }
      }

      if (theChar == '*') {
         return true;
      } else if (theChar == ',') {
         return true;
      } else if (Character.isDigit(theChar)) {
         final int num = Character.getNumericValue(theChar);
         if (num > -1 && num < 61) {
            return true;
         }
      }
      return false;
   }

   // Tries to parse sent string
   private CronTimeUnit parseSchemeStringToCronTimeUnit(String schedule) {

      // Parsing args
      List<Integer> minutes = null;
      List<Integer> hours = null;
      List<Integer> daysOfMonth = null;
      List<Integer> months = null;
      List<Integer> daysInWeek = null;

      // make it onespace
      schedule = StringUtils.oneSpace(schedule);

      final String[] args = schedule.split(SEPARATOR);
      if (args.length != 5) {
         throw new IllegalArgumentException("Cron syntax error : wrong number of arguments");
      } else {
         for (int a = 0; a < args.length; a++) {
            final String[] apa = args[a].trim().split(SEPARATOR_GLUE);
            final ArrayList<Integer> numbers = new ArrayList<Integer>();
            for (String b : apa) {
               try {
                  if (!b.equals("*")) {
                     numbers.add(new Integer(Integer.parseInt(b)));
                  } else {
                     numbers.add(new Integer(EVERY_NUMBER));
                     break; // * means only one arg
                  }
               } catch (NumberFormatException e) {
                  throw new IllegalArgumentException("Cron syntax error : NumberFormatException !");
               }
            }

            switch (a) {
               case INDEX_MINUTES:
                  minutes = numbers;
                  break;
               case INDEX_HOURS:
                  hours = numbers;
                  break;
               case DAYS_OF_MONTH:
                  daysOfMonth = numbers;
                  break;
               case INDEX_MONTHS:
                  months = numbers;
                  break;
               case INDEX_DAYS_IN_WEEK:
                  daysInWeek = numbers;
                  break;
            }
         }
      }
      return new CronTimeUnit(daysInWeek, months, daysOfMonth, hours, minutes);
   }

   // Default logger setup
   private void setLoggerDefaults() {
      if (logger != null) {
         logger.setLogLevel(Logger.INFO);
         // cycle after
         logger.setMaxNumberOfEntries(10000);
         // default, will just log to file
         logger.setIOUsage(Logger.IO_STD_OUT);
      }
   }

   /**
    * Method to set the IO level. Accepted levels are described in the Logger interface.
    * <br/>
    * But if you don't know what that's about, let's say it's
    * <br/><p/>
    * <ul>
    * <li>IO_OFF = 0      : no io</li>
    * <li>IO_FILE = 1     : log to file</i>
    * <li>IO_BOTH_OUT = 2 : log to file and stdout</i>    
    * </ul> 
    * <br/>  
    * @param IOLevel the io level
    * @see nollettnoll.generic.log.Logger 
    */
   @Override
   public void setIOLevel(int IOLevel) {
      if (logger != null) {
         logger.setIOUsage(IOLevel);
      }
   }

   /**
    * Method to set the log level. Accepted levels are described in the Logger interface.
    * <br/>
    * But if you don't know what that's about, let's say it's
    * <br/><p/>
    * <ul>
    * <li>ERR = 3</li>
    * <li>WARN = 4</i>
    * <li>INFO = 5</i>
    * <li>DEBUG = 6</i> 
    * </ul> 
    * <br/>     
    * @param level the logging level
    * @see nollettnoll.generic.log.Logger 
    */
   @Override
   public void setLogLevel(int level) {
      if (logger != null) {
         logger.setLogLevel(level);
      }
   }

   // --------- Inner private cron runner ---------

   /**
    * Responsible for the runner thread, executing held
    * executables according to held schemes.
    */
   private static class CronRunner extends Thread {

      // The current time; shall be calibrated once every minute
      private Calendar cal;

      // Running or not state
      private boolean running;

      // Singleton
      private static CronRunner runner = null;

      // The logger
      private static Logger logger = null;

      private CronRunner() {
      }; // Singleton

      // The executables held in-loop
      private Map<CronExecutable, CronTimeUnit> executables;

      // Added executables not yet in-loop
      private Map<CronExecutable, CronTimeUnit> executables_to_come;

      // Added executables to be removed
      private Map<CronExecutable, CronTimeUnit> executables_to_remove;

      // Are the new jobs grabbed yet
      private boolean grabbedNewJobs;

      // Is this runner initiated or not
      private boolean initiated = false;

      // Are jobs added or not
      private boolean addingJob = false;

      // Are jobs removed or not
      private boolean removingJob = false;

      // Are the removed jobs removed yet      
      private boolean grabbedLostNewJobs;

      // singleton instance provider
      private static CronRunner getInstance() {
         if (runner == null) {
            runner = new CronRunner();
         }
         return runner;
      }

      // the init
      private void init() {
         if (!runner.initiated) {
            running = false;
            executables = new HashMap<CronExecutable, CronTimeUnit>();
            executables_to_come = new HashMap<CronExecutable, CronTimeUnit>();
            executables_to_remove = new HashMap<CronExecutable, CronTimeUnit>();
            grabbedNewJobs = false;
            runner.initiated = true;
         }
      }

      // Method to add a job at any time
      private void addJob(CronExecutable exec, CronTimeUnit timeTable, Logger logger) {
         init();
         addingJob = true;
         if (logger != null) {
            this.logger = logger;
         }
         if (executables.size() > 0) {
            executables_to_come.put(exec, timeTable);
            grabbedNewJobs = false;
         } else {
            executables.put(exec, timeTable);
         }
         addingJob = false;
      }

      // Helper method to remove a job at any time
      private void removeJob(CronExecutable exec, CronTimeUnit timeTable) {
         init();
         removingJob = true;
         if (executables.size() > 0) {
            if (logger != null) {
               logger.logInfo("Scheduling to remove job : " + exec.cronTitle());
            }
            executables_to_remove.put(exec, timeTable);
            grabbedLostNewJobs = false;
         }
         removingJob = false;
      }

      // Helper method to remove all jobs
      private void removeAllJobs() {
         init();
         removingJob = true;
         if (executables.size() > 0) {
            if (logger != null) {
               logger.logInfo("Scheduling to remove ALL jobs..");
            }
            executables_to_remove.putAll(executables);
            grabbedLostNewJobs = false;
         }
         removingJob = false;
         executables_to_come = new HashMap<CronExecutable, CronTimeUnit>();
      }

      // Calibrate and sleep
      private void calibrateTimeAndSleep() {
         final long calSleepTime = (60 - cal.get(Calendar.SECOND)) * 1000;
         if (logger != null) {
            logger.logInfo("Calibrated sleep is : " + calSleepTime);
         }
         cal.add(Calendar.MILLISECOND, (int) calSleepTime);
         try {
            Thread.sleep(calSleepTime);
         } catch (InterruptedException e) {
            e.printStackTrace();
         }
      }

      @Override
      public void run() {
         if (!running) {
            running = true;
            this.cal = Calendar.getInstance();

            // First calibrate time
            calibrateTimeAndSleep();

            boolean doneIt = false;
            int countDown = 60;
            while (running) {
               //dumpTime();
               countDown--;
               if (cal.get(Calendar.SECOND) == 0) {
                  doneIt = false;
               }

               // Removing jobs..
               if (executables_to_remove.size() > 0 && !grabbedLostNewJobs) {
                  final Set<CronExecutable> toRemove = executables_to_remove.keySet();
                  for (CronExecutable c : toRemove) {
                     if (executables.containsKey(c)) {

                        // already present jobs
                        if (executables.get(c).equals(executables_to_remove.get(c))) {
                           if (logger != null) {
                              logger.logInfo("Removing job from actual jobs : " + c.cronTitle());
                           }
                           executables.remove(c);
                        }

                        // scheduled not yet run jobs
                        if (executables_to_come != null && executables_to_come.get(c) != null) {
                           if (executables_to_come.get(c).equals(executables_to_remove.get(c))) {
                              if (logger != null) {
                                 logger.logInfo("Removing job from potential jobs : " + c.cronTitle());
                              }
                              executables_to_come.remove(c);
                           }
                        }

                     }
                  }
                  if (!removingJob) {
                     executables_to_remove.clear();
                  }
               }

               // Add any added job..
               if (executables_to_come.size() > 0 && !grabbedNewJobs) {
                  if (logger != null) {
                     logger.logInfo("Adding jobs..");
                  }
                  executables.putAll(executables_to_come);
                  if (!addingJob) {
                     executables_to_come.clear();
                  }
               }

               if (!doneIt) {

                  if (logger != null) {
                     dumpTime();
                  }

                  Set<CronExecutable> execs = executables.keySet();
                  for (CronExecutable exec : execs) {
                     if (isStillMatched(executables.get(exec), exec.cronTitle(), this.cal)) {
                        exec.executeOnToc();
                     }
                  }

                  doneIt = true; // done it or not, we're done for this minute

                  // make sure the time is right
                  if (logger != null) {
                     logger.logDebug("Calibrating seconds..");
                  }
                  this.cal = Calendar.getInstance();
                  countDown = 60 - cal.get(Calendar.SECOND);

               } else {
                  if (logger != null) {
                     logger.logDebug("Already executed - secs to next check : " + countDown);
                  }
               }

               // sleep and roll another second..
               try {
                  Thread.sleep(1000);
               } catch (InterruptedException e) {
                  e.printStackTrace();
               }
               cal.add(Calendar.SECOND, 1);
            }
         }
      }

      public void stopThread() {
         running = false;
         try {
            Thread.sleep(1000);
         } catch (InterruptedException e) {
            e.printStackTrace();
         }
         cal = null;
      }

      // Determining if we should execute or not for all cron time units
      // in the most effective manner (right to left in the cron scheme)
      private boolean isStillMatched(CronTimeUnit cronTimeUnit, String cronTitle, Calendar cal) {

         boolean stillMatchedTime = false;
         stillMatchedTime = isStillMatchedInInterval("DAYS_OF_WEEK", cal, cronTimeUnit.getDaysInWeek(),
               Calendar.DAY_OF_WEEK, cronTitle);
         if (stillMatchedTime) {
            stillMatchedTime = isStillMatchedInInterval("MONTHS", cal, cronTimeUnit.getMonths(), Calendar.MONTH,
                  cronTitle);
         }
         if (stillMatchedTime) {
            stillMatchedTime = isStillMatchedInInterval("DAYS_OF_MONTH", cal, cronTimeUnit.getDaysInMonth(),
                  Calendar.DAY_OF_MONTH, cronTitle);
         }
         if (stillMatchedTime) {
            stillMatchedTime = isStillMatchedInInterval("HOURS", cal, cronTimeUnit.getHours(), Calendar.HOUR, cronTitle);
         }
         if (stillMatchedTime) {
            stillMatchedTime = isStillMatchedInInterval("MINUTES", cal, cronTimeUnit.getMinutes(), Calendar.MINUTE,
                  cronTitle);
         }
         return stillMatchedTime;
      }

      // Determining on sent calendar type if it is matched
      private boolean isStillMatchedInInterval(String slogan, Calendar now, List<Integer> interval, int calenderType,
            String cronTitle) {

         if (interval.get(0) == CronSchedule.EVERY_NUMBER) {
            return true;
         } else {

            for (Integer n : interval) {

               switch (calenderType) {

                  case Calendar.DAY_OF_WEEK:
                     logger.logDebug("Now has dayOfWeek : " + (now.get(Calendar.DAY_OF_WEEK) - 1));
                     logger.logDebug("Cal has daysOfWeek : " + n);
                     if ( (cal.get(Calendar.DAY_OF_WEEK) - 1) == n) {
                        return true;
                     }
                     break;

                  case Calendar.MONTH:
                     logger.logDebug("Now has month : " + (now.get(Calendar.MONTH) + 1));
                     logger.logDebug("Cal has month : " + n);
                     if ( (cal.get(Calendar.MONTH) + 1) == n) {
                        return true;
                     }
                     break;

                  case Calendar.DAY_OF_MONTH:
                     logger.logDebug("Now has dayOfMonth : " + now.get(Calendar.DAY_OF_MONTH));
                     logger.logDebug("Cal has dayOfMonth : " + n);
                     if (cal.get(calenderType) == n) {
                        return true;
                     }
                     break;

                  case Calendar.HOUR:
                     final int hour24 = getCurr24Hour(now);
                     logger.logDebug("Now has hour : " + hour24);
                     logger.logDebug("Cal has hour : " + n);
                     if (hour24 == n) {
                        return true;
                     }
                     break;

                  case Calendar.MINUTE:
                     logger.logDebug("Now has minute : " + now.get(Calendar.MINUTE));
                     logger.logDebug("Cal has minute : " + n);
                     if (cal.get(calenderType) == n) {
                        return true;
                     }
                     break;

                  default:
                     logger.logErr("Got unknown calenderType : " + calenderType);
                     break;
               }

               if (logger != null) {
                  logger.logInfo("Job " + cronTitle + " was denied on basis of : " + slogan);
               }
            }
            return false;
         }

      }

      // Just a helper log dumper
      private void dumpTime() {
         if (logger != null && logger.getLogLevel() == Logger.DEBUG) {
            final StringBuilder builder = new StringBuilder();
            final int realDay = cal.get(Calendar.DAY_OF_WEEK) - 1;
            final String cronDayOfWeek = "" + realDay;
            final int realMonth = cal.get(Calendar.MONTH) + 1;
            final String cronMonth = "" + realMonth;
            final String cronDayOfMonth = "" + cal.get(Calendar.DAY_OF_MONTH);
            final String cronHour = "" + getCurr24Hour(cal);
            final String cronMin = "" + cal.get(Calendar.MINUTE);
            final String currSec = "" + cal.get(Calendar.SECOND);
            builder.append(" dumpTime() : ");
            builder.append("min-");
            builder.append(cronMin);
            builder.append(" ");
            builder.append("hour-");
            builder.append(cronHour);
            builder.append(" ");
            builder.append("dayOfMon-");
            builder.append(cronDayOfMonth);
            builder.append(" ");
            builder.append("mon-");
            builder.append(cronMonth);
            builder.append(" ");
            builder.append("dayOfWeek-");
            builder.append(cronDayOfWeek);
            builder.append(" : curr sec ");
            builder.append(currSec);
            logger.logDebug(builder.toString());
         }
      }

      // converting to 24-hour format
      private static int getCurr24Hour(Calendar cal) {
         int hour = cal.get(Calendar.HOUR);
         if (cal.get(Calendar.AM_PM) == 1) {
            return hour + 12;
         }
         return hour;
      }
   }

   /**
    * These are the actual CronJobs we hold
    * 
    * @author ola@nollettnoll.net
    */
   private class CronJob {

      private CronExecutable executable;
      private CronTimeUnit scheme;

      private CronJob(CronTimeUnit scheme, CronExecutable executable) {
         this.scheme = scheme;
         this.executable = executable;
      }
   }

}
