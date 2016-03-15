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

import java.util.Comparator;

/**
 * Used for the priority queue to order the URL entries by the timeToSubmit value
 * @author aaguilar
 *
 */
public class TimeComparator implements Comparator<DataBaseURLEntry> {

  @Override
  public int compare(DataBaseURLEntry urlA, DataBaseURLEntry urlB) {
    if (urlA.getTimeToSubmit() < urlB.getTimeToSubmit())
      return -1;
    else if(urlA.getTimeToSubmit() > urlB.getTimeToSubmit())
      return 1;
    else
      return 0;
  }

}
