/***************************************************************************
 * Copyright 2012 Search Technologies Corp. 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package com.searchtechnologies.aspire.components.heritrixconnector;

import java.util.Date;

import com.searchtechnologies.aspire.framework.Standards;
import com.searchtechnologies.aspire.framework.utilities.DateTimeUtilities;
import com.searchtechnologies.aspire.scanner.SourceItem;
import com.searchtechnologies.aspire.services.AspireException;
import com.searchtechnologies.aspire.services.AspireObject;
import com.searchtechnologies.aspire.services.Job;
import com.searchtechnologies.aspire.services.events.JobEvent;

/**
 * Background thread for checking the priority queue of uncrawled urls. Used for throttling fetches of failed URLs.
 * @author aaguilar
 *
 */
public class PriorityQueueChecker implements Runnable {

  /*
   * Source info corresponding to the actual crawl.
   */
  private HeritrixSourceInfo info;

  /**
   * Determines if the thread should be running or not
   */
  private boolean running;

  /**
   * Determines if the thread is stopped by an UI command
   */
  private boolean stopped;

  /**
   * Determines if the thread is paused by an UI command 
   */
  private boolean paused;

  /**
   * Thread running this class
   */
  private Thread thread;

  /**
   * The crawl Parent Job
   */
  private Job j;

  /**
   * Scanner Component used for sending subJobs down the pipeline
   */
  private HeritrixScanner scanner; 

  @Override
  public void run() {

    while (running){
      DataBaseURLEntry entry =null;

      //Poll a url entry from the priority queue
      synchronized (info.getPriorityQueue()){
        entry = info.getPriorityQueue().poll();
      }



      while (entry!=null && !stopped){
        boolean contains = false;
        synchronized (info.getTempUncrawledDB()){
          contains = info.getTempUncrawledDB().containsKey(entry.getUrl());
        }
        if (!contains){
          //ignore url
        }else if (new Date().getTime() > entry.getTimeToSubmit()){
          try {

            if (info.getCheckNotCrawlable()) {
              
              checkAvailability(entry);
              
            } else { 
              //If we don't have to check for the availability of the URL, check deletion limits
              
              SourceItem item = new SourceItem(entry.getUrl());
              item.addField("md5", entry.getMd5Sum());
              item.addField("fail-count", entry.getFailCount());
              item.addField("lastAccessedTime", entry.getLastAccessedTime().getTime());
              
              if ((new Date().getTime() - entry.getLastAccessedTime().getTime()) > 
                    info.getDaysFailedThreshold()*86400000 /*time threshold*/ 
                  || entry.getFailCount()+1 >= info.getMaxFailures() /*maximum failures*/){

                if (info.getCheckNotCrawlable()) 
                  info.getLogger().debug("Sending to check uncrawled url: %s",entry.getUrl());
                else
                  info.getLogger().debug("Sending to delete uncrawled url: %s",entry.getUrl());
                
                //The maximum days of fail count was reached, we have to delete it.
                scanner.deleteAction(item, info);
                
                info.getIncrementalDB().remove(item.getUrl());
                
              } else { //We don't have to do anything, just update the logs and database 
                
                //Update failed count in JDBM2
                entry.incrementFailCount();

                //Set dateFirstFailed if this is the first time.
                if (entry.getDateFirstFailed()==null)
                  entry.setDateFirstFailed(info.getStartCrawlTime());

                info.getLogger().debug("Uncrawled url: %s updated to %d. Date of first failed: %s",entry.getUrl(),entry.getFailCount(),DateTimeUtilities.getISO8601Date(entry.getDateFirstFailed()));
                
                //Update database
                info.getIncrementalDB().put(entry.getUrl(), entry.toString());

                //Add to "failed URLs" log file
                scanner.failedUrlsLog.writeToFile("FAILED: %s LastAccessedTime: %s DateFirstFailed: %s Fail-Count: %d MD5Sum: %s",
                    entry.getUrl(),DateTimeUtilities.getISO8601DateTime(entry.getLastAccessedTime()),
                    DateTimeUtilities.getISO8601DateTime(entry.getDateFirstFailed()),entry.getFailCount(),entry.getMd5Sum());

              }
              //Enqueue.enqueue(scanner, bh, "checkForDeletion", "HeritrixScanner", subJob, 5, new File("failedjobs"));
            }
            // Remove the url entry from the uncrawled database
            synchronized (info.getTempUncrawledDB()){
              info.getTempUncrawledDB().remove(entry.getUrl());
            }

          } catch (AspireException e) {
            info.getLogger().error(e,"Error trying to send new job for url: %s",entry.getUrl());
            try {
              info.setScannerErrorMessage(e,"Error trying to send new job for url: %s",entry.getUrl());
            } catch (AspireException e2) {
              info.getLogger().error(e2,"Error trying to set Scanner Error");
            }
            running = false;
          }

        }else{
          //Return entry to queue
          info.getPriorityQueue().add(entry);

          try {
            //Sleep until next url is ready for be processed.
            long time = entry.getTimeToSubmit() - new Date().getTime();
            if (time > 0)
              Thread.sleep(time);
          } catch (InterruptedException e) {
            info.getLogger().error(e,"Error on Thread sleep");
            try {
              info.setScannerErrorMessage(e,"Error on Thread sleep");
            } catch (AspireException e2) {
              info.getLogger().error(e2,"Error trying to set Scanner Error");
            } 
            running = false;
          }
        }
        //If paused wait until it is resumed
        while (paused);
        synchronized (info.getPriorityQueue()){
          entry = info.getPriorityQueue().poll();
        }
      }

      //if there are no more url entries on the uncrawled database stop the thread.
      if (info==null || info.getTempUncrawledDB()==null || info.getTempUncrawledDB().size() == 0){
        running = false;
        scanner.deleteFinished = true;
      }
    }

  }

