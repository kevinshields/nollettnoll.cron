package nollettnoll.time.cron.schedule;

import java.util.List;

/**
 * A CronTimeUnit specifies execution times for a given cron job
 *
 * @author ola@nollettnoll.net
 *
 */
public class CronTimeUnit {

   // The minutes
   private List<Integer> minutes;

   // The hours
   private List<Integer> hours;

   // The days of month
   private List<Integer> daysOfMonth;

   // The months
   private List<Integer> months;

   // The days in week
   private List<Integer> daysInWeek;

   /**
    * The constructor takes lists of time units intrinsic to the nature of a cron job
    * 
    * @param daysInWeek days in week in which the job should run
    * @param months months in which the job should run
    * @param daysOfMonth days in month when the job should run
    * @param hours hours when the job should run
    * @param minutes when the job should run
    */
   public CronTimeUnit(List<Integer> daysInWeek, List<Integer> months, List<Integer> daysOfMonth, List<Integer> hours,
         List<Integer> minutes) {
      this.minutes = minutes;
      this.hours = hours;
      this.daysOfMonth = daysOfMonth;
      this.months = months;
      this.daysInWeek = daysInWeek;
   }

   /**
    * Returns the minutes for this CronTimeUnit
    * 
    * @return the minutes
    */
   public List<Integer> getMinutes() {
      return minutes;
   }

   /**
    * Returns the hours for this CronTimeUnit
    * 
    * @return the hours
    */
   public List<Integer> getHours() {
      return hours;
   }

   /**
    * Returns the days in month for this CronTimeUnit
    * 
    * @return the days in month
    */
   public List<Integer> getDaysInMonth() {
      return daysOfMonth;
   }

   /**
    * Returns the months for this CronTimeUnit
    * 
    * @return the months
    */
   public List<Integer> getMonths() {
      return months;
   }

   /**
    * Returns the days in week for this CronTimeUnit
    * 
    * @return the days in week
    */
   public List<Integer> getDaysInWeek() {
      return daysInWeek;
   }
}
