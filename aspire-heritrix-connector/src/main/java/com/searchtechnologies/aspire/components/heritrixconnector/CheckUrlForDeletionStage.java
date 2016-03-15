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

import org.w3c.dom.Element;

import com.searchtechnologies.aspire.framework.BranchHandlerFactory;
import com.searchtechnologies.aspire.framework.StageImpl;
import com.searchtechnologies.aspire.framework.Standards;
import com.searchtechnologies.aspire.scanner.DocType;
import com.searchtechnologies.aspire.services.AspireException;
import com.searchtechnologies.aspire.services.AspireObject;
import com.searchtechnologies.aspire.services.BranchHandler;
import com.searchtechnologies.aspire.services.Job;

/**
 * Stage for comparing the fail count and the date first failed with the configured values.
 * @author aaguilar
 *
 */
public class CheckUrlForDeletionStage extends StageImpl {

  private BranchHandler bh = null;

  @Override
  public void process(Job j) throws AspireException {
    AspireObject doc = j.get();
    
    AspireObject propertiesXml = doc.get(Standards.Scanner.CONNECTOR_SOURCE);
    Integer daysFailedThreshold = new Integer(0);
    Integer maxFailures = new Integer(0);
    HeritrixScanner scanner = ((HeritrixScanner)this.getComponent("/"+this.getAppName()+"/Main/Scanner"));
    
    if (propertiesXml!=null){
      
      String temp = propertiesXml.getText("daysToDelete","-1");
      if (temp!=null && !temp.isEmpty() && !"-1".equals(temp))
        daysFailedThreshold = Integer.parseInt(temp);
      
      temp = propertiesXml.getText("maxFailuresToDelete","-1");
      if (temp!=null && !temp.isEmpty() && !"-1".equals(temp))
        maxFailures = Integer.parseInt(temp);
      
    }else{
      throw new AspireException("com.searchtechnologies.aspire.components.heritrixconnector.ProcessFailedUrlStage","There is no properties configured in job");
    }
    if ((new Date().getTime() - Long.parseLong(doc.getText("lastAccessedTime"))) > daysFailedThreshold*86400000 /*time threshold*/ 
        || Integer.parseInt(doc.getText("fail-count"))+1 >= maxFailures /*maximum failures*/){ 
      doc.add("action", "delete");
      doc.add(Standards.Scanner.DOC_TYPE_TAG,DocType.item.toString());
      
      j.set(doc);
      j.setBranch("onDelete");
      scanner.reportDelete(doc.getText(Standards.Basic.FETCH_URL_TAG));
    }else{
      
      doc.add("action", "failed");
      j.set(doc);
      
      j.terminate();
    }
  }

  @Override
  public void initialize(Element config) throws AspireException {
    if(config == null) return;

    bh = BranchHandlerFactory.newInstance(config, this); 
  }

  @Override
  public void close() {
    if (bh!=null)
      bh.close();
  }
}
