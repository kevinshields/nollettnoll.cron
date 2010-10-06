package nollettnoll.time.cron.executables;

import java.io.IOException;

import nollettnoll.generic.log.Logger;
import nollettnoll.generic.log.impl.LoggerImpl;
import nollettnoll.time.cron.CronExecutable;

/**
 * 
 * The CronExecutable for native system exec; what is triggered from you own
 * crontab when running the CronTabRunner or when you schedule your own
 * SystemExecutable 
 * 
 * @author ola@nollettnoll.net
 * 
 */
public class SystemExecutable implements CronExecutable {

   private String toExec;
   private String args;
   private Logger logger;

   // The run thread
   private RunThis runThis;

   // The name
   private String name;

   // Logger suffix
   private static final String LOG_SUFFIX = SystemExecutable.class.getName();

   /**
    * Constructor for running a system executable
    * 
    * @param cronExec full path to the executable and the arguments
    */
   public SystemExecutable(String cronExec) {
      Logger logger = new LoggerImpl(LOG_SUFFIX);
      logger.setIOUsage(Logger.IO_BOTH_OUT);
      logger.setLogLevel(Logger.DEBUG);
      this.logger = logger;
      final String[] cmdAndArgs = cronExec.split(" ");
      init(cmdAndArgs);
   }

   /**
    * The constructor takes the executable (arg1) and
    * it's args
    * 
    * @param cmdAndArgs path to the executable and its args
    * @param logger the logger
    */
   public SystemExecutable(String[] cmdAndArgs, Logger logger) {
      this.logger = logger;
      init(cmdAndArgs);
   }

   private void init(String[] cmdAndArgs) {
      this.toExec = cmdAndArgs[0];
      this.args = getJobExec(cmdAndArgs);
      final StringBuilder nameBuilder = new StringBuilder();
      nameBuilder.append("System.exec-[");
      nameBuilder.append(toExec.toString());
      nameBuilder.append(" ");
      nameBuilder.append(args);
      nameBuilder.append("]");
      this.name = nameBuilder.toString();
   }

   @Override
   public String cronTitle() {
      return name;
   }

   @Override
   public void executeOnToc() {
      if (logger != null) {
         logger.logInfo(name + " is executing!");
      }

      // init the system exec runner
      if (runThis != null) {
         runThis.stopThread();
         runThis = null;
      }
      runThis = new RunThis(this.toExec, this.args, this.name, this.logger);
      runThis.start();
   }

   /**
    * The runnable - actual System.execs are triggered in instances of this
    * flavor
    * 
    * @author ola@nollettnoll.net
    *
    */
   private final class RunThis extends Thread {

      private final String toExec;
      private final String args;
      private final String name;
      private final Logger logger;
      private boolean running;

      protected RunThis(String toExec, String args, String name, Logger logger) {
         this.toExec = toExec;
         this.args = args;
         this.logger = logger;
         this.name = name;
         running = false;
      }

      @Override
      public void run() {
         running = true;
         while (running) {
            Process p = null;
            Exception exc = null;
            try {
               if (args != null) {
                  p = Runtime.getRuntime().exec(toExec + " " + args);
               } else {
                  p = Runtime.getRuntime().exec(toExec);
               }
               p.waitFor();
            } catch (IOException e) {
               exc = e;
            } catch (InterruptedException e) {
               exc = e;
            }

            if (exc != null) {
               logger.logInfo(name + " caught exception : " + exc.getMessage());
            }

            if (logger != null && p != null) {
               logger.logInfo(name + " executed. ExitValue was : " + p.exitValue());
               p.destroy();
            }

            running = false;
         }
      }

      protected void stopThread() {
         running = false;
      }
   }

   /*
    * Small helper to parse args
    */
   private final static String getJobExec(String[] cmdAndArgs) {
      final StringBuilder builder = new StringBuilder();
      int i = 0;
      for (i = 1; i < cmdAndArgs.length; i++) {
         builder.append(cmdAndArgs[i]);
         builder.append(" ");
      }
      if (i > 1) { // got args
         return StringUtils.oneSpace(builder.toString());
      } else {
         return null;
      }

   }

}
