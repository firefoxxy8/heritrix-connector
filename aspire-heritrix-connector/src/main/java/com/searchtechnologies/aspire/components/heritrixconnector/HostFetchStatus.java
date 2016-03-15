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
import java.util.List;

/**
 * Holds data for each host name of the URLs that are going to be checked for deletion 
 * @author aaguilar
 *
 */
public class HostFetchStatus {
  
  protected String state;
  protected long timestamp;
  protected ArrayList<DataBaseURLEntry> urlsToFetch;
  protected long totalUrlsToFetch;
  protected long totalUrlsFetched;
  
  protected int size = 10;
  
  public void setSize(int size) {
    this.size = size;
  }
  
  public String getState() {
    return state;
  }

  public void setState(String state) {
	  this.state = state;
  }

  public long getTimestamp() {
	  return timestamp;
  }

  public void setTimestamp(long timestamp) {
	  this.timestamp = timestamp;
  }

  public long getTotalUrlsToFetch() {
	  return totalUrlsToFetch;
  }

  public void setTotalUrlsToFetch(long totalUrlsToFetch) {
	  this.totalUrlsToFetch = totalUrlsToFetch;
  }

  public long getTotalUrlsFetched() {
	  return totalUrlsFetched;
  }

  public void setTotalUrlsFetched(long totalUrlsFetched) {
	  this.totalUrlsFetched = totalUrlsFetched;
  }
  public void incrementTotalUrlsFetched() {
    this.totalUrlsFetched++;
  }

  public List<DataBaseURLEntry> getUrlsToFetch() {
	  return urlsToFetch;
  }

  public HostFetchStatus(){
	  urlsToFetch = new ArrayList<DataBaseURLEntry>(10);
	  timestamp = 0;
	  state = "";
	  totalUrlsToFetch = 0;
	  totalUrlsFetched = 0;
  }

  public boolean addUrlToFetch(DataBaseURLEntry url){
    if (size==-1 || urlsToFetch.size()<size){
      urlsToFetch.add(url);
      totalUrlsToFetch++;
      return true;
    }else{
      return false;
    } 
  }
  public void updateUrlAsFetched(DataBaseURLEntry url){
    urlsToFetch.remove(url);
      totalUrlsFetched++;
  }
}

