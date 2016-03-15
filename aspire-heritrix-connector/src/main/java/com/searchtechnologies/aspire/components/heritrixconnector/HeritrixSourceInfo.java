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

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.archive.crawler.framework.CrawlJob;
import org.mapdb.*;

import com.searchtechnologies.aspire.framework.Standards.Scanner.Action;
import com.searchtechnologies.aspire.scanner.DSConnection;
import com.searchtechnologies.aspire.scanner.PushSourceInfo;
import com.searchtechnologies.aspire.services.AspireException;
import com.searchtechnologies.aspire.services.AspireObject;
import com.searchtechnologies.aspire.services.events.JobEvent;

/** This class contains all necessary information to handle Heritrix Source Information
 * 
 * @author aaguilar
 *
 */
public class HeritrixSourceInfo extends PushSourceInfo {
  public enum HeritrixScope {
    ALL,
    STAY_WITHIN_DOMAIN,
    STAY_WITHIN_HOST
  }

  public static final String SCOPE_DECISION_ACCEPT = "ACCEPT";

  public static final String SCOPE_DECISION_REJECT = "REJECT";

  public static final String SCOPE_DECIDE_RULE_NOT_ON_DOMAIN = "NotOnDomainsDecideRule";

  public static final String SCOPE_DECIDE_RULE_NOT_ON_HOST = "NotOnHostsDecideRule";

  public static final String INITIAL_CRAWL_STARTED = "STARTED";

  public static final String INITIAL_CRAWL_COMPLETE = "COMPLETE";

  public static final String TEMP_UNCRAWLED_DB_CREATED = "UNCRAWLED DB CREATED";

  public static final String ALL_COMPLETE = "ALL COMPLETE";


  /**
   * Number of documents on the queue, taken from Heritrix uriTotalReportData
   */
  private long queuedDocs=0;

  /**
   * Indicates which configuration file to load
   */
  private boolean useDefaultConfigFile=true;

  /**
   * Configuration file location if useDefaultConfigFile = false
   */
  private String configFileLocation = null;

  /**
   * Maximum number of hops to crawl, default = 3
   */
  private int maxHops = 3;

  /**
   * Crawl scope
   */
  private HeritrixScope scope;

  /**
   * Seconds to wait between requests
   */
  private long millisecondsPerRequest = 3000;

  /**
   * Crawl Regex Patterns
   */
  private URIFilterPattern crawlRegexPatterns = new URIFilterPattern();

  /**
   * Date and time when the crawl started
   */
  private Date startCrawlTime;

  /**
   * Tree map tied to the database record manager for this crawl. 
   */
  private Map<String,String> urlMap = null;

  /**
   * Tree map tied to the database record manager for this crawl, stores the URLs that should be checked for deletion. 
   */
  private Map<String,String> tempUncrawledDB = null;

  /**
   * If true the Scanner will continue to check the URLs that are accessible but not crawlable anymore.
   */
  private boolean checkNotCrawlableContent = false;

  /**
   * Number of days to wait since the last access for deleting failed URLs  
   */
  private int daysFailedThreshold = 2;

  /**
   * Number of failed access before deleting the URL
   */
  private int maxFailures = 5;

  /**
   * Directory where the database will be stored
   */
  private String urlDir = "";

  /**
   * Hash map that maps the host names with its URLs for deletion control throttling 
   */
  private HashMap<String, HostFetchStatus> hostHashMap = new HashMap<String,HostFetchStatus>();


  /**
   * Queue of URLs ordered by time. (the top of the queue has the next to be processed to delete 
   */
  private PriorityQueue<DataBaseURLEntry> priorityQueue;

  /**
   * Runnable object for thread throttle
   */
  private PriorityQueueChecker priorityQueueChecker;

  /**
   * Time to wait between uncrawled urls checks for the same host.
   */
  private long uncrawledAccessDelay;

  /**
   * Interval in minutes for the heritrix checkpoints
   */
  private int checkpointIntervalMinutes;

  /**
   * Delay in seconds between retries for failed seeds.
   */
  private int retryDelay = 20;

  /**
   * Number of retries for failed seeds.
   */
  private int maxRetries = 5;

  /**
   * Cleanup regex for cleanup the content before getting the modification signature
   */
  private String cleanupRegex = null;

  private CrawlJob job;

  private DB db;
  
  private AtomicInteger itemsCrawled;
  
  private AtomicInteger itemsSkipped;

  private boolean rejectDefaults = true;


