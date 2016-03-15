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

import java.text.ParseException;
import java.util.Date;

/**
 * Database entry for each URL crawled by Heritrix.
 * @author aaguilar
 *
 */
public class DataBaseURLEntry {
  
  /**
   * Last modified timestamp
   */
  private Date timestamp = null;
  
  /**
   * Represents the last time when Heritrix accessed this URL  
   */
  private Date lastAccessedTime=null;
  
  /**
   * Represents the first time when Heritrix couldn't access this URL.
   */
  private Date dateFirstFailed=null;
  
  /**
   * Counts how many times Heritrix failed on accessing this URL
   */
  private int failCount=0;
  
  /**
   * Represents the content of this URL 
   */
  private String md5Sum = "";
  
  /**
   * Time in milliseconds when this url should be sent to the process-uncrawled pipeline
   */
  private long timeToSubmit;
  
  /**
   * Url for this document
   */
  private String url;
  
  /**
   * Gets the Url for this object. This is only set by an explicit call of setUrl(url) becouse createDataBaseURLEntryFromString don't instantiate it   
   * @return url the Url for this object
   */
  public String getUrl() {
    return url;
  }
  
  /**
   * Sets the url for this object
   * @param url the Url for this object
   */
  public void setUrl(String url){
    this.url = url;
  }

  /**
   * Get the Time in milliseconds when this url should be sent to the process-uncrawled pipeline 
   * @return the Time in milliseconds when this url should be sent to the process-uncrawled pipeline
   */
  public long getTimeToSubmit() {
    return timeToSubmit;
  }

  /**
   * Set the Time in milliseconds when this url should be sent to the process-uncrawled pipeline 
   * @param timeToSubmit the Time in milliseconds when this url should be sent to the process-uncrawled pipeline
   */
  public void setTimeToSubmit(long timeToSubmit) {
    this.timeToSubmit = timeToSubmit;
  }

  /**
   * Creates a new instance of DataBaseURLEntry
   * @param lastAccessedTime Represents the last time when Heritrix accessed this URL
   * @param dateFirstFailed Represents the first time when Heritrix couldn't access this URL.
   * @param failCount Counts how many times Heritrix failed on accessing this URL
   * @param md5Sum Represents the content of this URL 
   */
  public DataBaseURLEntry(Date lastAccessedTime, Date dateFirstFailed, int failCount, String md5Sum, Date timestamp){
    this.lastAccessedTime = lastAccessedTime;
    this.dateFirstFailed = dateFirstFailed;
    this.failCount = failCount;
    this.md5Sum = md5Sum;
    this.timestamp = timestamp;
  }
  
  /**
   * Gets the LastAccessedTime for this URL
   * @return LastAccessedTime the LastAccessedTime for this URL
   */
  public Date getLastAccessedTime() {
    return lastAccessedTime;
  }
  
  /**
   * Sets the LastAccessedTime for this URL
   * @param lastAccessedTime the LastAccessedTime for this URL
   */
  public void setLastAccessedTime(Date lastAccessedTime) {
    this.lastAccessedTime = lastAccessedTime;
  }
  
  /**
   * Gets the timestamp for this URL
   * @return the timestamp for this URL
   */
  public Date getTimestamp() {
    return timestamp;
  }
  
  /**
   * Sets the timestamp for this URL
   * @param timestamp the LastAccessedTime for this URL
   */
  public void setTimestamp(Date timestamp) {
    this.timestamp = timestamp;
  }
  
  /**
   * Gets the DateFirstFailed for this URL
   * @return the DateFirstFailed for this URL
   */
  public Date getDateFirstFailed() {
    return dateFirstFailed;
  }
  
  /**
   * Sets the DateFirstFailed for this URL
   * @param dateFirstFailed the DateFirstFailed for this URL
   */
  public void setDateFirstFailed(Date dateFirstFailed) {
    this.dateFirstFailed = dateFirstFailed;
  }
  
  /**
   * Gets the FailCount for this URL
   * @return the FailCount for this URL
   */
  public int getFailCount() {
    return failCount;
  }
  
  /**
   * Sets the FailCount for this URL
   * @param failCount
   */
  public void setFailCount(int failCount) {
    this.failCount = failCount;
  }

  /**
   * Gets the MD5 representation of the content for this URL 
   * @return the MD5 representation of the content for this URL 
   */
  public String getMd5Sum() {
    return md5Sum;
  }

  /**
   * Sets the MD5 representation of the content for this URL
   * @param md5Sum the MD5 representation of the content for this URL 
   */
  public void setMd5Sum(String md5Sum) {
    this.md5Sum = md5Sum;
  }
  
  @Override 
  public String toString(){
    String datefirstfailed = "";
    if (dateFirstFailed != null)
      datefirstfailed = ""+dateFirstFailed.getTime();
    return lastAccessedTime.getTime()+"|"+
      datefirstfailed+"|"+
      failCount+"|"+md5Sum+"|"+timestamp.getTime();
  }
  
  /**
   * Creates a new instance of DataBaseURLEntry based on a pipe separated string.
   * @param data Pipe separated string with the entire data for this URL
   * @return an new Instance of DataBaseURLEntry
   * @throws ParseException
   */
  public static DataBaseURLEntry createDataBaseURLEntryFromString(String data) {
    if (data != null && !data.isEmpty()){
      String []splitData = data.split("\\|");
      Date lastAccessedTime = new Date(Long.parseLong(splitData[0]));
      Date dateFirstFailed = null;
      Date timestamp = null;
      if (!splitData[1].isEmpty()){
        dateFirstFailed = new Date(Long.parseLong(splitData[1]));
      }
      if (!splitData[4].isEmpty()){
        timestamp = new Date(Long.parseLong(splitData[4]));
      }
      return new DataBaseURLEntry(lastAccessedTime,dateFirstFailed,Integer.parseInt(splitData[2])/*fail count*/,splitData[3]/*MD5Sum*/,timestamp);
    }else
      return null;
  }
  
  /**
   * Increment the value of the fail Count variable
   */
  public void incrementFailCount(){
    failCount++;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((md5Sum == null) ? 0 : md5Sum.hashCode());
    result = prime * result + ((url == null) ? 0 : url.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    DataBaseURLEntry other = (DataBaseURLEntry) obj;
    if (url == null) {
      if (other.url != null)
        return false;
    } else if (!url.equals(other.url))
      return false;
    return true;
  }
  
  
}
