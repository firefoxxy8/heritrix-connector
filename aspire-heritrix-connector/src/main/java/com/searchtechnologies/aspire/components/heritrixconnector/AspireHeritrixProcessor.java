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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicInteger;

import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.modules.deciderules.recrawl.IdenticalDigestDecideRule;
import org.json.JSONException;

import com.searchtechnologies.aspire.framework.utilities.StringUtilities;
import com.searchtechnologies.aspire.services.AspireException;

/**
 * Heritrix processor for Aspire: will enqueue all Heritrix output CrawlURI's to an Aspire Pipeline
 * @author ralfaro
 *
 */
public class AspireHeritrixProcessor extends Processor {
  
  /**
   * A reference to the HeritrixScanner that is using this processor (the scanner 
   * launched the Heritrix Job using this processor's instance). 
   */
  private HeritrixScanner heritrixScanner=null;
  
  /**
   * Determines how many writes have the database wrote and determines when commits are done
   */
  private AtomicInteger writes = new AtomicInteger(0);

  /**
   * Regex to exclude the content
   */
  private String cleanupRegex;
  
  private String[] cleanupTypes = {
                       "text/html",
                       "application/xhtml+xml",
                       "text/xml",
                       "application/xml"};
  
  /**
   * Set a regex to exclude from the content (and MD5 value)
   * @param cleanupRegex
   */
  public void setCleanupRegex(String cleanupRegex) {
    this.cleanupRegex = cleanupRegex;
  }

  /**
   * Sets the heritrixScanner instance
   * @param scanner HeritrixScanner
   */
  public void setHeritrixScanner(HeritrixScanner scanner){
    heritrixScanner=scanner;
  }

  /*
   * (non-Javadoc)
   * @see org.archive.modules.Processor#shouldProcess(org.archive.modules.CrawlURI)
   */
  @Override
  protected boolean shouldProcess(CrawlURI uri) {
    // If failure, or we haven't fetched the resource yet, return
    if (uri.getFetchStatus() <= 0) {
        return false;
    }
    
    // If no recorded content at all, don't write record.
    long recordLength = uri.getContentSize();
    if (recordLength <= 0) {
        // getContentSize() should be > 0 if any material (even just
        // HTTP headers with zero-length body is available.
        return false;
    }
    
    return true;
  }

  /*
   * (non-Javadoc)
   * @see org.archive.modules.Processor#innerProcess(org.archive.modules.CrawlURI)
   */
  @Override
  protected void innerProcess(CrawlURI uri) throws InterruptedException {
    try {
      if (shouldWrite(uri)){
        heritrixScanner.debug("URL processed: %s, protocol: %s, status code: %d hops: %d path-from-seed: %s", uri.getURI(), uri.getUURI().getScheme(), uri.getFetchStatus(),uri.getHopCount(),uri.getPathFromSeed());
        processURL(uri);
      } else {     
        heritrixScanner.debug("URL not processed: %s, protocol: %s, status code: %d hops: %d path-from-seed: %s", uri.getURI(), uri.getUURI().getScheme(), uri.getFetchStatus(),uri.getHopCount(),uri.getPathFromSeed());
      } 
    } catch (IOException e) {
      heritrixScanner.error(e,"Cannot read input stream for url: %s", uri.getURI());
    } catch (AspireException e) {
      heritrixScanner.error(e,"Error processing onAdd event for url: %s", uri.getURI());
    }
  }
  
  /**
   * Process the CrawlURI from heritrix, calculates the MD5 sum and pass the link to the Heritrix Scanner
   * @param uri
   * @throws IOException
   * @throws AspireException
   */
  private void processURL(CrawlURI uri) throws IOException, AspireException{
    //Gets the input stream
    //InputStream is = uri.getRecorder().getRecordedInput().getMessageBodyReplayInputStream();
    
    InputStream is = cleanupContent(uri);
    
    
    Long size = null;
    try {
      size = (Long) uri.getExtraInfo().get("aspireContentSize");
    } catch (JSONException e) {
    }
    long streamSize = 0;
    if (size==null)
      streamSize = uri.getRecorder().getRecordedInput().getSize();
    else
      streamSize = size;
    
    String md5 = uri.getContentDigestString();
    
    String discoveredBy = "";
    
    if (uri.getVia() != null)
      discoveredBy = uri.getVia().getURI();
    
    //Sends the url to be processed by the HeritrixScanner
    heritrixScanner.addURL(uri.getURI(), streamSize, md5, writes.incrementAndGet()%10==0/*Commits every 100*/, is, 
        uri.getContentType(), uri.getData().get("xslt")!=null, discoveredBy, uri.getPathFromSeed());

  }
  
