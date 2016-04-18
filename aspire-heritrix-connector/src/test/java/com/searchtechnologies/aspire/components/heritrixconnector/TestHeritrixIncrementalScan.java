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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.DefaultHandler;
import org.mortbay.jetty.handler.HandlerList;
import org.mortbay.jetty.handler.ResourceHandler;

import com.searchtechnologies.aspire.application.AspireActivator;
import com.searchtechnologies.aspire.ctb.ComponentTestBench;
import com.searchtechnologies.aspire.framework.JobFactory;
import com.searchtechnologies.aspire.framework.Standards;
import com.searchtechnologies.aspire.framework.Standards.Scheduler;
import com.searchtechnologies.aspire.framework.utilities.DateTimeUtilities;
import com.searchtechnologies.aspire.framework.utilities.FileUtilities;
import com.searchtechnologies.aspire.services.AspireApplication;
import com.searchtechnologies.aspire.services.AspireObject;
import com.searchtechnologies.aspire.services.Job;
import com.searchtechnologies.aspire.services.ServiceUtils;
import com.searchtechnologies.aspire.test.UnitTestHelper;

import junit.framework.TestCase;
import mockit.Mock;
import mockit.MockUp;

/**
 * Test incremental scan
 * @author aaguilar
 *
 */
public class TestHeritrixIncrementalScan extends TestCase {
  UnitTestHelper uth = new UnitTestHelper(this.getClass());
  ComponentTestBench ctb = new ComponentTestBench();
  HeritrixScanner heritrixScanner = null;
  protected Server server;
  String baseUrl = "http://localhost:8888";
  HashMap<String, String> incMap = new HashMap<String, String>();
  HashMap<String, String> failedMap = new HashMap<String, String>();
  
