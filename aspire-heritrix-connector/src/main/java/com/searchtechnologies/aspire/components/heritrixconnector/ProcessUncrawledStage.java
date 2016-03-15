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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.lang.mutable.MutableInt;
import org.w3c.dom.Element;

import com.searchtechnologies.aspire.framework.BranchHandlerFactory;
import com.searchtechnologies.aspire.framework.StageImpl;
import com.searchtechnologies.aspire.framework.Standards;
import com.searchtechnologies.aspire.framework.Standards.Scanner;
import com.searchtechnologies.aspire.scanner.DocType;
import com.searchtechnologies.aspire.services.AspireException;
import com.searchtechnologies.aspire.services.AspireObject;
import com.searchtechnologies.aspire.services.BranchHandler;
import com.searchtechnologies.aspire.services.Job;

/**
 * Stage for all pages that where not crawled by heritrix in the actual job.
 * @author aaguilar
 *
 */
public class ProcessUncrawledStage extends StageImpl {

  private BranchHandler bh;

  @Override
  public void process(Job j) throws AspireException {
    
    AspireObject doc = j.get();
    
    MutableInt contentSize = new MutableInt(0);
    String md5 = null;
    byte[] contentBytes = null;
    try {
      InputStream is = (InputStream) j.getVariable(Standards.Basic.CONTENT_STREAM_KEY);
      //InputStream is = HeritrixScanner.openURLInputStream(doc.getText(Standards.Basic.FETCH_URL_TAG),true);
      
      contentBytes = AspireHeritrixProcessor.getStringFromInputStream(is).getBytes();
      
      md5 = HeritrixScanner.computeMD5(new ByteArrayInputStream(contentBytes), contentSize);
      
    } catch (NoSuchAlgorithmException e) {
      throw new AspireException("com.searchtechnologies.aspire.components.heritrixconnector.ProcessUncrawledStage",e,"Error computing md5 sum");
    } catch (IOException e) {
      throw new AspireException("com.searchtechnologies.aspire.components.heritrixconnector.ProcessUncrawledStage",e,"Error computing md5 sum");
    }
    
    HeritrixScanner scanner = ((HeritrixScanner)this.getComponent("/"+this.getAppName()+"/Main/Scanner"));
    
    String lastMd5 = doc.getText("md5");
    //Check MD5 Signature
    if (lastMd5.equals(md5)){
      doc.add(Scanner.ACTION_TAG, Scanner.Action.noChange.toString());
      j.set(doc);
      j.terminate();
      scanner.reportNoChange(doc.getText(Standards.Basic.FETCH_URL_TAG));
      return;
    }else{
      
      j.getVariableMap().remove(Standards.Basic.CONTENT_STREAM_KEY);
      if (contentBytes != null)
        j.putVariable(Standards.Basic.CONTENT_BYTES_KEY, contentBytes);
      doc.add(Scanner.ACTION_TAG, Scanner.Action.update.toString());
      doc.removeChildren("md5");
      doc.add("md5",md5);
      doc.add("contentSize", contentSize);
      doc.set(Standards.Scanner.DOC_TYPE_TAG,DocType.item.toString());
      j.set(doc);
      scanner.reportUpdate(doc.getText(Standards.Basic.FETCH_URL_TAG));
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