  public static String getStringFromInputStream(InputStream is) throws IOException {
    
    BufferedReader br = null;
    StringBuilder sb = new StringBuilder();
 
    String line;
    try {
 
      br = new BufferedReader(new InputStreamReader(is,"UTF-8"));
      while ((line = br.readLine()) != null) {
        sb.append(line);
      }
 
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (br != null) {
        br.close();
      }
    }
 
    return sb.toString();
  }
  
  private InputStream cleanupContent(CrawlURI uri) throws IOException {
    String type = uri.getContentType();
    InputStream is = uri.getRecorder().getRecordedInput().getMessageBodyReplayInputStream();
    
    if (cleanupRegex.isEmpty())
      return is;
    
    boolean matches = false;
    for (String contentType : cleanupTypes) {
      if (StringUtilities.equalsIgnoreCase(type, contentType)) {
        matches = true;
        break;
      }
    }
    
    if (is != null && matches) { 
      
      String html = (String) uri.getData().get("xslt");
      if (html!=null){
        try {
          is.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      } else {
        //Get the original html content
        html = getStringFromInputStream(is);  
      }
     /* FileWriter fw = new FileWriter("D:\\out.txt");
      fw.write(html);
      fw.close();*/
      
      //Remove the regular expressions
      html = html.replaceAll(cleanupRegex,"");
      
      
     /* fw = new FileWriter("D:\\out2.txt");
      fw.write(html);
      fw.close();*/
      
      uri.setContentDigest("MD5",StringUtilities.stringToMD5(html).getBytes());
      uri.addExtraInfo("aspireContentSize", new Long(html.length()));
      return new ByteArrayInputStream(html.getBytes());
    }
    
    return is;
  }

  /**
   * Filters the CrawlURI's so it doesn't crawl the robots file and any page that threw a page(4XX)/server(5XX) error
   * @param curi
   * @return true if it should be written
   */
  private boolean shouldWrite(CrawlURI curi){
    if (getSkipIdenticalDigests() && IdenticalDigestDecideRule.hasIdenticalDigest(curi)) {
        return false;
    }
    boolean retVal;
    String robotsMeta = null;
    try {
      robotsMeta = curi.getExtraInfo().getString("robotsMeta");
    } catch (JSONException e) {
    }
    if (robotsMeta!=null && robotsMeta.contains("noindex")) {
      return false;
    }
    if (!curi.getURI().toLowerCase().endsWith("robots.txt")){
      String scheme = curi.getUURI().getScheme().toLowerCase();
      if (scheme.equals("http") || scheme.equals("https")) {
          retVal = curi.getFetchStatus() >= 200 && curi.getFetchStatus() < 400 && curi.getHttpMethod() != null;
      } else if (scheme.equals("ftp")) {
          retVal = curi.getFetchStatus() >= 200 && curi.getFetchStatus() < 400;
      } else {
          return false;
      }
      if (retVal == false) {
          return false;
      }
      return true; 
    } else {
      return false;
    }
  }
  
  /**
   * Whether to skip the writing of a record when URI history information is
   * available and indicates the prior fetch had an identical content digest.
   * Note that subclass settings may provide more fine-grained control on
   * how identical digest content is handled; for those controls to have
   * effect, this setting must not be 'true' (causing content to be 
   * skipped entirely). 
   * Default is false.
   */
  boolean skipIdenticalDigests = false; 
  public boolean getSkipIdenticalDigests() {
      return skipIdenticalDigests;
  }
  public void setSkipIdenticalDigests(boolean skipIdenticalDigests) {
      this.skipIdenticalDigests = skipIdenticalDigests;
  }
  
  @Override
  protected void innerRejectProcess(CrawlURI uri) throws InterruptedException {
    if (heritrixScanner!=null){
      heritrixScanner.info("URL rejected by Heritrix: %s", uri.getURI());
    }
  }

}
