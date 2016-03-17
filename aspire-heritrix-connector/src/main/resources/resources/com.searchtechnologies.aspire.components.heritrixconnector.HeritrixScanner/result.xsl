<?xml version="1.0"?>
<!-- 
 ***************************************************************************
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
 *
 -->
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:import href="/aspire/files/common.xsl"/>
  <xsl:output indent="yes" method="html" />

  <xsl:template match="/">
    <html><head><title>Heritrix scanner</title></head>
    <body>
      <xsl:call-template name="standard-result-page"/>
    </body>
    </html>
  </xsl:template>
</xsl:stylesheet>