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

import java.util.ArrayList;

/**
 * Contains a list of accept and reject regex patterns for CrawlURI's
 * @author ralfaro
 *
 */
public class URIFilterPattern {
  
  private ArrayList<String> acceptPatterns = new ArrayList<String>(); 

  private ArrayList<String> rejectPatterns = new ArrayList<String>();

  /**
   * Sets the accept patterns
   * @param acceptPatterns the acceptPattern to set
   */
  public void setAcceptPatterns(ArrayList<String> acceptPatterns) {
    for(String acceptPattern : acceptPatterns)
    {
      if(!this.acceptPatterns.contains(acceptPattern))
        this.acceptPatterns.add(acceptPattern);
    }
  }
  
  /**
   * Adds a single pattern to the accept patterns list
   * @param pattern
   */
  public void addAcceptPattern(String pattern){
    this.acceptPatterns.add(pattern);
  }

  /**
   * @return the acceptPattern
   */
  public ArrayList<String> getAcceptPatterns() {
    return acceptPatterns;
  }
  
  /**
   * Returns a list of accept patterns formatted as values of a list property for a spring 
   * application context file
   * @see URIFilterPattern#getPatternsAsBeansListValues(ArrayList)
   * @return the list of patterns
   */
  public String getAcceptPatternsAsBeansListValues(){
    return getPatternsAsBeansListValues(acceptPatterns);
  }

  /**
   * Sets the reject patterns
   * @param rejectPatterns the rejectPatterns to set
   */
  public void setRejectPatterns(ArrayList<String> rejectPatterns) {
    for(String rejectPattern : rejectPatterns)
    {
      if(!this.rejectPatterns.contains(rejectPattern))
        this.rejectPatterns.add(rejectPattern);
    }
  }
  
  /**
   * Adds a single pattern to the reject patterns list
   * @param pattern the reject pattern to add
   */
  public void addRejectPattern(String pattern){
    this.rejectPatterns.add(pattern);
  }

  /**
   * Returns the reject patterns
   * @return the rejectPatterns
   */
  public ArrayList<String> getRejectPatterns() {
    return rejectPatterns;
  }
  
  /**
   * Returns a list of reject patterns formatted as values of a list property for a spring 
   * application context file
   * @see URIFilterPattern#getPatternsAsBeansListValues(ArrayList)
   * @return the list of reject patterns formatted as values
   */
  public String getRejectPatternsAsBeansListValues(){
    return getPatternsAsBeansListValues(rejectPatterns);
  }
  
  
  /**
   * Tests if a specified Crawl URI should be accepted or not.
   * @param uri the uri to test
   * @return true if the uri should be accepted
   */
  public boolean accept(String uri){
    boolean matches = true;
    
    if (acceptPatterns.size()==0 && rejectPatterns.size()==0){
      return true;
    }
    
    if (acceptPatterns.size()!=0){
      matches = false;
      for (String acceptPattern : acceptPatterns){
        if (uri.matches(acceptPattern)){
          matches = true;
          break;
        }
      }
    }
    
    for (String rejectPattern : rejectPatterns){
      if (uri.matches(rejectPattern)){
        matches = false;
        break;
      }
    }
    return matches;
  }

  /**
   * Returns a list of regex formatted to be passed to the Spring application context file as 
   * values of a list property
   * Example:
   *  <value>.*</value>
   *  <value>.*html$</value>
   * @return the list of patterns suitable for the spring application
   */
  private String getPatternsAsBeansListValues(ArrayList<String> patterns){
    StringBuilder sb = new StringBuilder();
    sb.append("<list xmlns=\"http://www.springframework.org/schema/beans\">");
    for (String pattern : patterns){
      sb.append("<value>");
      sb.append(pattern);
      sb.append("</value>");
    }
    sb.append("</list>");
    return sb.toString();
  }

  public String getRejectDefaults(boolean reject) {
    ArrayList<String> defaultRejects = new ArrayList<String>();
    if (reject) {
      defaultRejects.add(".*\\.js.*");
      defaultRejects.add(".*\\.css.*");
      defaultRejects.add(".*\\.swf.*");
      defaultRejects.add(".*\\.gif.*");
      defaultRejects.add(".*\\.png.*");
      defaultRejects.add(".*\\.jpg.*");
      defaultRejects.add(".*\\.jpeg.*");
      defaultRejects.add(".*\\.bmp.*");
      defaultRejects.add(".*\\.mp3.*");
      defaultRejects.add(".*\\.mp4.*");
      defaultRejects.add(".*\\.avi.*");
      defaultRejects.add(".*\\.mpg.*");
      defaultRejects.add(".*\\.mpeg.*");
    }
    return getPatternsAsBeansListValues(defaultRejects);
  }
}