  /**
   * Gets the Queue of URLs ordered by time. (the top of the queue has the next to be processed to delete)
   * @return the Queue of URLs ordered by time
   */
  public PriorityQueue<DataBaseURLEntry> getPriorityQueue(){
    return priorityQueue;
  }
  /**
   * Gets background thread that submit urls for uncrawled deletion check
   * @return the background thread that submit urls for uncrawled deletion check
   */
  public PriorityQueueChecker getPriorityQueueChecker() {
    return priorityQueueChecker;
  }

  /**
   * Gets the Hash map that maps the host names with its URLs for deletion control throttling
   * @return he Hash map that maps the host names with its URLs for deletion control throttling
   */
  public HashMap<String, HostFetchStatus> getHostHashMap() {
    return hostHashMap;
  }

  /**
   * Gets the URL db for this crawl
   * @return the URL db for this crawl
   */
  public Map<String, String> getUrlDB() {
    return urlMap;
  }

  /**
   * Gets the uncrawled db for this crawl
   * @return the uncrawled db for this crawl
   */
  public Map<String, String> getTempUncrawledDB() {
    return tempUncrawledDB;
  }

  /**
   * Sets the url db for this crawl
   * @param map the url db for this crawl
   */
  public void setUrlDB(Map<String, String> map) {
    this.urlMap = map;
  }

  /**
   * Sets the uncrawled db for this crawl
   * @param tempUncrawledDB the uncrawled db for this crawl
   */
  public void setTempUncrawledDB(Map<String, String> tempUncrawledDB) {
    this.tempUncrawledDB = tempUncrawledDB;
  }

  /**
   * Closes the database connection
   * @throws AspireException
   */
  public void closeUrlDB() throws AspireException{

    if (getUrlDB() != null){
      db.commit();
      db.close();
    }
    setUrlDB(null);

  }

  /**
   * Commits the changes to the database
   * @throws IOException 
   */
  public void commitUrlDB() throws AspireException {

    try{
      if (getUrlDB() != null){
        db.commit();
      }  
    }catch(Exception ioe){
      getLogger().warn("Could not commit entries to the database %s", ioe);
    }

  }


  /**
   * Constructs a new HeritrixSourceInfo
   * @param scanner the scanner
   */
  public HeritrixSourceInfo(HeritrixScanner scanner){
    super(scanner);
    
    itemsCrawled = new AtomicInteger(0);
    itemsSkipped = new AtomicInteger(0);
    
    sourceType="heritrix";
    priorityQueue = new PriorityQueue<DataBaseURLEntry>(5,new TimeComparator());
    priorityQueueChecker = new PriorityQueueChecker(this);
    
  }

  public DB getDB() {
    return db;
  }
  
  public void createDB () {
    File dbFile = new File(this.getUrlDir(),"urlDB");
    if (!dbFile.exists()) {
      dbFile.getParentFile().mkdirs();
    }
    db = DBMaker.newFileDB(dbFile)
        .closeOnJvmShutdown()
        .make();
  }
  
  public void setDB(DB db) {
    this.db = db;
  }
  
  /**
   * sets the queuedDocs
   * @param queuedDocs the queuedDocs to set
   */
  public void setQueuedDocs(long queuedDocs){
    this.queuedDocs=queuedDocs;
  }

  /*
   * (non-Javadoc)
   * @see com.searchtechnologies.aspire.snapshot.SourceInfo#getQueuedDocs()
   */
  @Override
  public long getQueuedDocs(){
    return queuedDocs;
  }

  /**
   * @param useDefaultConfigFile the useDefaultConfigFile to set
   */
  public void setUseDefaultConfigFile(boolean useDefaultConfigFile) {
    this.useDefaultConfigFile = useDefaultConfigFile;
  }

  /**
   * @return the useDefaultConfigFile
   */
  public boolean useDefaultConfigFile() {
    return useDefaultConfigFile;
  }

  /**
   * @param configFileLocation the configFileLocation to set
   */
  public void setConfigFileLocation(String configFileLocation) {
    this.configFileLocation = configFileLocation;
  }

  /**
   * @return the configFileLocation
   */
  public String getConfigFileLocation() {
    return configFileLocation;
  }

  /**
   * @param maxHops the maxHops to set
   */
  public void setMaxHops(int maxHops) {
    this.maxHops = maxHops;
  }

  /**
   * @return the maxHops
   */
  public int getMaxHops() {
    return maxHops;
  }

  /**
   * @param secondsPerRequest the seconds to wait between requests
   */
  public void setMillisecondsPerRequest(long secondsPerRequest){
    this.millisecondsPerRequest = secondsPerRequest;
  }

  /**
   * @return secondsPerRequest
   */
  public long getMillisecondsPerRequest() {
    return millisecondsPerRequest;
  }

