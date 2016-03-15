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
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import org.w3c.dom.Element;

import com.searchtechnologies.aspire.framework.StageImpl;
import com.searchtechnologies.aspire.framework.Standards;
import com.searchtechnologies.aspire.services.AspireException;
import com.searchtechnologies.aspire.services.AspireObject;
import com.searchtechnologies.aspire.services.Job;

public class PrintToErrorStage extends StageImpl {
  
  boolean printContent = false;
  String outputFileName = null;
  File outputFile = null;
  PrintStream outputStream = System.err;
  boolean outputFileNotFound = false;

  public void process(Job j) throws AspireException {
    // don't write the job id as it contains the timestamp and make comparison awkward
//    outputStream.println("PRINT-TO-ERROR(component=" + getName() + ",jobId=" + (j.getJobId() == null ? "null" : j.getJobId()) + "): \n");
    
    AspireObject doc = j.get();
    try {
      outputStream.println("PRINT-TO-ERROR(component=" + getName() + "): \n\n" + doc.toXmlString(AspireObject.PRETTY));
    }
    catch(Exception e) {
      throw new AspireException("aspire.utilities.tools.PrintToError.unable-to-write-XML", e,
          "Unable to write XML for URL \"%s\". Job ID: \"%s\".", doc.getText(Standards.Basic.DISPLAY_URL_TAG), j.getJobId());
    }
    
    if(printContent) {
      if(Standards.Basic.getContentBytes(j) != null) {
        try {
          outputStream.println("CONTENT[0:200]:  " + (new String(Standards.Basic.getContentBytes(j),"UTF-8")).substring(0,200));
        }
        catch (UnsupportedEncodingException e) {
          // Should never happen
        }
      }
      else if(Standards.Basic.getContentStream(j) != null)
        outputStream.println("CONTENT:  (InputStream)");
      else {
        outputStream.println("CONTENT:  (null)");
      }
    }
  }

  public void close() {
    if(outputStream != System.err)
      outputStream.close();    
  }

  public void initialize(Element config) throws AspireException {
    if(config == null)  return;
    printContent = getBooleanFromConfig(config, "printContent", printContent);
    outputFileName = getStringFromConfig(config, "outputFile", outputFileName);
    try {
      if(outputFileName != null) {
        outputFile = new File(outputFileName);
        outputStream = new PrintStream(outputFile);
      }
    }
    catch(FileNotFoundException e) {
      outputFileNotFound = true;
      outputStream = System.err;
    }
  }
}
