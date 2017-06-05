package org.apache.geode.session.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.apache.geode.test.dunit.DUnitEnv;
import org.apache.geode.test.dunit.cache.internal.JUnit4CacheTestCase;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

/**
 * Created by danuta on 5/26/17.
 */
public class CargoTest extends JUnit4CacheTestCase
{
  static TomcatInstall tomcat7;

  Client client;
  ContainerManager manager;

  CloseableHttpResponse resp;

  @BeforeClass
  public static void setupTomcatInstall() throws Exception
  {
    tomcat7 = new TomcatInstall(TomcatInstall.TomcatVersion.TOMCAT7);
    tomcat7.setLocators(DUnitEnv.get().getLocatorString());
  }

  private void containersShouldBeCreatingIndividualSessions(ContainerManager manager) throws Exception
  {
    for (int i = 0; i < manager.numContainers(); i++)
    {
      client.setPort(Integer.parseInt(manager.getContainerPort(i)));
      resp = client.get(null);

      assertEquals("JSESSIONID", resp.getFirstHeader("Set-Cookie").getElements()[0].getName());
    }
  }

  private void containersShouldReplicateSessions(ContainerManager manager) throws Exception
  {
    if (manager.numContainers() < 2)
      throw new IllegalArgumentException("Bad ContainerManager, must have 2 or more containers for this test");

    client.setPort(Integer.parseInt(manager.getContainerPort(0)));
    resp = client.get(null);

    for (int i = 0; i < manager.numContainers(); i++)
    {
      client.setPort(Integer.parseInt(manager.getContainerPort(i)));
      resp = client.get(null);

      assertEquals(resp.getFirstHeader("Set-Cookie"), null);
    }
  }

  private void containersShouldHavePersistentSessionData(ContainerManager manager) throws Exception
  {
    String key = "value_testSessionPersists";
    String value = "Foo";

    if (manager.numContainers() < 2)
      throw new IllegalArgumentException("Bad ContainerManager, must have 2 or more containers for this test");

    client.setPort(Integer.parseInt(manager.getContainerPort(0)));
    resp = client.set(key, value);

    for (int i = 0; i < manager.numContainers(); i++)
    {
      client.setPort(Integer.parseInt(manager.getContainerPort(i)));
      resp = client.get(key);

      assertEquals(value, EntityUtils.toString(resp.getEntity()));
    }
  }

  private void containerFailureShouldStillAllowOtherContainersDataAccess(ContainerManager manager) throws Exception
  {
    String key = "value_testSessionPersists";
    String value = "Foo";

    if (manager.numContainers() < 2)
      throw new IllegalArgumentException("Bad ContainerManager, must have 2 or more containers for this test");

    client.setPort(Integer.parseInt(manager.getContainerPort(0)));
    resp = client.set(key, value);

    manager.stopContainer(0);

    for (int i = 1; i < manager.numContainers(); i++)
    {
      client.setPort(Integer.parseInt(manager.getContainerPort(i)));
      resp = client.get(key);

      assertEquals(value, EntityUtils.toString(resp.getEntity()));
    }
  }

  private void containerInvalidationShouldRemoveValueAccessForAllContainers(ContainerManager manager) throws Exception
  {
    String key = "value_testInvalidate";
    String value = "Foo";

    client.setPort(Integer.parseInt(manager.getContainerPort(0)));
    resp = client.set(key, value);
    resp = client.invalidate();

    for (int i = 0; i < manager.numContainers(); i++)
    {
      client.setPort(Integer.parseInt(manager.getContainerPort(i)));
      resp = client.get(key);

      assertEquals("", EntityUtils.toString(resp.getEntity()));
    }
  }

  private void containerShouldExpireInSetTimeframeForAllContainers(ContainerManager manager) throws Exception
  {
    String key = "value_testSessionExpiration";
    String value = "Foo";

    client.setPort(Integer.parseInt(manager.getContainerPort(0)));
    resp = client.set(key, value);
    resp = client.setMaxInactive(1);

    Thread.sleep(10000);

    for (int i = 0; i < manager.numContainers(); i++) {
      client.setPort(Integer.parseInt(manager.getContainerPort(i)));
      resp = client.get(key);

      assertEquals("", EntityUtils.toString(resp.getEntity()));
    }
  }

  @Before
  public void setup()
  {
    client = new Client();
    manager = new ContainerManager();
  }

  @After
  public void stop()
  {
    manager.stopAllActiveContainers();
  }

  @Test
  public void twoTomcatContainersShouldBeCreatingIndividualSessions() throws Exception
  {
    ContainerManager manager = new ContainerManager();
    tomcat7.setLocators("");

    manager.addContainers(2, tomcat7);

    manager.startAllInactiveContainers();
    containersShouldBeCreatingIndividualSessions(manager);
    manager.stopAllActiveContainers();
  }

  @Test
  public void twoTomcatContainersShouldReplicateCookies() throws Exception
  {
    ContainerManager manager = new ContainerManager();
    manager.addContainers(2, tomcat7);

    manager.startAllInactiveContainers();
    containersShouldReplicateSessions(manager);
    manager.stopAllActiveContainers();
  }

  @Test
  public void threeTomcatContainersShouldHavePersistentSessionData() throws Exception
  {
    ContainerManager manager = new ContainerManager();
    manager.addContainers(3, tomcat7);

    manager.startAllInactiveContainers();
    containersShouldHavePersistentSessionData(manager);
    manager.stopAllActiveContainers();
  }

  @Test
  public void containerFailureShouldStillAllowTwoOtherContainersToAccessSessionData() throws Exception
  {
    ContainerManager manager = new ContainerManager();
    manager.addContainers(3, tomcat7);

    manager.startAllInactiveContainers();
    containerFailureShouldStillAllowOtherContainersDataAccess(manager);
    manager.stopAllActiveContainers();
  }

  @Test
  public void invalidateShouldNotAllowContainerToAccessKeyValue() throws Exception
  {
    ContainerManager manager = new ContainerManager();
    manager.addContainers(2, tomcat7);

    manager.startAllInactiveContainers();
    containerInvalidationShouldRemoveValueAccessForAllContainers(manager);
    manager.stopAllActiveContainers();
  }

  @Test
  public void sessionShouldExpireInSetTimePeriod() throws Exception
  {
    ContainerManager manager = new ContainerManager();
    manager.addContainers(2, tomcat7);

    manager.startAllInactiveContainers();
    containerShouldExpireInSetTimeframeForAllContainers(manager);
    manager.stopAllActiveContainers();
  }

//  /**
//   * Test callback functionality. This is here really just as an example. Callbacks are useful to
//   * implement per test actions which can be defined within the actual test method instead of in a
//   * separate servlet class.
//   */
//  @Test
//  public void testCallback() throws Exception {
//    final String helloWorld = "Hello World";
//    Callback c = new Callback() {
//
//      @Override
//      public void call(HttpServletRequest request, HttpServletResponse response)
//          throws IOException {
//        PrintWriter out = response.getWriter();
//        out.write(helloWorld);
//      }
//    };
//    servlet.getServletContext().setAttribute("callback", c);
//
//    Runtime rt = Runtime.getRuntime();
//    Process pr = rt.exec("jar cvf /path/to/your/project/your-file.war");
//
//    WebConversation wc = new WebConversation();
//    WebRequest req = new GetMethodWebRequest(String.format("http://localhost:%d/test", port));
//
//    req.setParameter("cmd", QueryCommand.CALLBACK.name());
//    req.setParameter("param", "callback");
//    WebResponse response = wc.getResponse(req);
//
//    assertEquals(helloWorld, response.getText());
//  }
}
