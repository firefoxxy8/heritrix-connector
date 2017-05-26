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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.LogManager;

import org.apache.commons.lang.mutable.MutableInt;
import org.archive.crawler.framework.CrawlJob;
import org.archive.crawler.framework.Engine;
import org.archive.modules.credential.HttpAuthenticationCredential;
import org.w3c.dom.Element;

import com.searchtechnologies.aspire.framework.AXML;
import com.searchtechnologies.aspire.framework.BranchHandlerFactory;
import com.searchtechnologies.aspire.framework.RotatingFileWriter;
import com.searchtechnologies.aspire.framework.Standards;
import com.searchtechnologies.aspire.framework.Standards.Scanner;
import com.searchtechnologies.aspire.framework.utilities.DateTimeUtilities;
import com.searchtechnologies.aspire.framework.utilities.FileUtilities;
import com.searchtechnologies.aspire.framework.utilities.PropertyUtilities;
import com.searchtechnologies.aspire.framework.utilities.StringUtilities;
import com.searchtechnologies.aspire.groupexpansion.cache.SpecialAclStore;
import com.searchtechnologies.aspire.groupexpansion.cache.UserGroupCache;
import com.searchtechnologies.aspire.scanner.AbstractPushScanner;
import com.searchtechnologies.aspire.scanner.ItemType;
import com.searchtechnologies.aspire.scanner.PushSourceInfo;
import com.searchtechnologies.aspire.scanner.SourceInfo;
import com.searchtechnologies.aspire.scanner.SourceItem;
import com.searchtechnologies.aspire.services.AspireException;
import com.searchtechnologies.aspire.services.AspireObject;
import com.searchtechnologies.aspire.services.BranchHandler;
import com.searchtechnologies.aspire.services.Job;
import com.searchtechnologies.aspire.services.auditing.AuditConstants;
import com.searchtechnologies.aspire.services.auditing.AuditConstants.Actions;
import com.searchtechnologies.aspire.services.events.BatchEvent;
import com.searchtechnologies.aspire.services.events.JobEvent;
import com.searchtechnologies.aspire.services.events.ProcessingEvent;
import com.searchtechnologies.aspire.services.groupexpansion.UoG;

/** Implements a Heritrix based web crawler that talks to Aspire to publish output jobs.
 * 
 * @author aaguilar
 */
public class HeritrixScanner extends AbstractPushScanner {

  static final String STAGE_ID = "heritrixScanner";

  /**
   * Default heritrix job dir
   */
  private String heritrixJobsDir = "heritrixJobs";

  /**
   * Heritrix engine
   */
  private Engine engine = null;

  /**
   * BranchHandler
   */
  BranchHandler bh = null;

  /**
   * Source display name configured from the component's configuration
   */
  private String sourceDisplayName = null;

  /**
   * Indicates whether or not to load the default config file (overwritten by connectorSource info from the process job)
   */
  private boolean defaultConfigFile = true;

  /**
   * if defaultConfigFile == false, then uses this file to create the Heritrix job
   */
  private String configFileLocation = null;

  /**
   * Aspire object with all accept/reject crawl patterns
   */
  private AspireObject crawlPatterns = null;

  /**
   * Max hops to crawl
   */
  private int maxHops = 3;

  /**
   * Seconds per request
   */
  private long maxDelayMs = 3000;

  /**
   * Crawl scope
   */
  private String scope = "all";

  /**
   * updater component name
   */
  private String updaterComponentName = null;

  /**
   * Wait for subjob timeout
   */
  long waitForSubJobsTimeout = DateTimeUtilities.MINUTES(10);


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
   * Directory where the MapDB database is going to be stored
   */
  private String mapDBDir = null;

  /**
   * Logger for all the deleted URLs
   */
  protected RotatingFileWriter deletedUrlsLog = null;

  /**
   * Logger for all the failed URLs
   */
  protected RotatingFileWriter failedUrlsLog = null;

  /**
   * Delay between checks of URLs of the same host.
   */
  private long uncrawledAccessDelay;

  /**
   * Heritrix interval (in minutes) for checkpoints
   */
  private int checkpointIntervalMinutes;
  
  /**
   * Maximum number of threads for Heritrix
   */
  private int maxHeritrixThreads = 3;
  
  /**
   *  Queue assignment policy
   */
  private String queueAssignmentPolicy = "HostnameQueueAssignmentPolicy";

  /**
   * Number of queues when the HashingQueueAssignmentPolicy queue assignment policy is selected 
   */
  private int parallelQueues = -1;
  
  /**
   * String to be passed to build the document to pass to the Heritrix engine with Number of queues for the HashingQueueAssignmentPolicy 
   */
  private String parallelQueuesString = "";
  
  
  boolean stop = false;
  boolean paused = false;
  
  boolean deleteFinished = true;

  @Override
  public void doTestCrawl(PushSourceInfo si, int numSkipDocuments,
      int numProcessDocuments) throws AspireException {
    HeritrixSourceInfo info = (HeritrixSourceInfo)si;
    
    info.closeUrlDB();
    
    info.setUrlDir(mapDBDir+"/urlDB-Test");
    
    info.setIncrementalDB(info.openIncrementalDB());
    
    info.getIncrementalDB().clear();
    info.commitUrlDB();
    
    crawl(info);
  }

  @Override
  public void doCrawl(PushSourceInfo si) throws AspireException{
    HeritrixSourceInfo info = (HeritrixSourceInfo)si;
    if (si.fullCrawl()) {
      info.getIncrementalDB().clear();
      info.commitUrlDB();
    }
    info.setItemsToSkip(0);
    info.setItemsToTest(Integer.MAX_VALUE);
    crawl(info);
  }

  @Override
  public void stop(PushSourceInfo si) throws AspireException {
    HeritrixSourceInfo info = (HeritrixSourceInfo) si;
    CrawlJob job = info.getCrawlJob();
    stop = true;

    waitForMainCrawl(info);
    synchronized (this) {
     
      if (!job.getCrawlController().isPaused() && !job.getCrawlController().isPausing()) {
        pause(si);
      }

      job.terminate();
      
      info.commitUrlDB();
      
    }
    
  }

  @Override
  public void pause(PushSourceInfo si) {
    paused = true;
    HeritrixSourceInfo info = (HeritrixSourceInfo) si;
    
    waitForMainCrawl(info);
    if (!info.getCrawlJob().getCrawlController().isPausing() && !info.getCrawlJob().getCrawlController().isPaused() && !"FINISHED".equals(info.getCrawlJob().getCrawlController().getState().toString())) {
      synchronized (this) {
        while ((info.getCrawlJob().isUnpausable() || !info.getCrawlJob().isRunning()) && !"FINISHED".equals(info.getCrawlJob().getCrawlController().getState().toString())) {
          busyWait(100);
          
        }
        info.getCrawlJob().getCrawlController().requestCrawlPause();
      }
    }
  }