  /**
   * @param scope the scope to set
   */
  public void setScope(HeritrixScope scope) {
    this.scope = scope;
  }

  public void setScope(String scope){
    if (scope!=null && scope.equalsIgnoreCase("staywithindomain")){
      this.scope = HeritrixScope.STAY_WITHIN_DOMAIN;
    } else if (scope!=null && scope.equalsIgnoreCase("staywithinhost")){
      this.scope = HeritrixScope.STAY_WITHIN_HOST;
    } else {
      //if all, or any wrong option, stores all
      this.scope = HeritrixScope.ALL;
    }
  }

  /**
   * @return the scope
   */
  public HeritrixScope getScope() {
    return scope;
  }

  /**
   * Returns the scope rule to be applied depending on the scope selected
   * 
   * scope = ALL
   *      NotOnDomainsDecideRule
   * 
   * scope = Within Domain
   *      NotOnDomainsDecideRule
   *      
   * scope = Within Host
   *      NotOnHostsDecideRule
   * @return String with the corresponding rule name value
   */
  public String getCrawlScopeDecideRuleValue(){
    switch (this.scope){
      case ALL:
      case STAY_WITHIN_DOMAIN:
        return SCOPE_DECIDE_RULE_NOT_ON_DOMAIN;
      case STAY_WITHIN_HOST:
        return SCOPE_DECIDE_RULE_NOT_ON_HOST;
      default:
        return SCOPE_DECIDE_RULE_NOT_ON_DOMAIN;
    }
  }

  public String getCrawlScopeDecisionValue(){
    switch (this.scope){
      case ALL:
        return SCOPE_DECISION_ACCEPT;
      case STAY_WITHIN_DOMAIN:
      case STAY_WITHIN_HOST:
        return SCOPE_DECISION_REJECT;
      default:
        return SCOPE_DECISION_ACCEPT;
    }
  }


  /**
   * @param crawlRegexPatternsObject the crawlRegexPatterns to set
   * @throws AspireException 
   */
  public void setCrawlRegexPatterns(AspireObject crawlRegexPatternsObject) throws AspireException {
    if (crawlRegexPatternsObject!=null){
      List<AspireObject> acceptPatternObjects = crawlRegexPatternsObject.getAll("accept");
      for (AspireObject acceptPatObject : acceptPatternObjects) {
        this.crawlRegexPatterns.addAcceptPattern(acceptPatObject.getAttribute("pattern"));
      }

      List<AspireObject> rejectPatternObjects = crawlRegexPatternsObject.getAll("reject");
      for (AspireObject rejectPatObject : rejectPatternObjects) {
        this.crawlRegexPatterns.addRejectPattern(rejectPatObject.getAttribute("pattern"));
      }
    }
  }

  /**
   * @return the crawlRegexPatterns
   */
  public URIFilterPattern getCrawlRegexPatterns() {
    return crawlRegexPatterns;
  }

  /**
   * Sets the date and time when the crawl started
   * @param startCrawlTime
   */
  public void setStartCrawlTime(Date startCrawlTime) {
    this.startCrawlTime = startCrawlTime;
  }

  /**
   * Gets the date and time when the crawl started
   * @return startCrawlTime
   */
  public Date getStartCrawlTime() {
    return startCrawlTime;
  }

  /**
   * Sets the number of days to wait since the last access for deleting failed URLs
   * @param daysFailedThreshold the number of days to wait since the last access for deleting failed URLs
   */
  public void setDaysFailedThreshold(int daysFailedThreshold) {
    this.daysFailedThreshold = daysFailedThreshold;
  }

  /**
   * Sets the number of failed access before deleting the URL
   * @param maxFailures he number of failed access before deleting the URL
   */
  public void setMaxFailures(int maxFailures) {
    this.maxFailures = maxFailures;
  }

  /**
   * Sets If the Scanner will continue to check the URLs that are accessible but not crawlable anymore.
   * @param checkNotCrawlableContent
   */
  public void setCheckNotCrawlable(boolean checkNotCrawlableContent) {
    this.checkNotCrawlableContent = checkNotCrawlableContent;
  }

  /**
   * Gets the number of days to wait since the last access for deleting failed URLs
   * @return daysFailedThreshold the number of days to wait since the last access for deleting failed URLs
   */
  public int getDaysFailedThreshold() {
    return daysFailedThreshold;
  }

  /**
   * Gets the number of failed access before deleting the URL
   * @return the number of failed access before deleting the URL
   */
  public int getMaxFailures() {
    return maxFailures;
  }

  /**
   * Gets true the Scanner will continue to check the URLs that are accessible but not crawlable anymore.
   * @return checkNotCrawlableContent
   */
  public boolean getCheckNotCrawlable() {
    return checkNotCrawlableContent;
  }