  protected void setUp() throws Exception {
    super.setUp();
    new MockHeritrixSourceInfo();
    FileUtilities.delete(uth.getSourceFile("testHeritrixIncrementalScan/data"));
    FileUtilities.delete(uth.getSourceFile("testHeritrixIncrementalScan/heritrixJobs"));
    startServlet();
  }

  
  protected void tearDown() throws Exception {
    super.tearDown();
    stopServlet();
    ctb.shutdown();
    heritrixScanner.getHeritrixSourceInfo().close();
  }

  
  public void testIncremental() throws Exception {
    uth.createResultDir("testHeritrixIncrementalScan/webPages");
    
    UnitTestHelper.copyFile(uth.getSourceFilePath("testHeritrixIncrementalScan/webPages/index2.html"), 
        uth.getResultFilePath("testHeritrixIncrementalScan/webPages/index.html"));
    UnitTestHelper.copyFile(uth.getSourceFilePath("testHeritrixIncrementalScan/webPages/linkA.html"), 
        uth.getResultFilePath("testHeritrixIncrementalScan/webPages/linkA.html"));
    UnitTestHelper.copyFile(uth.getSourceFilePath("testHeritrixIncrementalScan/webPages/linkB.html"), 
        uth.getResultFilePath("testHeritrixIncrementalScan/webPages/linkB.html"));
    
    ctb.addComponentFactory(uth.getSourceFilePath("testHeritrixIncrementalScan/ComponentFactory.xml"));
    ctb.launchApplicationTestBench(uth.getSourceFilePath("testHeritrixIncrementalScan"), 
        uth.getSourceFilePath("testHeritrixIncrementalScan/settings.xml"), new AspireActivator());

    ctb.waitForHttp();

    // Get a reference to the scanner
    heritrixScanner = (HeritrixScanner) ServiceUtils.waitForComponent(ctb.getBundleContext(), AspireApplication.OSGI_TIMEOUT, "/TestHeritrixScanner/Main/Scanner");
    assertNotNull(heritrixScanner);
    
    AspireObject data = new AspireObject(Standards.Basic.DOCUMENT_ROOT);
    data.setAttribute(Scheduler.ACTION_ATTR, Scheduler.START_ACTION);
    data.setAttribute(Scheduler.PROPERTIES_ATTR, Scheduler.FULL_CRAWL_REQUEST);
    data.setAttribute("crawlId", "1");
    data.add("id", "1");
    data.add("displayName", "HeritrixCrawl");    AspireObject connectorSource = new AspireObject("connectorSource");
    connectorSource.add("daysToDelete","2");
    connectorSource.add("maxFailuresToDelete","2");
    connectorSource.add("checkNotCrawlableContent","true");
    data.add(connectorSource);
    data.setAttribute(Standards.Scanner.NORMALIZED_CS_NAME, "TestHeritrixScanner");
    Job j = JobFactory.newInstance(data);
    j.putVariable("addUpdateCount", new AtomicInteger(0));
    j.putVariable("deleteCount", new AtomicInteger(0));
    
    heritrixScanner.process(j);
    
    Map<String,String> urlDB = heritrixScanner.getHeritrixSourceInfo().getIncrementalDB();
    System.out.println("---------- The database contains "+urlDB.size()+" entries. ----------");
    for (String url : urlDB.keySet()){
      if (!"||status||".equals(url)){
        DataBaseURLEntry entry = DataBaseURLEntry.createDataBaseURLEntryFromString(urlDB.get(url));
        assertEquals(0,entry.getFailCount());
        assertNull("Date First Failed for "+url+" should be null",entry.getDateFirstFailed());
        assertNotNull(entry.getLastAccessedTime());
        System.out.println("     "+url+": "+entry.getMd5Sum());
      }
    }
    
    assertEquals(4,urlDB.size());
    
    UnitTestHelper.copyFile(uth.getSourceFilePath("testHeritrixIncrementalScan/webPages/index.html"), 
        uth.getResultFilePath("testHeritrixIncrementalScan/webPages/index.html"));
    UnitTestHelper.copyFile(uth.getSourceFilePath("testHeritrixIncrementalScan/webPages/linkC.html"), 
        uth.getResultFilePath("testHeritrixIncrementalScan/webPages/linkC.html"));
    UnitTestHelper.copyFile(uth.getSourceFilePath("testHeritrixIncrementalScan/webPages/other.html"), 
        uth.getResultFilePath("testHeritrixIncrementalScan/webPages/other.html"));
    
    heritrixScanner.getHeritrixSourceInfo().close();
    data = new AspireObject(Standards.Basic.DOCUMENT_ROOT);
    data.setAttribute(Scheduler.ACTION_ATTR, Scheduler.START_ACTION);
    data.setAttribute(Scheduler.PROPERTIES_ATTR, Scheduler.INCREMENTAL_CRAWL_REQUEST);
    data.setAttribute("crawlId", "1");
    data.add("id", "1");
    data.add("displayName", "HeritrixCrawl");
    data.setAttribute(Standards.Scanner.NORMALIZED_CS_NAME, "TestHeritrixScanner");
    data.add(connectorSource);
    j = JobFactory.newInstance(data);
    j.putVariable("addUpdateCount", new AtomicInteger(0));
    j.putVariable("deleteCount", new AtomicInteger(0));
    
    heritrixScanner.process(j);
    urlDB = heritrixScanner.getHeritrixSourceInfo().getIncrementalDB();
    System.out.println("---------- (incremental) The database contains "+urlDB.size()+" entries. ----------");
    for (String url : urlDB.keySet()){
      if (!"||status||".equals(url)){
        DataBaseURLEntry entry = DataBaseURLEntry.createDataBaseURLEntryFromString(urlDB.get(url));
        assertEquals(0,entry.getFailCount());
        assertNull("Date First Failed for "+url+" should be null",entry.getDateFirstFailed());
        assertNotNull(entry.getLastAccessedTime());
        System.out.println("     "+url+": "+entry.getMd5Sum());
      }
    }
    assertEquals(6,urlDB.size());
    
    // -------- DELETION TEST ---------
    UnitTestHelper.copyFile(uth.getSourceFilePath("testHeritrixIncrementalScan/webPages/index2.html"), 
        uth.getResultFilePath("testHeritrixIncrementalScan/webPages/index.html"));
    
    heritrixScanner.getHeritrixSourceInfo().close();
    data = new AspireObject(Standards.Basic.DOCUMENT_ROOT);
    data.setAttribute(Scheduler.ACTION_ATTR, Scheduler.START_ACTION);
    data.setAttribute(Scheduler.PROPERTIES_ATTR, Scheduler.INCREMENTAL_CRAWL_REQUEST);
    data.setAttribute("crawlId", "1");
    data.add("id", "1");
    data.add("displayName", "HeritrixCrawl");
    data.setAttribute(Standards.Scanner.NORMALIZED_CS_NAME, "TestHeritrixScanner");
    data.add(connectorSource);
    j = JobFactory.newInstance(data);
    j.putVariable("addUpdateCount", new AtomicInteger(0));
    j.putVariable("deleteCount", new AtomicInteger(0));
    
    heritrixScanner.process(j);
    urlDB = heritrixScanner.getHeritrixSourceInfo().getIncrementalDB();
    System.out.println("---------- (incremental, delete) The database contains "+urlDB.size()+" entries. ----------");
    for (String url : urlDB.keySet()){
      if (!"||status||".equals(url)){
        DataBaseURLEntry entry = DataBaseURLEntry.createDataBaseURLEntryFromString(urlDB.get(url));
        assertEquals(0,entry.getFailCount());
        assertNull("Date First Failed for "+url+" should be null",entry.getDateFirstFailed());
        assertNotNull(entry.getLastAccessedTime());
        System.out.println("     "+url+": "+entry.getMd5Sum()+" fc: "+entry.getFailCount()+" lat:"+DateTimeUtilities.getISO8601DateTime(entry.getLastAccessedTime())+" dff:"+DateTimeUtilities.getISO8601DateTime(entry.getDateFirstFailed()));
      }
    }
    
    assertEquals(6,urlDB.size());

    FileUtilities.delete(uth.getResultFilePath("testHeritrixIncrementalScan/webPages/linkC.html"));
    
    heritrixScanner.getHeritrixSourceInfo().close();
    data = new AspireObject(Standards.Basic.DOCUMENT_ROOT);
    data.setAttribute(Scheduler.ACTION_ATTR, Scheduler.START_ACTION);
    data.setAttribute(Scheduler.PROPERTIES_ATTR, Scheduler.INCREMENTAL_CRAWL_REQUEST);
    data.setAttribute("crawlId", "1");
    data.add("id", "1");
    data.add("displayName", "HeritrixCrawl");
    data.add(connectorSource);
    data.setAttribute(Standards.Scanner.NORMALIZED_CS_NAME, "TestHeritrixScanner");
    j = JobFactory.newInstance(data);
    j.putVariable("addUpdateCount", new AtomicInteger(0));
    j.putVariable("deleteCount", new AtomicInteger(0));
    
    heritrixScanner.process(j);
    urlDB = heritrixScanner.getHeritrixSourceInfo().getIncrementalDB();
    System.out.println("---------- (incremental, delete) The database contains "+urlDB.size()+" entries. ----------");
    for (String url : urlDB.keySet()){
      if (!"||status||".equals(url)){
        DataBaseURLEntry entry = DataBaseURLEntry.createDataBaseURLEntryFromString(urlDB.get(url));
        System.out.println("     "+url+": "+entry.getMd5Sum()+" fc: "+entry.getFailCount()+" lat:"+DateTimeUtilities.getISO8601DateTime(entry.getLastAccessedTime())+" dff:"+DateTimeUtilities.getISO8601DateTime(entry.getDateFirstFailed()));
      }
    }
    DataBaseURLEntry entry = DataBaseURLEntry.createDataBaseURLEntryFromString(urlDB.get(baseUrl+"/linkC.html")); 
    
    assertNotNull("DateFirstFailed should be not null and it is null",entry.getDateFirstFailed());
    assertEquals(1,entry.getFailCount());
    assertEquals(6,urlDB.size());
    
    heritrixScanner.getHeritrixSourceInfo().close();
    data = new AspireObject(Standards.Basic.DOCUMENT_ROOT);
    data.setAttribute(Scheduler.ACTION_ATTR, Scheduler.START_ACTION);
    data.setAttribute(Scheduler.PROPERTIES_ATTR, Scheduler.INCREMENTAL_CRAWL_REQUEST);
    data.setAttribute("crawlId", "1");
    data.add("id", "1");
    data.add("displayName", "HeritrixCrawl");
    data.add(connectorSource);
    data.setAttribute(Standards.Scanner.NORMALIZED_CS_NAME, "TestHeritrixScanner");
    j = JobFactory.newInstance(data);
    j.putVariable("addUpdateCount", new AtomicInteger(0));
    j.putVariable("deleteCount", new AtomicInteger(0));
    
    heritrixScanner.process(j);

    urlDB = heritrixScanner.getHeritrixSourceInfo().getIncrementalDB();
    System.out.println("---------- (incremental, delete) The database contains "+urlDB.size()+" entries. ----------");
    for (String url : urlDB.keySet()){
      if (!"||status||".equals(url)){
        entry = DataBaseURLEntry.createDataBaseURLEntryFromString(urlDB.get(url));
        System.out.println("     "+url+": "+entry.getMd5Sum()+" fc: "+entry.getFailCount()+" lat:"+DateTimeUtilities.getISO8601DateTime(entry.getLastAccessedTime())+" dff:"+DateTimeUtilities.getISO8601DateTime(entry.getDateFirstFailed()));
        assertEquals(0,entry.getFailCount());
        assertNull("Date First Failed for "+url+" should be null",entry.getDateFirstFailed());
        assertNotNull(entry.getLastAccessedTime());
      }
    }
    
    assertEquals(5,urlDB.size());
    
    heritrixScanner.getHeritrixSourceInfo().close();
    
    FileUtilities.delete(uth.getResultFilePath("testHeritrixIncrementalScan/webPages"));
  }

  
  public void testCheckCrawlableOnly() throws Exception{
    
    uth.createResultDir("testHeritrixIncrementalScan/webPages");
    UnitTestHelper.copyFile(uth.getSourceFilePath("testHeritrixIncrementalScan/webPages/index.html"), 
        uth.getResultFilePath("testHeritrixIncrementalScan/webPages/index.html"));
    UnitTestHelper.copyFile(uth.getSourceFilePath("testHeritrixIncrementalScan/webPages/linkA.html"), 
        uth.getResultFilePath("testHeritrixIncrementalScan/webPages/linkA.html"));
    UnitTestHelper.copyFile(uth.getSourceFilePath("testHeritrixIncrementalScan/webPages/linkB.html"), 
        uth.getResultFilePath("testHeritrixIncrementalScan/webPages/linkB.html"));
    UnitTestHelper.copyFile(uth.getSourceFilePath("testHeritrixIncrementalScan/webPages/linkC.html"), 
        uth.getResultFilePath("testHeritrixIncrementalScan/webPages/linkC.html"));
    UnitTestHelper.copyFile(uth.getSourceFilePath("testHeritrixIncrementalScan/webPages/other.html"), 
        uth.getResultFilePath("testHeritrixIncrementalScan/webPages/other.html"));
    
    ctb.addComponentFactory(uth.getSourceFilePath("testHeritrixIncrementalScan/ComponentFactory.xml"));
    ctb.launchApplicationTestBench(uth.getSourceFilePath("testHeritrixIncrementalScan"), 
        uth.getSourceFilePath("testHeritrixIncrementalScan/settingsCheckCrawlableOnly.xml"), new AspireActivator());

    ctb.waitForHttp();

    // Get a reference to the scanner
    heritrixScanner = (HeritrixScanner) ServiceUtils.waitForComponent(ctb.getBundleContext(), AspireApplication.OSGI_TIMEOUT, "/TestHeritrixScanner/Main/Scanner");
    assertNotNull(heritrixScanner);
    
    AspireObject data = new AspireObject(Standards.Basic.DOCUMENT_ROOT);
    data.setAttribute(Scheduler.ACTION_ATTR, Scheduler.START_ACTION);
    data.setAttribute(Scheduler.PROPERTIES_ATTR, Scheduler.FULL_CRAWL_REQUEST);
    data.setAttribute("crawlId", "1");
    data.add("id", "1");
    data.add("displayName", "HeritrixCrawl");
    data.setAttribute(Standards.Scanner.NORMALIZED_CS_NAME, "TestHeritrixScanner");
    Job j = JobFactory.newInstance(data);
    j.putVariable("addUpdateCount", new AtomicInteger(0));
    j.putVariable("deleteCount", new AtomicInteger(0));
    
    heritrixScanner.process(j);
    Map<String,String> urlDB = heritrixScanner.getHeritrixSourceInfo().getIncrementalDB();
    System.out.println("---------- The database contains "+urlDB.size()+" entries. ----------");
    for (String url : urlDB.keySet()){
      if (!"||status||".equals(url)){
        DataBaseURLEntry entry = DataBaseURLEntry.createDataBaseURLEntryFromString(urlDB.get(url));
        assertEquals(0,entry.getFailCount());
        assertNull("Date First Failed for "+url+" should be null",entry.getDateFirstFailed());
        assertNotNull(entry.getLastAccessedTime());
        System.out.println("     "+url+": "+entry.getMd5Sum()+" fc: "+entry.getFailCount()+" lat:"+DateTimeUtilities.getISO8601DateTime(entry.getLastAccessedTime())+" dff:"+DateTimeUtilities.getISO8601DateTime(entry.getDateFirstFailed()));
      }
    }
    
    assertEquals(6,urlDB.size());
    
    
    // -------- DELETION TEST ---------
    UnitTestHelper.copyFile(uth.getSourceFilePath("testHeritrixIncrementalScan/webPages/index2.html"), 
        uth.getResultFilePath("testHeritrixIncrementalScan/webPages/index.html"));
    
    data = new AspireObject(Standards.Basic.DOCUMENT_ROOT);
    data.setAttribute(Scheduler.ACTION_ATTR, Scheduler.START_ACTION);
    data.setAttribute(Scheduler.PROPERTIES_ATTR, Scheduler.INCREMENTAL_CRAWL_REQUEST);
    data.setAttribute("crawlId", "1");
    data.add("id", "1");
    data.add("displayName", "HeritrixCrawl");
    data.setAttribute(Standards.Scanner.NORMALIZED_CS_NAME, "TestHeritrixScanner");
    AspireObject connectorSource = new AspireObject("connectorSource");
    connectorSource.add("daysToDelete","2");
    connectorSource.add("maxFailuresToDelete","2");
    connectorSource.add("checkNotCrawlableContent","false");
    data.add(connectorSource);
    j = JobFactory.newInstance(data);
    j.putVariable("addUpdateCount", new AtomicInteger(0));
    j.putVariable("deleteCount", new AtomicInteger(0));
    
    heritrixScanner.process(j);
    urlDB = heritrixScanner.getHeritrixSourceInfo().getIncrementalDB();
    System.out.println("---------- (incremental, delete) The database contains "+urlDB.size()+" entries. ----------");
    for (String url : urlDB.keySet()){
      if (!"||status||".equals(url)){
        DataBaseURLEntry entry = DataBaseURLEntry.createDataBaseURLEntryFromString(urlDB.get(url));
        System.out.println("     "+url+": "+entry.getMd5Sum()+" fc: "+entry.getFailCount()+" lat:"+DateTimeUtilities.getISO8601DateTime(entry.getLastAccessedTime())+" dff:"+DateTimeUtilities.getISO8601DateTime(entry.getDateFirstFailed()));
      }
    }
    DataBaseURLEntry entry = DataBaseURLEntry.createDataBaseURLEntryFromString(urlDB.get(baseUrl+"/linkC.html"));
    
    assertEquals(1,entry.getFailCount());
    assertNotNull("DateFirstFailed should be not null and it is null",entry.getDateFirstFailed());
    
    entry = DataBaseURLEntry.createDataBaseURLEntryFromString(urlDB.get(baseUrl+"/other.html"));
    
    assertEquals(1,entry.getFailCount());
    assertNotNull("DateFirstFailed should be not null and it is null",entry.getDateFirstFailed());
    assertEquals(6,urlDB.size());
    
    data = new AspireObject(Standards.Basic.DOCUMENT_ROOT);
    data.setAttribute(Scheduler.ACTION_ATTR, Scheduler.START_ACTION);
    data.setAttribute(Scheduler.PROPERTIES_ATTR, Scheduler.INCREMENTAL_CRAWL_REQUEST);
    data.setAttribute("crawlId", "1");
    data.add("id", "1");
    data.add("displayName", "HeritrixCrawl");
    data.add(connectorSource);
    data.setAttribute(Standards.Scanner.NORMALIZED_CS_NAME, "TestHeritrixScanner");
    j = JobFactory.newInstance(data);
    j.putVariable("addUpdateCount", new AtomicInteger(0));
    j.putVariable("deleteCount", new AtomicInteger(0));
    
    heritrixScanner.process(j);
    urlDB = heritrixScanner.getHeritrixSourceInfo().getIncrementalDB();
    System.out.println("---------- (incremental, delete) The database contains "+urlDB.size()+" entries. ----------");
    for (String url : urlDB.keySet()){
      if (!"||status||".equals(url)){
        entry = DataBaseURLEntry.createDataBaseURLEntryFromString(urlDB.get(url));
        System.out.println("     "+url+": "+entry.getMd5Sum()+" fc: "+entry.getFailCount()+" lat:"+DateTimeUtilities.getISO8601DateTime(entry.getLastAccessedTime())+" dff:"+DateTimeUtilities.getISO8601DateTime(entry.getDateFirstFailed()));
      }
    }
    entry = DataBaseURLEntry.createDataBaseURLEntryFromString(urlDB.get(baseUrl+"/linkC.html")); 
    assertNull("Url "+baseUrl+"/linkC.html should be deleted",entry);
    
    entry = DataBaseURLEntry.createDataBaseURLEntryFromString(urlDB.get(baseUrl+"/other.html")); 
    assertNull("Url "+baseUrl+"/other.html should be deleted",entry);
    
    assertEquals(4,urlDB.size());
    
    FileUtilities.delete(uth.getResultFilePath("testHeritrixIncrementalScan/webPages"));
  }

  
  public void startServlet() throws Exception{
    server = new Server(8888);
    ResourceHandler resource_handler=new ResourceHandler();
    resource_handler.setWelcomeFiles(new String[]{uth.getResultFilePath("testHeritrixIncrementalScan/webPages/index.html")});
    resource_handler.setResourceBase(uth.getResultFilePath("testHeritrixIncrementalScan/webPages"));
    System.out.println("serving "+resource_handler.getBaseResource());
    HandlerList handlers = new HandlerList();
    handlers.setHandlers(new Handler[]{resource_handler,new DefaultHandler()});
    server.setHandler(handlers);
    server.start();
  }

  
  public void stopServlet() throws Exception{
    System.out.println("Stopping");
    server.stop();
  }
  
  private class MockHeritrixSourceInfo extends MockUp<HeritrixSourceInfo> {
    @Mock
    public Map<String, String> openDB(String name) {
      if (name.equals("snapshots"))
        return incMap;
      else
        return failedMap;
    }
  }
}