  private void checkAvailability(DataBaseURLEntry entry) throws AspireException {
    AspireObject subJobDoc = new AspireObject(Standards.Basic.DOCUMENT_ROOT);
    //Adds fetchUrl to subjob data

    subJobDoc.add(Standards.Basic.FETCH_URL_TAG, entry.getUrl());

    //   subJobDoc.add("crawlId", info.getCrawlId());
    subJobDoc.add("uncrawled","true");
    subJobDoc.add("md5", entry.getMd5Sum());
    subJobDoc.add("fail-count", entry.getFailCount());
    subJobDoc.add("lastAccessedTime", entry.getLastAccessedTime().getTime());

    subJobDoc.add(info.getPropertiesXml());

    AspireObject connectorSpecific = subJobDoc.get("connectorSpecific");
    if (connectorSpecific != null) {
      subJobDoc.push(connectorSpecific);
    } else {
      subJobDoc.push("connectorSpecific");
    }
    subJobDoc.setAttribute("type", info.getSourceType());
    subJobDoc.add("displayName", info.getFriendlyName());
    subJobDoc.pop();

    //Creates subjob
    Job subJob = j.createSubJob(subJobDoc);
    subJob.registerListener((HeritrixScanner)scanner, JobEvent.SUCCESS_EVENT | JobEvent.UNHANDLED_ERROR_EVENT);

    scanner.enqueue(info, subJob, "processUncrawled");
  }

  public void resume(){
    paused = false;
  }

  public void pause(){
    paused = true;
  }

  public void stop(){
    running = false;
    stopped = true;
    paused = false;
  }

  public void start(Job j, HeritrixScanner scanner){
    this.scanner = scanner;
    this.j = j;
    if (thread!=null && running == true){
      running = false;
      stopped = true;
      paused = false;
      while (thread.isAlive());
    }
    running = true;
    stopped = false;
    paused = false;
    thread = new Thread(this);
    thread.start();
  }

  public PriorityQueueChecker(HeritrixSourceInfo info){
    running = false;
    this.info = info;
  }

  public boolean isRunning() {
    return running;
  }

}
