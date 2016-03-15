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
<?xml version="1.0"?>
<xsl:stylesheet version="1.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:import href="/aspire/files/common.xsl" />
	<xsl:import href="/aspire/files/component-manager.xsl" />
	<xsl:output indent="yes" method="html" />

	<xsl:template match="/">
		<html>
			<script src="/aspire/files/js/JSON.js" />
			<script src="/aspire/files/js/JSONError.js" />
			<script src="/aspire/files/js/utilities.js" />
			<head>
				<title>
					HeritrixScanner Component Status -
					<xsl:value-of select="/status/component/@name" />
				</title>
			</head>
			<body>
				<xsl:call-template name="header" />
				<table>
					<tr>
						<td>
							<img border="1">
								<xsl:attribute name="src"><xsl:value-of
									select="/status/@application" /><xsl:value-of
									select="/status/@component" /><xsl:text>/files/image.jpeg</xsl:text></xsl:attribute>
							</img>
						</td>
						<td>
							<h2 style="margin-left:2em">
								HeritrixScanner:
								<xsl:value-of select="/status/component/@name" />
							</h2>
						</td>
					</tr>
				</table>

				<p />
				<hr width="50%" />
				<h4>URI Totals Report Data Status: </h4>
				<xsl:for-each select="status/component/heritrixScanner/uriTotalsReportData">
					<xsl:variable name="displayName">
						<xsl:choose>
							<xsl:when test="@defaultConfig = 'true'">
								<xsl:value-of select="@url" />
							</xsl:when>
							<xsl:otherwise>
								<xsl:value-of select="@configFile" />
							</xsl:otherwise>
						</xsl:choose>
					</xsl:variable>
					<xsl:call-template name="collapsingDivHeader">
						<xsl:with-param name="id" select="@crawlId" />
						<xsl:with-param name="text" select="$displayName" />
					</xsl:call-template>
					<div>
						<xsl:attribute name="id"><xsl:value-of
							select="@crawlId"></xsl:value-of></xsl:attribute>
						<table border="1" cellpadding="3" cellspacing="0" colspan="1">
							<tr bgcolor="#DDF3DD">
								<td colspan="1">
									<b>
										<xsl:text>Queued Uri Count: </xsl:text>
									</b>
								</td>
								<td colspan="1">
									<xsl:value-of select="queuedUriCount" />
								</td>
							</tr>
							<tr>
								<td colspan="1">
									<b>
										<xsl:text>Downloaded Uri Count: </xsl:text>
									</b>
								</td>
								<td colspan="1">
									<xsl:value-of select="downloadedUriCount" />
								</td>
							</tr>
							<tr bgcolor="#DDF3DD">
								<td colspan="1">
									<b>
										<xsl:text>Total Uri Count: </xsl:text>
									</b>
								</td>
								<td colspan="1">
									<xsl:value-of select="totalUriCount" />
								</td>
							</tr>
						</table>
					</div>
					<br />
				</xsl:for-each>
				
				<h4>URI Total Report:</h4>
				<xsl:value-of select="status/component/heritrixScanner/uriTotalReport" />
				<br />
				<h4>Frontier Report:</h4>
        <xsl:value-of select="status/component/heritrixScanner/frontierReport" />
        <br />
        <h4>Elapsed Report:</h4>
        <xsl:value-of select="status/component/heritrixScanner/elapsedReport" />
        <br />
        
		<p />
		
		<hr width="50%" />
		<xsl:call-template name="componentDetail" />
				
		<p />
		<hr width="50%" />
		<xsl:call-template name="scannerDetail"> 		 					
			<xsl:with-param name="title" select="'Scanner Detail:'" />
			<xsl:with-param name="divName" select="'scannerDetail'" />
			<xsl:with-param name="path" select="/status/component/scanner/*" /> 
		</xsl:call-template>
		
		<p />
		<hr width="50%" />
		<xsl:call-template name="scannerDetail"> 		 					
			<xsl:with-param name="title" select="'Scanner Type Detail:'" />
			<xsl:with-param name="divName" select="'scannerTypeDetail'" />
			<xsl:with-param name="path" select="/status/component/scanner/*/*" /> 
		</xsl:call-template>
		
		<p />
		<hr width="50%" />
		<xsl:call-template name="scannerDetail"> 		 					
			<xsl:with-param name="title" select="'Scanner Specific Detail:'" />
			<xsl:with-param name="divName" select="'scannerSpecificDetail'" />
			<xsl:with-param name="path" select="/status/component/scanner/*/*/*" /> 
		</xsl:call-template>


				
        <xsl:call-template name="collapsingDivHeader">
          <xsl:with-param name="id">databaseStatistics</xsl:with-param>
          <xsl:with-param name="text">
            Database Statistics
          </xsl:with-param>
          <xsl:with-param name="collapsed">false</xsl:with-param>
        </xsl:call-template>
        <div id="databaseStatistics">
				<xsl:for-each
					select="status/component/heritrixScanner/contentSourcesDB/database">

					<div>
						<xsl:attribute name="id"><xsl:value-of
							select="@id"></xsl:value-of></xsl:attribute>

						<table border="1" cellpadding="3" cellspacing="0" colspan="1">
							<tr bgcolor="#DDF3DD">
								<td colspan="1">
									<b>
										<xsl:text>Database Id: </xsl:text>
									</b>
								</td>
								<td colspan="1">
									<xsl:value-of select="@id" />
								</td>
							</tr>
							<tr>
								<td colspan="1">
									<b>
										<xsl:text>Name: </xsl:text>
									</b>
								</td>
								<td colspan="1">
									<xsl:value-of select="friendlyName" />
								</td>
							</tr>
							<tr bgcolor="#DDF3DD">
								<td colspan="1">
									<b>
										<xsl:text>Database Directory: </xsl:text>
									</b>
								</td>
								<td colspan="1">
									<xsl:value-of select="directory" />
								</td>
							</tr>
							<tr>
								<td colspan="1">
									<b>
										<xsl:text>Uri added on last crawl: </xsl:text>
									</b>
								</td>
								<td colspan="1">
									<xsl:value-of select="urlAdded" />
								</td>
							</tr>
							<tr bgcolor="#DDF3DD">
								<td colspan="1">
									<b>
										<xsl:text>Uri updated on last crawl: </xsl:text>
									</b>
								</td>
								<td colspan="1">
									<xsl:value-of select="urlUpdated" />
								</td>
							</tr>
							<tr>
								<td colspan="1">
									<b>
										<xsl:text>Revisit rate (last crawl): </xsl:text>
									</b>
								</td>
								<td colspan="1">
									<xsl:value-of select="revisitRate" />
								</td>
							</tr>
							<tr bgcolor="#DDF3DD">
								<td colspan="1">
									<b>
										<xsl:text>Uri deleted on last crawl: </xsl:text>
									</b>
								</td>
								<td colspan="1">
									<xsl:value-of select="urlDeleted" />
								</td>
							</tr>
							<tr>
								<td colspan="1">
									<b>
										<xsl:text>Total Uri Count: </xsl:text>
									</b>
								</td>
								<td colspan="1">
									<xsl:value-of select="size" />
								</td>
							</tr>
							<tr bgcolor="#DDF3DD">
                <td colspan="1" valign="top">
                  <b>
                    <xsl:call-template name="collapsingDivHeader">
                      <xsl:with-param name="id">lastHostname<xsl:value-of select="@id" /></xsl:with-param>
                      <xsl:with-param name="text">Show last hostnames processed</xsl:with-param>
                      <xsl:with-param name="collapsed">true</xsl:with-param>
                    </xsl:call-template>
                  </b>
                </td>
                <td colspan="1">
                  <div>
                    <xsl:attribute name="id">lastHostname<xsl:value-of select="@id" /></xsl:attribute>
                    <table border="1" cellpadding="3" cellspacing="0"
                      colspan="1">
                      <xsl:for-each select="lastHostnames/hostname">
                        <tr bgcolor="#DDF3DD">
                          <td colspan="1">
                            <b>
                              <xsl:value-of select="@name" />
                            </b>
                          </td>
                          <td colspan="1">
                            <xsl:value-of select="total" />
                          </td>
                        </tr>
                      </xsl:for-each>
                    </table>
                  </div>
                </td>
              </tr>
							<tr bgcolor="#DDF3DD">
								<td colspan="1" valign="top">
									<b>
										<xsl:call-template name="collapsingDivHeader">
											<xsl:with-param name="id">hostname<xsl:value-of select="@id" /></xsl:with-param>
											<xsl:with-param name="text">Show hostnames</xsl:with-param>
											<xsl:with-param name="collapsed">false</xsl:with-param>
										</xsl:call-template>
									</b>
								</td>
								<td colspan="1">
									<div>
										<xsl:attribute name="id">hostname<xsl:value-of select="@id" /></xsl:attribute>
										<table border="1" cellpadding="3" cellspacing="0"
											colspan="1">
											<xsl:for-each select="hostnames/hostname">
												<tr bgcolor="#DDF3DD">
													<td colspan="1">
														<b>
															<xsl:value-of select="@name" />
														</b>
													</td>
													<td colspan="1">
														<xsl:value-of select="total" />
													</td>
												</tr>
											</xsl:for-each>
										</table>
									</div>
								</td>
							</tr>
						</table>
					</div>
					<br />
				</xsl:for-each>
				</div>
			</body>
		</html>
	</xsl:template>
</xsl:stylesheet>