  /**
   * Sets the directory where the database will be stored
   * @param urlDir the directory where the database will be stored
   */
  public void setUrlDir(String urlDir){
    this.urlDir = urlDir;
  }

  /**
   * Gets the directory where the database will be stored
   * @return the directory where the database will be stored
   */
  public String getUrlDir(){
    return urlDir;
  }

  /**
   * Open the JDBM2 database and sets the urlDB. Uses the default filenames for the database 'jdbm2'.
   * @throws AspireException 
   */
  protected Map<String,String> openUrlDB() throws AspireException {
    return openDB("urlDB");
  }

  /**
   * Open the JDBM2 database and sets the urlDB. Uses the default filenames for the database 'jdbm2'.
   * @throws AspireException 
   */
  protected Map<String,String> openTempDB() throws AspireException {
    return openDB("temp_uncrawled_db");
  }

  /**
   * Open the JDBM2 database and sets the urlDB.
   * @param fname      Name that the files of the database will use
   * @throws AspireException 
   */
  protected Map<String,String> openDB(String fname) throws AspireException{
    return db.getHashMap(fname);
  }

  /**
   * Time to wait between uncrawled urls checks from the same host.
   * @return uncrawledAccessDelay
   */
  public long getUncrawledAccessDelay() {
    return uncrawledAccessDelay;
  }

  /**
   * Time to wait between uncrawled urls checks from the same host.
   * @param uncrawledAccessDelay
   */
  public void setUncrawledAccessDelay(long uncrawledAccessDelay) {
    this.uncrawledAccessDelay = uncrawledAccessDelay;
  }

  /**
   * Gets the interval in minutes for the heritrix checkpoints
   * @return checkpointIntervalMinutes
   */
  public int getCheckpointIntervalMinutes() {
    return checkpointIntervalMinutes;
  }

  /**
   * Sets the interval in minutes for the heritrix checkpoints
   * @param checkpointIntervalMinutes
   */
  public void setCheckpointIntervalMinutes(int checkpointIntervalMinutes) {
    this.checkpointIntervalMinutes = checkpointIntervalMinutes;
  }

  /**
   * Gets the delay in seconds between retries for failed seeds.
   * @return retryDelay
   */
  public int getRetryDelay() {
    return retryDelay;
  }

  /**
   * Sets the delay in seconds between retries for failed seeds.
   * @param retryDelay
   */
  public void setRetryDelay(int retryDelay) {
    this.retryDelay = retryDelay;
  }

  /**
   * Gets the number of retries for failed seeds
   * @return maxRetries the number of retries for failed seeds
   */
  public int getMaxRetries() {
    return maxRetries;
  }

  /**
   * Sets the number of retries for failed seeds
   * @param maxRetries the number of retries for failed seeds
   */
  public void setMaxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
  }
  
  @Override
  public DSConnection newDSConnection() throws AspireException {
    return null;
  }
  
  /**
   * Gets the cleanup regex for cleanup the content before getting the modification signature
   * @return the cleanup regex for the content
   */
  public String getCleanupRegex() {
    return cleanupRegex;
  }
  
  /**
   * Sets the cleanup regex for cleanup the content before getting the modification signature
   * @param cleanupRegex the cleanup regex for the content
   */
  public void setCleanupRegex(String cleanupRegex) {
    this.cleanupRegex = cleanupRegex;
  }

  /**
   * Gets the CrawlJob to run this crawl
   * @return the CrawlJob for this crawl
   */
  public CrawlJob getCrawlJob() {
    return job;
  }

  /**
   * Sets the CrawlJob to run this crawl
   * @param job the CrawlJob for this crawl 
   */
  public void setCrawlJob(CrawlJob job) {
    this.job = job;
  }

  @Override 
  public void close() throws AspireException {
    super.close();
    closeUrlDB();
    this.urlMap = null;
  }
  
  @Override
  public void jobComplete(Action action, JobEvent event) throws AspireException {
    // Nothing to do
  }
  public void incrementItemsCrawled() {
    itemsCrawled.incrementAndGet();
  }
  public boolean canContinue() {
    return !isTestMode() || (itemsCrawled.get() <= getItemsToTest());
  }
  public synchronized boolean skipTestItem() {
    boolean skip = isTestMode() && (itemsSkipped.get() < getItemsToSkip());
    if (skip)
      itemsSkipped.incrementAndGet();
    return skip;
  }
  public boolean rejectDefaults() {
    return rejectDefaults ;
  }
  
  public void setRejectDefaults(boolean reject) {
    rejectDefaults = reject;
  }
}
