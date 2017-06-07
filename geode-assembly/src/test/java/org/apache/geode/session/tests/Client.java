/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.session.tests;

import org.apache.geode.modules.session.QueryCommand;
import org.apache.http.Header;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URISyntaxException;

public class Client {
  private String host = "localhost";
  private int port = 8080;
  private String cookie;
  private HttpContext context;

  private URIBuilder reqURIBuild;
  private CloseableHttpClient httpclient;

  public Client() {
    reqURIBuild = new URIBuilder();
    reqURIBuild.setScheme("http");

    httpclient = HttpClients.createDefault();
    context = new BasicHttpContext();

    cookie = null;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public int getPort() {
    return port;
  }

  public void resetURI() {
    reqURIBuild.setHost(host + ":" + port);
    reqURIBuild.clearParameters();
  }

  private Response doRequest(HttpGet req, boolean storeRespCookie) throws IOException {
    if (cookie != null) {
      BasicClientCookie cookie = new BasicClientCookie("JSESSIONID", this.cookie);
      cookie.setDomain(req.getURI().getHost());
      cookie.setPath("/");

      BasicCookieStore cookieStore = new BasicCookieStore();
      cookieStore.addCookie(cookie);

      context.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);
    }

    CloseableHttpResponse resp = httpclient.execute(req, context);

    boolean isNew = true;
    String reqCookie = getCookieHeader(resp);
    if (reqCookie == null) {
      isNew = false;
      reqCookie = this.cookie;
    } else if (storeRespCookie) {
      this.cookie = reqCookie;
    }

    StatusLine status = resp.getStatusLine();
    if (status.getStatusCode() != 200) {
      throw new IOException("Http request failed. " + status);
    }

    Response response = new Response(reqCookie, EntityUtils.toString(resp.getEntity()), isNew);
    resp.close();
    return response;
  }

  private String getCookieHeader(CloseableHttpResponse resp) {
    Header lastHeader = resp.getLastHeader("Set-Cookie");

    if (lastHeader == null) {
      return null;
    }
    return lastHeader.getElements()[0].getValue();
  }

  public Response get(String key) throws IOException, URISyntaxException {
    return get(key, true);
  }

  public Response get(String key, boolean storeRespCookie) throws IOException, URISyntaxException {
    resetURI();
    reqURIBuild.setParameter("cmd", QueryCommand.GET.name());
    reqURIBuild.setParameter("param", key);

    return doRequest(new HttpGet(reqURIBuild.build()), storeRespCookie);
  }

  public Response set(String key, String value) throws IOException, URISyntaxException {
    return set(key, value, true);
  }

  public Response set(String key, String value, boolean storeRespCookie)
      throws IOException, URISyntaxException {
    resetURI();
    reqURIBuild.setParameter("cmd", QueryCommand.SET.name());
    reqURIBuild.setParameter("param", key);
    reqURIBuild.setParameter("value", value);

    return doRequest(new HttpGet(reqURIBuild.build()), storeRespCookie);
  }

  public Response remove(String key) throws IOException, URISyntaxException {
    return remove(key, true);
  }

  public Response remove(String key, boolean storeRespCookie) throws URISyntaxException,
      IOException {
    resetURI();
    reqURIBuild.setParameter("cmd", QueryCommand.REMOVE.name());
    reqURIBuild.setParameter("param", key);

    return doRequest(new HttpGet(reqURIBuild.build()), storeRespCookie);
  }

  public Response invalidate() throws IOException, URISyntaxException {
    return invalidate(true);
  }

  public Response invalidate(boolean storeRespCookie) throws IOException, URISyntaxException {
    resetURI();
    reqURIBuild.setParameter("cmd", QueryCommand.INVALIDATE.name());
    reqURIBuild.setParameter("param", "null");

    return doRequest(new HttpGet(reqURIBuild.build()), storeRespCookie);
  }

  public Response setMaxInactive(int time) throws IOException, URISyntaxException {
    return setMaxInactive(time, true);
  }

  public Response setMaxInactive(int time, boolean storeRespCookie)
      throws IOException, URISyntaxException {
    resetURI();
    reqURIBuild.setParameter("cmd", QueryCommand.SET_MAX_INACTIVE.name());
    reqURIBuild.setParameter("value", Integer.toString(time));

    return doRequest(new HttpGet(reqURIBuild.build()), storeRespCookie);
  }

  public class Response {
    private final String sessionCookie;
    private final String response;
    private final boolean isNew;

    public Response(String sessionCookie, String response, boolean isNew) {
      this.sessionCookie = sessionCookie;
      this.response = response;
      this.isNew = isNew;
    }

    public String getSessionCookie() {
      return sessionCookie;
    }

    public String getResponse() {
      return response;
    }

    public boolean isNew() {
      return isNew;
    }

    @Override
    public String toString() {
      return "Response{" + "sessionCookie='" + sessionCookie + '\'' + ", response='" + response
          + '\'' + '}';
    }
  }
}