  /**
   * Wait until the main crawl starts
   * @param info
   */
  private void waitForMainCrawl(HeritrixSourceInfo info) {
    boolean waiting = false;
    //The crawl has not started yet, wait until it is and pause it.
    while (info.getCrawlJob() == null || 
           info.getCrawlJob().getCrawlController() == null) {
      busyWait(100);
      waiting = true;
    }
    if (waiting) {
      //Give the main crawl 500 milliseconds to start the crawl before pausing it
      busyWait(500);
    }
  }

  /**
   * Just wait for ms milliseconds
   * @param ms
   */
  private void busyWait(int ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
    }
  }

  @Override
  public void resume(PushSourceInfo si) throws AspireException {
    paused = false;
    HeritrixSourceInfo info = (HeritrixSourceInfo) si;
    info.getCrawlJob().getCrawlController().requestCrawlResume();
    info.commitUrlDB();
    crawl((HeritrixSourceInfo) si);
  }

  public void crawl(HeritrixSourceInfo info) throws AspireException{
 
      stop = false;
      CrawlJob job;

      job = info.getCrawlJob();
    synchronized (this){
      if (job == null) {

        //Prepares the CrawlJob using user configuration
        job = prepareEngine(info);

        info.setCrawlJob(job);

        checkForCheckpoints(job, info);

        //wait for the job to be launchable
        while (!job.isLaunchable()) {
          busyWait(100);
        }
        busyWait(5000);
        
        job.launch();

        //wait for the job to be running (starts paused)
        while (!job.isUnpausable()) {
          busyWait(100);
        }

        //Requests a resume to unpause the job (unpause from UI)
        job.getCrawlController().requestCrawlResume();
        while(!job.isRunning()) {
          busyWait(100);
        }
        busyWait(3000);
      }
    }
    //waits for the job to finish
    while(job.isRunning() && 
        !info.getCrawlJob().getCrawlController().isPaused() &&
        info.canContinue() && info.getStatus() != SourceInfo.SCAN_ABORTED) {
      busyWait(100);
    }
    if (paused) 
      return;
    
    if (!stop) {
      //Delete the checkpoints
      if (job.getCheckpointService()!=null) {
        debug("Deleting checkpoints");
        for (File file : job.getCheckpointService().findAvailableCheckpointDirectories()) {
          while (!FileUtilities.delete(file)) {
            try {
              Thread.sleep(100);
            } catch (InterruptedException e) {
              // Do nothing
            }
          }
        }
        debug("Checkpoints deleted");
      }
      job.teardown();

      info.getIncrementalDB().put("||status||", HeritrixSourceInfo.INITIAL_CRAWL_COMPLETE+","+info.getStartCrawlTime().getTime());

      try {
        if (info.getStatus()==HeritrixSourceInfo.SCAN_START) {
          info("Started to process deletes for uncrawled URLs");
          deleteAfterCrawl(info);
        }
      } catch (IOException ioe) {
        throw new AspireException("com.searchtechnologies.aspire.components.heritrixconnector.HeritrixScanner", ioe,
            "Error trying to process uncrawled urls");
      }
    } else {
      job.teardown();
    }
    
    
    while (!deleteFinished) {
      try {
        Thread.sleep(400);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    
  }

  /**
   * Check for checkpoints to be restored in this crawl. Sets the heritrix job to restore that checkpoint crawl.
   * @param job
   * @throws AspireException
   */
  private void checkForCheckpoints(CrawlJob job, HeritrixSourceInfo info) throws AspireException {
    long last = -1;
    File lastFile = null;
    if (job.getCheckpointService()!=null)
      for (File file : job.getCheckpointService().findAvailableCheckpointDirectories()){
        long fileTime;

        try {
          fileTime = new SimpleDateFormat("yyyyMMddHHmmss").parse(file.getName().split("-")[1]).getTime();
        } catch (ParseException e) {
          throw new AspireException("com.searchtechnologies.aspire.components.heritrixconnector.ParseError", e,
              "Error trying to parse date format");
        }
        if (fileTime > last){
          last = fileTime;
          lastFile = file;
        }
      }

    if (lastFile!=null && !info.fullCrawl() && (job.getCheckpointService()!=null)){
      job.getCheckpointService().setRecoveryCheckpointByName(lastFile.getName());
      String statusData = info.getIncrementalDB().get("||status||");

      if (statusData!=null) //if there is no status on the database it means it is empty.
        info.setStartCrawlTime(new Date(Long.parseLong(statusData.split(",")[1])));
    }

    info.getIncrementalDB().put("||status||", HeritrixSourceInfo.INITIAL_CRAWL_STARTED+","+info.getStartCrawlTime().getTime());
    info.commitUrlDB();
  }

  private CrawlJob prepareEngine(HeritrixSourceInfo info) throws AspireException {
    //rescans job folder to check if any job has been deleted or added
    engine.findJobConfigs();

    CrawlJob job =null;

    String jobFolderName = "HeritrixCrawls";

    //if job folder doesn't exist, creates it
    File jobFolder = new File (heritrixJobsDir +"/" + jobFolderName);
    if (!jobFolder.exists()){
      jobFolder.mkdir();
    }

    File jobConfigFile = new File (heritrixJobsDir +"/" + jobFolderName + "/crawler-beans.cxml");
    if (jobConfigFile.exists()){
      jobConfigFile.delete();
    }


    if (info.useDefaultConfigFile()){

      //Downloads the default crawler-beans.cxml and creates the new job
      InputStream is = getServletInputStream("/crawler-beans.cxml");
      AXML a = new AXML(new InputStreamReader(is));
      Element e = a.getMyElement();
      //Load properties from source info to replace in profile crawler-beans.cxml
      Map<String, String> heritrixConfigProperties = new HashMap<String, String>();
      heritrixConfigProperties.put("heritrixSeedUrl", info.getStartUrl());
      heritrixConfigProperties.put("acceptCrawlRegex", info.getCrawlRegexPatterns().getAcceptPatternsAsBeansListValues());
      heritrixConfigProperties.put("rejectCrawlRegex", info.getCrawlRegexPatterns().getRejectPatternsAsBeansListValues());
      heritrixConfigProperties.put("rejectDefaults", info.getCrawlRegexPatterns().getRejectDefaults(info.rejectDefaults()));
      heritrixConfigProperties.put("maxHops", Integer.toString(info.getMaxHops()));
      heritrixConfigProperties.put("scopeDecideRule", info.getCrawlScopeDecideRuleValue());
      heritrixConfigProperties.put("scopeDecision", info.getCrawlScopeDecisionValue());
      heritrixConfigProperties.put("maxDelayMs", Long.toString(info.getMillisecondsPerRequest()));
      heritrixConfigProperties.put("checkpointIntervalMinutes", String.valueOf(info.getCheckpointIntervalMinutes()));
      heritrixConfigProperties.put("retryDelay", String.valueOf(info.getRetryDelay()));
      heritrixConfigProperties.put("maxRetries", String.valueOf(info.getMaxRetries()));
      heritrixConfigProperties.put("maxHeritrixThreads", String.valueOf(info.getmaxHeritrixThreads()));
      heritrixConfigProperties.put("queueAssignmentPolicy", info.getQueueAssignmentPolicy());
      heritrixConfigProperties.put("parallelQueuesString", info.getParallelQueuesString());

      e = PropertyUtilities.substitutePropertiesInElement(heritrixConfigProperties, e);
      AXML.write(e, jobConfigFile, true);
    } else {
      File configFile = new File (info.getConfigFileLocation());
      InputStream input = null;
      OutputStream output = null;

      try {

        //Copy the custom crawler-beans
        input = new FileInputStream(configFile);
        output = new FileOutputStream(jobConfigFile);
        FileUtilities.copyStream(input, output);

      } catch (FileNotFoundException e) {
        throw new AspireException ("com.searchtechnologies.aspire.components.heritrixconnector.HeritrixScanner.CustomJobConfigNotFound", e,
            "Custom Job Configuration file not found: %s", configFile);
      } catch (IOException e) {
        throw new AspireException("con.searchtechnologies.aspire.component.heritrixconnector.ErrorWritingCustomJobConfig", e,
            "Error writing custom configuration file to job folder: %s", jobConfigFile);
      } finally {
        try {
          if (input!=null){
            input.close();
          }
          if (output!=null){
            output.flush();
            output.close();
          }
        } catch (IOException e) {

          throw new AspireException("con.searchtechnologies.aspire.component.heritrixconnector.ErrorWritingCustomJobConfig", e, 
              "Error writing custom configuration file to job folder: %s", jobConfigFile);

        }
      }
    }


    //once job folder is created, or job already exists, loads the folder to the engine
    engine.addJobDirectory(new File (heritrixJobsDir +"/" + jobFolderName));
    job = engine.getJob(jobFolderName);
    if (!job.isLaunchable()){
      job.terminate();
      job.teardown();
    }
    job.checkXML();
    //Before validating the config file, it checks that the xml is well formed
    if (!job.isXmlOk())  {
      throw new AspireException ("con.searchtechnologies.aspire.component.heritrixconnector.InvalidHeritrixConfiguration",
          "Invalid Heritrix job configuration file: %s", jobConfigFile);
    }

    //Validates the configuration of the job (build action from UI)
    job.validateConfiguration();

    if (!job.hasValidApplicationContext()){
      throw new AspireException ("con.searchtechnologies.aspire.component.heritrixconnector.InvalidHeritrixConfiguration",
          "Job configuration error on file: %s", jobConfigFile);
    }

    AspireHeritrixProcessor aspireProcessor = job.getJobContext().getBean("aspireProcessor", AspireHeritrixProcessor.class);

    aspireProcessor.setCleanupRegex(info.getCleanupRegex());
    aspireProcessor.setHeritrixScanner(this);

    return job;
  }

  @Override
  public SourceInfo initializeSourceInfo(AspireObject propertiesXml) throws AspireException {

    if (!Thread.currentThread().getContextClassLoader().equals(CrawlJob.class.getClassLoader())) {
      Thread.currentThread().setContextClassLoader(CrawlJob.class.getClassLoader());
    }
    
    HeritrixSourceInfo info = loadCfgFromJob(propertiesXml);    

    //Check if the custom file exists, if not throws an error before starting the Heritrix engine
    if (!defaultConfigFile && ((StringUtilities.isNotEmpty(configFileLocation) 
        && !new File(getFilePathFromAspireHome(configFileLocation)).exists()) 
        || (StringUtilities.isEmpty(configFileLocation)))){

      throw new AspireException(this,"com.searchtechnologies.aspire.components.heritrixconnector.HeritrixScanner",
          "Custom configuration file doesn't exist or value is empty, configFileLocation: %s", configFileLocation);
    }
    
    this.info = info;

    return info;
  }

  @Override
  public ItemType newItemType() {
    return null;
  }

  @Override
  public void doAdditionalInitialization(Element config) throws AspireException {
    if(config == null) return;

    bh = BranchHandlerFactory.newInstance(config, this);

    heritrixJobsDir = getStringFromConfig(config, "jobsFolder", appDataDir("heritrixJobs"));
    heritrixJobsDir = getFilePathFromAspireHome(heritrixJobsDir);

    sourceDisplayName = getStringFromConfig(config, "displayName", sourceDisplayName);


    configFileLocation = getStringFromConfig(config, "configFileLocation",configFileLocation);

    if (configFileLocation!=null)
      defaultConfigFile=false;

    crawlPatterns = AspireObject.createFromXML(new StringReader(AXML.toString(config))).get("crawlPatterns");


    mapDBDir = getStringFromConfig(config, "jdbmDir", appDataDir("incremental"));

    checkNotCrawlableContent = getBooleanFromConfig(config, "checkNotCrawlableContent", false);

    daysFailedThreshold = getIntegerFromConfig(config, "daysToDelete", 2, 1, Integer.MAX_VALUE);

    maxFailures = getIntegerFromConfig(config, "maxFailuresToDelete", 5, 1, Integer.MAX_VALUE);

    uncrawledAccessDelay = getLongFromConfig(config, "uncrawledAccessDelay", 2000L, 1L, 100000L);

    maxHops = getIntegerFromConfig(config, "maxHops",maxHops, 1, 20);

    maxDelayMs = getLongFromConfig(config, "millisecondsPerRequest", maxDelayMs, 1L, 100000L);

    scope = getStringFromConfig(config,"scope", scope);

    updaterComponentName = getStringFromConfig(config, "updaterComponent", null);
    
    if (StringUtilities.isNotEmpty(updaterComponentName)) {
      info("Using updater job component: %s", updaterComponentName);
    }
    waitForSubJobsTimeout = getLongFromConfig(config, "waitForSubJobsTimeout",
        waitForSubJobsTimeout, 0L, null);

    checkpointIntervalMinutes = getIntegerFromConfig(config, "checkpointIntervalMinutes", 15, 1, Integer.MAX_VALUE);

    deletedUrlsLog = new RotatingFileWriter(this.getFilePathFromAspireHome("log")+"/"+this.getAppName()+"/deleted.jobs");
    failedUrlsLog = new RotatingFileWriter(this.getFilePathFromAspireHome("log")+"/"+this.getAppName()+"/failed.jobs");


    HttpAuthenticationCredential.securityManager = new AspireSecurityManager();

    FileInputStream finp;
    File properties = new File(heritrixJobsDir+"/logging.properties");
    System.setProperty("java.util.logging.config.file", properties.getAbsolutePath());
    
    //Loads the heritrix engine
    engine = new Engine(new File (heritrixJobsDir));

    
    try {
      if (properties.exists()){
        finp = new FileInputStream(properties);
        LogManager.getLogManager().readConfiguration(finp);
      }
    } catch (FileNotFoundException e) {
      error(e, "FileNotFoundException exception whilst reading configuration");
    } catch (SecurityException e) {
      error(e, "SecurityException exception whilst reading configuration");
    } catch (IOException e) {
      error(e, "IO exception whilst reading configuration");
    }
  }

  /**
   * Loads all configuration information into a HeritrixSourceInfo and places it in a HashMap of outstanding sources.
   * @param doc AspireObject to read the data from
   * @return HeritrixSourceInfo with config information for current processing job
   * @throws AspireException
   * @throws IOException
   */
  private HeritrixSourceInfo loadCfgFromJob(AspireObject propertiesXml) throws AspireException {
    HeritrixSourceInfo info = null;

    String inputUrl = null;
    AspireObject crawlPatterns = null;
    int maxHops = -1;
    long maxDelayMs = 3000;
    String scope = null;
    String configFileLocation = null;
    String useDefaultConfigFileValue = null;
    boolean useDefaultConfigFile = true;
    boolean fullCrawl = false;

    String mapDBDir = this.mapDBDir;
    if (mapDBDir == null)
      mapDBDir = appDataDir("incremental");
    
    boolean checkNotCrawlableContent = this.checkNotCrawlableContent;
    int daysFailedThreshold = this.daysFailedThreshold;
    int maxFailures = this.maxFailures;
    long uncrawledAccessDelay = this.uncrawledAccessDelay;
    int retryDelay = 20;
    int maxRetries = 5;
    boolean rejectDefaults = true;

    String cleanupRegex = "";

    if (propertiesXml != null) {
      useDefaultConfigFileValue = propertiesXml.getText("defaultConfigFile",Boolean.toString(this.defaultConfigFile));
      useDefaultConfigFile = Boolean.parseBoolean(useDefaultConfigFileValue);

      if (useDefaultConfigFile){
        inputUrl = propertiesXml.getText("url");
        maxHops = Integer.parseInt(propertiesXml.getText("maxHops", "-1"));
        maxDelayMs = Long.parseLong(propertiesXml.getText("millisecondsPerRequest", "-1"));
        scope = propertiesXml.getText("crawlScope");
        crawlPatterns = propertiesXml.get("crawlPatterns");
        rejectDefaults = Boolean.parseBoolean(propertiesXml.getText("rejectDefaults"));
      } else {
        maxDelayMs = Long.parseLong(propertiesXml.getText("millisecondsPerRequest", "-1"));
        configFileLocation = propertiesXml.getText("configFileLocation", this.configFileLocation);
      }

      String temp = propertiesXml.getText("checkNotCrawlableContent","-1");
      if (temp!=null && !temp.isEmpty() && !"-1".equals(temp))
        checkNotCrawlableContent = Boolean.parseBoolean(temp.toLowerCase());

      temp = propertiesXml.getText("daysToDelete","-1");
      if (temp!=null && !temp.isEmpty() && !"-1".equals(temp))
        daysFailedThreshold = Integer.parseInt(temp);  

      temp = propertiesXml.getText("maxFailuresToDelete","-1");
      if (temp!=null && !temp.isEmpty() && !"-1".equals(temp))
        maxFailures = Integer.parseInt(temp);

      temp = propertiesXml.getText("uncrawledAccessDelay","-1");
      if (temp!=null && !temp.isEmpty() && !"-1".equals(temp))
        uncrawledAccessDelay = Long.parseLong(temp);

      temp = "-1";
      if (propertiesXml.get("seedsRetry")!=null){
        temp = propertiesXml.get("seedsRetry").getAttribute("retryDelay");
        if (temp!=null && !temp.isEmpty() && !"-1".equals(temp))
          retryDelay = Integer.parseInt(temp);

        temp = "-1";
        temp = propertiesXml.get("seedsRetry").getAttribute("maxRetries");
        if (temp!=null && !temp.isEmpty() && !"-1".equals(temp))
          maxRetries = Integer.parseInt(temp);
      }
      
      temp = propertiesXml.getText("cleanupRegex");
      if (temp!=null && !temp.isEmpty()){
        cleanupRegex = temp;
      }
      
      temp = "-1";
      temp = propertiesXml.getText("maxHeritrixThreads", "-1");
      if (temp!=null && !temp.isEmpty() && !"-1".equals(temp)){
        maxHeritrixThreads = Integer.parseInt(temp);
      }
      
      temp = queueAssignmentPolicy = propertiesXml.getText("queueAssignmentPolicy");
      if (temp!=null && !temp.isEmpty()){
        queueAssignmentPolicy = temp;
        
        if (temp.equalsIgnoreCase("HashingQueueAssignmentPolicy"))
        {
          temp = "-1";
          temp = propertiesXml.getText("parallelQueues", "-1");
          if (temp!=null && !temp.isEmpty() && !"-1".equals(temp)){
            parallelQueues = Integer.parseInt(temp);
            parallelQueuesString = String.format("<property xmlns=\"http://www.springframework.org/schema/beans\" name=\"parallelQueues\" value=\"%s\" />", parallelQueues);
          }            
        }
        else
        {
          parallelQueues = -1;
          parallelQueuesString = "";
        }
      }
    }

    if (StringUtilities.isEmpty(useDefaultConfigFileValue)){
      useDefaultConfigFile = this.defaultConfigFile;
    }

    if (useDefaultConfigFile) {
      if (StringUtilities.isEmpty(inputUrl)){
        throw new AspireException(this,"com.searchtechnologies.aspire.components.heritrixconnector.HeritrixScanner",
            "No URL is configured to initialize the crawl");
      }
      if (maxHops==-1){
        maxHops = this.maxHops;
      }
      if (maxDelayMs == -1){
        maxDelayMs = this.maxDelayMs;
      }
      if (StringUtilities.isEmpty(scope)){
        scope = this.scope;
      }
      if (crawlPatterns == null){
        crawlPatterns = this.crawlPatterns;
      }
    } else {
      if (StringUtilities.isEmpty(configFileLocation)){
        configFileLocation = this.configFileLocation;
      }
      configFileLocation = getFilePathFromAspireHome(configFileLocation);
    }

    info = new HeritrixSourceInfo(this);
    info.setFriendlyName(sourceDisplayName);
    info.setUseDefaultConfigFile(useDefaultConfigFile);
    info.setMaxHops(maxHops);
    info.setMillisecondsPerRequest(maxDelayMs);
    info.setScope(scope);
    info.setCrawlRegexPatterns(crawlPatterns);
    info.setRejectDefaults(rejectDefaults);
    info.setConfigFileLocation(configFileLocation);
    info.setFullCrawl(fullCrawl);
    info.setStartCrawlTime(new Date());
    info.setUncrawledAccessDelay(uncrawledAccessDelay);
    info.setCheckpointIntervalMinutes(checkpointIntervalMinutes);
    info.setRetryDelay(retryDelay);
    info.setMaxRetries(maxRetries);
    info.setCleanupRegex(cleanupRegex);
    
    info.setUrlDir(mapDBDir+"/urlDB");
    
    if (this.info != null && 
        ((HeritrixSourceInfo)this.info).getIncrementalDB() != null &&
        !this.info.isTestMode()
        && ((HeritrixSourceInfo)this.info).getUrlDir().equals(info.getUrlDir())) {
      
      info.setIncrementalDB(((HeritrixSourceInfo)this.info).getIncrementalDB());
      
    } else { 
      try {
        info.setIncrementalDB(info.openIncrementalDB());
      } catch (AspireException ae) {
        error(ae, "Error openning NoSQL Connection, using in-memory Incremental Database!, Please check your NoSQL Settings or server");
        info.setIncrementalDB(new HashMap<String, String>());
      }
    }
    
    info.setDaysFailedThreshold(daysFailedThreshold);
    info.setMaxFailures(maxFailures);
    info.setCheckNotCrawlable(checkNotCrawlableContent);
    
    info.setMaxHeritrixThreads(maxHeritrixThreads);
    info.setQueueAssignmentPolicy(queueAssignmentPolicy);
    info.setParallelQueues(parallelQueues);
    info.setParallelQueuesString(parallelQueuesString);

    return info;
  }

  public HeritrixSourceInfo getHeritrixSourceInfo() {
    return (HeritrixSourceInfo) info;
  }

  public void addURL(String uri, long streamSize, String md5, boolean commitDB,
      InputStream is, String contentType, boolean xslt, String parent, String pathFromSeed) throws AspireException {

    HeritrixSourceInfo info = getHeritrixSourceInfo();

    info.incrementItemsCrawled();
    if (!info.canContinue() || info.skipTestItem())
      return;
    
    boolean skipURL = false;
    SourceItem item = new SourceItem(uri);
    item.setContentStream(is);
    
    item.setSourceType(info.getSourceType());
    item.setSourceName(info.getSourceId());
    item.setConnectorSpecificField(Scanner.MD5_TAG, md5);
    item.setConnectorSpecificField("xslt", xslt);
    item.setConnectorSpecificField("discoveredBy", parent);
    item.setConnectorSpecificField("pathFromSeed", pathFromSeed);
    item.setSourceId(info.getContentSourceId());
    
    boolean addOnIncremental = false;

    if (!info.fullCrawl()) {
      DataBaseURLEntry value=null;
      String data = info.getIncrementalDB().get(uri);
      if (data != null){
        value = DataBaseURLEntry.createDataBaseURLEntryFromString(data);
      }
      else {
    	  // The url didn't exist on previous crawl
    	  addOnIncremental = true;
      }
      //if there is and old value and the content is the same, do not reindex the URL
      if (value!= null && value.getMd5Sum().equals(md5)){
        skipURL = true;
      }else{//Reindex as there was no old url or the content is different.
        skipURL = false;
      }
    }
    
    synchronized (this.getClass()){
      info.getIncrementalDB().put(uri, new DataBaseURLEntry(info.getStartCrawlTime(),null,0,md5,new Date()).toString());

      if (commitDB){ //Commits every 100 writes.
        info.commitUrlDB();
      }
    }
    
    if (info.fullCrawl())
      addAction(item,info);
    else if (!skipURL) {
      // The url didn't exist on previous crawl
      if (addOnIncremental)
    	  addAction(item,info);
      else
    	updateAction(item,info);
    } else {
      noChangeAction(item, info);
    }
    
  }
  
  @Override
  public void addAction(SourceItem item, SourceInfo info) throws AspireException {
    if (item.getPatternUrl()!=null && info.getFileFilter().accept(item.getPatternUrl()))
      super.addAction(item, info);
    else 
      logAuditAction(Actions.EXCLUDED, item.getId(), item.getFetchUrl(), null);
  }
  
  @Override
  public void updateAction(SourceItem item, SourceInfo info) throws AspireException {
    if (item.getPatternUrl()!=null && info.getFileFilter().accept(item.getPatternUrl()))
      super.updateAction(item, info);
    else 
      logAuditAction(Actions.EXCLUDED, item.getId(), item.getFetchUrl(), null);
  }

  /**
   * Find all URLs that were not accessed and evaluate which ones should be deleted
   * @param info 
   * @param job
   * @throws AspireException
   * @throws IOException 
   */
  private void deleteAfterCrawl(HeritrixSourceInfo info) throws AspireException, IOException{

    if (info.getTempUncrawledDB()==null){
      info.setTempUncrawledDB(info.openTempDB());
    }

    info.getTempUncrawledDB().clear();
    info.commitUrlDB();


    if (HeritrixSourceInfo.INITIAL_CRAWL_COMPLETE.equals(info.getIncrementalDB().get("||status||").split(",")[0])){

      /* Contains the all the Entries on the database */
      Iterator<Entry<String,String>> iter = info.getIncrementalDB().entrySet().iterator();
      //Writes uncrawled urls to files by its host name
      HashMap<String,BufferedWriter> files = new HashMap<String,BufferedWriter>();
      long commitDB = 0;
      // Scan through ALL URLs inside of JDBM2 (SCAN_UNCRAWLED LOOP)
      while (iter.hasNext() && info.getStatus()!=HeritrixSourceInfo.SCAN_STOPPED){
        Entry<String,String> entry = iter.next();
        String url = entry.getKey();
        String data = entry.getValue();
        DataBaseURLEntry value=null;

        if (!"||status||".equals(url)){
          if (data != null)
            value = DataBaseURLEntry.createDataBaseURLEntryFromString(data);

          long diff = info.getStartCrawlTime().getTime() - value.getLastAccessedTime().getTime();

          /* We only need those that were not accessed on the actual crawl */
          if (value!=null && diff > 0){
            if (url!=null && info.getTempUncrawledDB().get(url)==null){
              info.getTempUncrawledDB().put(url,data);

              commitDB++;
              if (commitDB%25==0){
                info.commitUrlDB();
              }

              //Add it to the respective hostname file
              String hostname = new URL(StringUtilities.safeUrl(url)).getHost();
              if (!files.containsKey(hostname)){
                File file = new File(info.getUrlDir()+"/urlsToDelete_"+hostname+".urls");
                file.getParentFile().mkdirs();
                if (file.exists()){
                  file.delete();
                }
                files.put(hostname, new BufferedWriter(new FileWriter(file)));
              }
              files.get(hostname).write(url+" "+entry.getValue()+"\n");

            }
          }
        }
        if (info.getStatus()==HeritrixSourceInfo.SCAN_PAUSED){
          info.commitUrlDB();
        }
        while (info.getStatus()==HeritrixSourceInfo.SCAN_PAUSED);
      }
      info.getIncrementalDB().put("||status||", HeritrixSourceInfo.TEMP_UNCRAWLED_DB_CREATED+","+info.getStartCrawlTime().getTime());
      info.commitUrlDB();
      for (BufferedWriter bw : files.values()){
        bw.flush();
        bw.close();
      }

      //Fill the hashmap of hostnames-Status
      try {
        for (String hostname : files.keySet())
          scanUncrawledUrls(info,hostname);
      } catch (IOException ioe) {
        error(ioe,"Error scanning uncrawled urls file");
        info.setScannerErrorMessage(ioe,"Error scanning uncrawled urls file");
        throw new AspireException("com.searchtechnologies.aspire.components.heritrixconnector.HeritrixScanner",ioe,"Error scanning uncrawled urls file");
      }

      info.getPriorityQueueChecker().start(info.getScanJob(), this);

      long lastChange = new Date().getTime();
      int lastCount = info.getTempUncrawledDB().size();
      while (info.getPriorityQueueChecker().isRunning()){
        try {
          Thread.sleep(500);
          if (new Date().getTime() - lastChange >= 2000){
            try {
              for (String hostname : files.keySet())
                scanUncrawledUrls(info,hostname);
            } catch (IOException ioe) {
              error(ioe,"Error scanning uncrawled urls file");
              info.setScannerErrorMessage(ioe,"Error scanning uncrawled urls file");
              throw new AspireException("com.searchtechnologies.aspire.components.heritrixconnector.HeritrixScanner",ioe,"Error scanning uncrawled urls file");
            }
          }
          if (lastCount!=info.getTempUncrawledDB().size()){
            lastChange = new Date().getTime();
          }
        } catch (InterruptedException e) {}
      }



    }

    if (info.getStatus()==HeritrixSourceInfo.SCAN_PAUSED){
      info.commitUrlDB();
    }else if (info.getStatus()==HeritrixSourceInfo.SCAN_STOPPED){
      info.getTempUncrawledDB().clear();
      info.commitUrlDB();
    }else if (HeritrixSourceInfo.TEMP_UNCRAWLED_DB_CREATED.equals(info.getIncrementalDB().get("||status||").split(",")[0])){
      info.commitUrlDB();
    }



  }

  protected void scanUncrawledUrls(HeritrixSourceInfo info, String hostname) throws IOException{
    deleteFinished = false;
    BufferedReader br = new BufferedReader(new FileReader(new File(info.getUrlDir()+"/urlsToDelete_"+hostname+".urls")));
    String line = br.readLine();
    long count = 0;
    if (!info.getHostHashMap().containsKey(hostname)) {
      info.getHostHashMap().put(hostname, new HostFetchStatus());
      if (!info.getCheckNotCrawlable()) {
        info.getHostHashMap().get(hostname).setSize(-1);
      }
    }

    //Skips the urls that are already processed
    while (line != null && count<info.getHostHashMap().get(hostname).getTotalUrlsFetched()) {
      line = br.readLine();
      count++;
    }

    if (line==null){
      br.close();
      return;
    }
    //Send job to priority queue
    String[] data = line.split(" ");
    DataBaseURLEntry entry = DataBaseURLEntry.createDataBaseURLEntryFromString(data[1]);
    entry.setUrl(data[0]);
    //Should be sent right away by the background thread
    entry.setTimeToSubmit(new Date().getTime()-1);
    if (info.getTempUncrawledDB().containsKey(entry.getUrl())  && !info.getPriorityQueue().contains(entry)) {
      synchronized(info.getPriorityQueue()){
        info.getPriorityQueue().add(entry);
        info.getHostHashMap().get(hostname).addUrlToFetch(entry);
      }
    }

    while(line!=null){
      line = br.readLine();
      if (line!=null){
        data = line.split(" ");
        entry = DataBaseURLEntry.createDataBaseURLEntryFromString(data[1]);
        entry.setUrl(data[0]);
        if (!info.getCheckNotCrawlable()){
          entry.setTimeToSubmit(new Date().getTime()-1);
          if (info.getTempUncrawledDB().containsKey(entry.getUrl()) && !info.getPriorityQueue().contains(entry)) {
            synchronized(info.getPriorityQueue()){
              if (!info.getHostHashMap().get(hostname).addUrlToFetch(entry))
                break;
              info.getPriorityQueue().add(entry);
            }
          }
        }else{
          synchronized (info.getPriorityQueue()){
            if (!info.getHostHashMap().get(hostname).addUrlToFetch(entry))
              break;
          }
        }

      }
    }
    br.close();
  }

  public void reportUpdate(String url) throws AspireException {
    //Log action on the audit file
    logAuditAction(AuditConstants.Actions.UPDATE, url, url, null, getName());
    info.incrementDocsUpdated();
  }
  
  public void reportDelete(String url) throws AspireException {
    //Log action on the audit file
    logAuditAction(AuditConstants.Actions.DELETE, url, url, null, getName());
    info.incrementDocsDeleted();
  }
  
  public void reportNoChange(String url) throws AspireException {
    //Log action on the audit file
    logAuditAction(AuditConstants.Actions.NOCHANGE, url, url, null, getName());
  }
  

  /*
   * (non-Javadoc)
   * @see com.searchtechnologies.aspire.framework.ComponentImpl#getStatus()
   */
  @Override
  public AspireObject getStatus() throws AspireException{

    HeritrixSourceInfo info = (HeritrixSourceInfo) this.info;

    AspireObject status = addDerivedStatus(STAGE_ID, super.getStatus());

    if (info != null) {
      CrawlJob job = info.getCrawlJob();
      if (job!=null){
        status.push("uriTotalsReportData");
        status.setAttribute("defaultConfig", Boolean.toString(info.useDefaultConfigFile()));
        if (info.useDefaultConfigFile()){
          status.setAttribute("url", info.getStartUrl());
        } else {
          status.setAttribute("configFile", info.getConfigFileLocation());
        }
        if (job!=null & job.uriTotalsReportData()!=null)
          for (Entry<String, Long> entry2 : job.uriTotalsReportData().entrySet()){
            if (!entry2.getKey().equalsIgnoreCase("futureuricount")){
              status.add(entry2.getKey(), entry2.getValue());
            }
          }
        status.pop(); // uriTotalsReportData
        status.add("uriTotalReport", job.uriTotalsReport());
        status.add("frontierReport", job.frontierReport());
        status.add("elapsedReport", job.elapsedReport());
      }

      status.push("contentSourcesDB");
  
      if (info.getIncrementalDB()!=null){
        status.push("database");
        //status.setAttribute("id", ""+info.getDatabaseId());
        status.add("friendlyName",info.getFriendlyName());
        status.add("urlAdded",info.getDocsAdded());
        status.add("urlUpdated",info.getDocsUpdated());
        status.add("revisitRate",(info.getDocsUpdated()+0.0)/((info.getIncrementalDB().size()-1)+info.getDocsDeleted()+0.0));
        status.add("urlDeleted",info.getDocsDeleted());
        status.add("size", info.getIncrementalDB().size()-1);
        status.add("directory",info.getUrlDir());
  
        HashMap<String,MutableInt> hostCount = new HashMap<String,MutableInt>(); 
        HashMap<String,MutableInt> lastHostCount = new HashMap<String,MutableInt>();
        long now = new Date().getTime();
        for (String url : info.getIncrementalDB().keySet()){
          String hostname = null;
          if (url.equals("||status||"))
            continue;
          try {
            hostname = new URL(StringUtilities.safeUrl(url)).getHost();
          } catch (MalformedURLException e) {
            throw new AspireException("com.searchtechnologies.aspire.components.heritrixconnector.HeritrixScanner",e,"Error getting hostname for url: %s",url);
          }
          DataBaseURLEntry data =  DataBaseURLEntry.createDataBaseURLEntryFromString(info.getIncrementalDB().get(url));
          if (hostCount.containsKey(hostname)){
            hostCount.get(hostname).increment();
          }else{
            hostCount.put(hostname, new MutableInt(1));
          }
  
          if ((now - data.getTimestamp().getTime()) <= 300000){ //last 5 minutes
            if (lastHostCount.containsKey(hostname)){
              lastHostCount.get(hostname).increment();
            }else{
              lastHostCount.put(hostname, new MutableInt(1));
            } 
          }
        }
  
        ValueComparator comparator = new ValueComparator(hostCount);
        TreeMap<String,MutableInt> sorted_map = new TreeMap<String,MutableInt>(comparator);
  
        sorted_map.putAll(hostCount);
  
        status.push("hostnames");
        for (String hostname : sorted_map.keySet()){
          status.push("hostname");
          status.setAttribute("name", hostname);
          status.add("total", hostCount.get(hostname).toString());
          status.pop(); // hostname
        }
        status.pop(); // hostnames
  
        comparator = new ValueComparator(lastHostCount);
        sorted_map = new TreeMap<String,MutableInt>(comparator);
  
        sorted_map.putAll(lastHostCount);
  
        status.push("lastHostnames");
        for (String hostname : sorted_map.keySet()){
          status.push("hostname");
          status.setAttribute("name", hostname);
          status.add("total", lastHostCount.get(hostname).toString());
          status.pop(); // hostname
        }
        status.pop(); // lastHostnames
  
        status.pop();
      }
    }

    status.popAll();
    return status;
  }

  /**
   * Calculate the MD5 sum for the content of a given InputStream
   * @param is InputStream of the content to calculate
   * @return A String representation of the MD5 sum
   * @throws NoSuchAlgorithmException
   * @throws IOException
   */
  protected static String computeMD5(InputStream is) throws NoSuchAlgorithmException, IOException{
    return computeMD5(is,new MutableInt(0));
  }

  /**
   * Calculate the MD5 sum for the content of a given InputStream
   * @param contentSize the content size
   * @param is InputStream of the content to calculate
   * @return A String representation of the MD5 sum
   * @throws NoSuchAlgorithmException
   * @throws IOException
   */
  protected static String computeMD5(InputStream is, MutableInt contentSize) throws NoSuchAlgorithmException, IOException{
    try{
      MessageDigest digest = null;
      digest = MessageDigest.getInstance("MD5");

      byte[] buffer = new byte[1024];
      int len;

      // Read the input stream and update the digest 
      while ((len = is.read(buffer)) > -1 ) {
        digest.update(buffer);
        contentSize.add(len);
      }


      // Convert the message digest into a string representation 
      BigInteger bigInt = new BigInteger(1, digest.digest());
      return bigInt.toString(16);
    }finally{
      if (is!=null){
        is.close();
      }
    }
  }

  /**
   * Receive an url, open its connection and inputstream
   * @param urlParam
   * @return InputStream for the contents of the URL
   * @throws IOException if occurs a problem with the connection or the responseCode is not 200 
   * @throws AspireException for any MalformedURLException
   */
  protected static InputStream openURLInputStream(String urlParam, boolean successResponse) throws IOException, AspireException {

    HttpURLConnection urlConn = null;
    URL url = null;

    try {
      url = new URL(StringUtilities.safeUrl(urlParam));
    } catch (MalformedURLException e) {
      throw new AspireException("com.searchtechnologies.aspire.components.heritrixconnector.HeritrixScanner",e,"The URL \"%s\" is reported as being malformed by the java URL parsing utilities.", urlParam);
    }

    urlConn = (HttpURLConnection)url.openConnection();
    urlConn.setRequestProperty("User-Agent", "Heritrix Crawler connector for Aspire");
    //urlConn.setRequestProperty("Accept" ,"text/html, application/xml;q=0.9, application/xhtml+xml, image/png, image/jpeg, image/gif, image/x-xbitmap, *\/*;q=0.1");

    if (successResponse && urlConn.getResponseCode()!=200){
      throw new IOException("Response from URL: "+urlParam+" was not successful: "+urlConn.getResponseMessage());
    }
    InputStream is = urlConn.getInputStream();

    return is;

  }

  /*
   * (non-Javadoc)
   * @see com.searchtechnologies.aspire.services.JobEventHandler#processJobEvent(com.searchtechnologies.aspire.services.JobEvent)
   */
  @Override
  public void processEvent(ProcessingEvent pe) throws AspireException {
    if (pe.getEventType() == BatchEvent.BATCH_ERROR_EVENT || 
        pe.getEventType() == BatchEvent.BATCH_SUCCESS_EVENT) {
      // Handle batch events and return
      super.processEvent(pe);
      return;
    }
    
    JobEvent event = (JobEvent) pe;
    Job j = event.getJob();
    AspireObject jobData=j.get();
    HeritrixSourceInfo info = (HeritrixSourceInfo)this.info;

    // Get the URL, get the hostname from the URL
    // Lookup the hostname in the hashMap, get the next URL in the list, send it down the pipeline
    // increment total Urls fetched (if equal to total URLs to fetch, then don't re-scan the database)
    // If the list is empty, re-scan the entire uncrawled database to find more URLs for the host, fill up the list again, and then submit the next one


    String action = jobData.getText("action");
    String url = jobData.getText(Standards.Basic.FETCH_URL_TAG);

    if ("true".equals(jobData.getText("uncrawled"))){
      DataBaseURLEntry dbEntry = DataBaseURLEntry.createDataBaseURLEntryFromString(info.getIncrementalDB().get(url));
      dbEntry.setUrl(url);


      String hostname = null;
      try {
        hostname = new URL(StringUtilities.safeUrl(url)).getHost();
      }
      catch (MalformedURLException e1) {
        error(e1,"Malformed URL: %s",url);
        info.setScannerErrorMessage(e1,"Malformed URL: %s",url);
        new AspireException("com.searchtechnologies.aspire.components.heritrixconnector.HeritrixScanner",e1,"Malformed URL: %s",url);
      }

     if ("delete".equals(action)){

        if (dbEntry!=null){
          dbEntry.incrementFailCount();

          // Set dateFirstFailed if this is the first time.
          if (dbEntry.getDateFirstFailed()==null)
            dbEntry.setDateFirstFailed(info.getStartCrawlTime());

          //Remove from JDBM2
          info.getIncrementalDB().remove(url);

          //Add to "deleted URLs" log file
          deletedUrlsLog.writeToFile("DELETED: %s LastAccessedTime: %s DateFirstFailed: %s Fail-Count: %d MD5Sum: %s",
              url,DateTimeUtilities.getISO8601DateTime(dbEntry.getLastAccessedTime()),
              DateTimeUtilities.getISO8601DateTime(dbEntry.getDateFirstFailed()),dbEntry.getFailCount(),dbEntry.getMd5Sum());

        }
        super.processEvent(pe);
      }
     else if ("failed".equals(action) || 
          ("update".equals(action) && event.getEventType()==JobEvent.UNHANDLED_ERROR_EVENT)){

        //Update failed count in JDBM2
        dbEntry.incrementFailCount();
        
       //Set dateFirstFailed if this is the first time.
        if (dbEntry.getDateFirstFailed()==null)
          dbEntry.setDateFirstFailed(info.getStartCrawlTime());
        
        info.getIncrementalDB().put(url, dbEntry.toString());

        //Add to "failed URLs" log file
        failedUrlsLog.writeToFile("FAILED: %s LastAccessedTime: %s DateFirstFailed: %s Fail-Count: %d MD5Sum: %s",
            url,DateTimeUtilities.getISO8601DateTime(dbEntry.getLastAccessedTime()),
            DateTimeUtilities.getISO8601DateTime(dbEntry.getDateFirstFailed()),dbEntry.getFailCount(),dbEntry.getMd5Sum());
        if (("update".equals(action) && event.getEventType()==JobEvent.UNHANDLED_ERROR_EVENT)){
          info.setDocumentErrorMessage(event.getJobId(), "There was an error trying to update the url: %s", url);
        }
      }
      else if (Scanner.Action.update.toString().equals(action)){

        //Reset the timestamp and failed count on the job, update the MD5 signature
        dbEntry.setFailCount(0);
        dbEntry.setDateFirstFailed(null);
        dbEntry.setMd5Sum(jobData.getText("md5"));

        info.getIncrementalDB().put(url, dbEntry.toString());
        info.incrementDocsUpdated();
        
        super.processEvent(pe);
      }
      else if ("found-no-change".equals(action)){
        //Reset the timestamp and failed count on the job
        dbEntry.setFailCount(0);
        dbEntry.setDateFirstFailed(null);
        dbEntry.setMd5Sum(jobData.getText("md5"));

        info.getIncrementalDB().put(url, dbEntry.toString());
      }
      else{
        super.processEvent(pe);
      }

      if (hostname!=null && info.getHostHashMap().get(hostname)!=null){
        info.getHostHashMap().get(hostname).getUrlsToFetch().remove(dbEntry);
        info.getHostHashMap().get(hostname).incrementTotalUrlsFetched();
      }

      if (info.getHostHashMap().get(hostname).getUrlsToFetch().size()==0){
        try {
          scanUncrawledUrls(info, hostname);
          if (info.getHostHashMap().get(hostname).getUrlsToFetch().size()==0) { 
            deleteFinished = true;
            info.updateJobCompletedStatistic();
          }
        }
        catch (IOException e) {
          error(e,"Error trying to scan hostname: "+hostname);
          info.setScannerErrorMessage(e,"Error trying to scan hostname: "+hostname);
          throw new AspireException("com.searchtechnologies.aspire.components.heritrixconnector.HeritrixScanner",e,"Error trying to scan hostname: "+hostname);
        }
      }
      else{
        if (info.getCheckNotCrawlable()){
          DataBaseURLEntry nextUrl;
          synchronized (info.getPriorityQueue()){
            //Get next url entry
            nextUrl = info.getHostHashMap().get(hostname).getUrlsToFetch().get(0);

            //Send to submit at 2 seconds from now

            nextUrl.setTimeToSubmit(new Date().getTime()+info.getUncrawledAccessDelay());

            info.getPriorityQueue().add(nextUrl);  
          }
        }
      }
    } else {
      super.processEvent(pe);
    }
  }

  /*
   * (non-Javadoc)
   * @see com.searchtechnologies.aspire.scanner.AbstractScanner#testConnection(com.searchtechnologies.aspire.services.Job, com.searchtechnologies.aspire.services.AspireObject)
   */
  @Override
  public boolean testConnection(Job job, AspireObject result) throws AspireException{
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean downloadSpecialAcls(SourceInfo si, SpecialAclStore specialAcls) throws AspireException {
    // Nothing to do
    return false;
  }

  @Override
  public boolean canAccessSpecialAcl(byte[] specialAcl, UoG uog, Collection<UoG> grps) {
    // Nothing to do
    return false;
  }

  @Override
  public boolean downloadUsersAndGroups(SourceInfo si, UserGroupCache userGroupMap, Collection<UoG> externalUserGroupList)
      throws AspireException {
    // Nothing to do
    return false;
  }
}


class ValueComparator implements Comparator<String> {

  Map<String, MutableInt> base;
  public ValueComparator(Map<String, MutableInt> base) {
    this.base = base;
  }

  // Note: this comparator imposes orderings that are inconsistent with equals.    
  public int compare(String a, String b) {
    if (base.get(a).intValue() > base.get(b).intValue()) {
      return -1;
    } else {
      return 1;
    } // returning 0 would merge keys
  }
}


