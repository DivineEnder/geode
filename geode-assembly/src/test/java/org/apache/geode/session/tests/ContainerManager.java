package org.apache.geode.session.tests;

import org.codehaus.cargo.container.ContainerType;
import org.codehaus.cargo.container.InstalledLocalContainer;
import org.codehaus.cargo.container.configuration.ConfigurationType;
import org.codehaus.cargo.container.configuration.FileConfig;
import org.codehaus.cargo.container.configuration.LocalConfiguration;
import org.codehaus.cargo.container.deployable.WAR;
import org.codehaus.cargo.container.property.GeneralPropertySet;
import org.codehaus.cargo.container.property.LoggingLevel;
import org.codehaus.cargo.container.property.ServletPropertySet;
import org.codehaus.cargo.generic.DefaultContainerFactory;
import org.codehaus.cargo.generic.configuration.DefaultConfigurationFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.IntStream;

/**
 * Created by danuta on 6/1/17.
 */
public class ContainerManager
{
  private static final String PLAIN_LOG_FILE = "/tmp/logs/container";

  private ArrayList<InstalledLocalContainer> containers;
  private ArrayList<ContainerInstall> installs;

  public ContainerManager()
  {
    containers = new ArrayList<>();
    installs = new ArrayList<>();
  }

  public String getPathToTestWAR() throws IOException
  {
    // Start out searching directory above current
    String curPath = "../";

    // Looking for extensions folder
    final String warModuleDirName = "extensions";
    File warModuleDir = null;

    // While directory searching for is not found
    while (warModuleDir == null)
    {
      // Try to find the find the directory in the current directory
      File[] files = new File(curPath).listFiles();
      for (File file : files)
      {
        if (file.isDirectory() && file.getName().equals(warModuleDirName))
        {
          warModuleDir = file;
          break;
        }
      }

      // Keep moving up until you find it
      curPath += "../";
    }

    // Return path to extensions plus hardcoded path from there to the WAR
    return warModuleDir.getAbsolutePath() + "/session-testing-war/build/libs/session-testing-war.war";
  }

  public InstalledLocalContainer addContainer(ContainerInstall install, int index) throws Exception
  {
    // Create the Cargo Container instance wrapping our physical container
    LocalConfiguration configuration = (LocalConfiguration) new DefaultConfigurationFactory().createConfiguration(
        install.getContainerId(), ContainerType.INSTALLED, ConfigurationType.STANDALONE, "/tmp/cargo_configs/config" + index);
    configuration.setProperty(GeneralPropertySet.LOGGING, LoggingLevel.HIGH.getLevel());
    configuration.setProperty(GeneralPropertySet.PORT_OFFSET, Integer.toString(index));
    configuration.applyPortOffset();



//    FileConfig fconfg = new FileConfig();

//    configuration.setConfigFileProperty();

    // Statically deploy WAR file for servlet
    String WARPath = getPathToTestWAR();
    WAR war = new WAR(WARPath);
    war.setContext("");
    configuration.addDeployable(war);
    System.out.println("Deployed WAR file found at " + WARPath);

    // Create the container, set it's home dir to where it was installed, and set the its output log
    InstalledLocalContainer container =
        (InstalledLocalContainer) (new DefaultContainerFactory()).createContainer(
            install.getContainerId(), ContainerType.INSTALLED, configuration);

    container.setHome(install.getInstallPath());
    container.setOutput(PLAIN_LOG_FILE + containers.size() + ".log");
    System.out.println("Sending log file output to " + PLAIN_LOG_FILE + containers.size() + ".log");

    containers.add(index, container);
    installs.add(index, install);

    System.out.println("Setup container " + getContainerDescription(index) + "\n-----------------------------------------");
    return container;
  }

  public InstalledLocalContainer addContainer(ContainerInstall install) throws Exception
  {
    return addContainer(install, containers.size());
  }

  public InstalledLocalContainer editContainer(ContainerInstall install, int index) throws Exception
  {
    stopContainer(index);
    return addContainer(install, index);
  }

  public String getContainerPort(int index)
  {
    LocalConfiguration config = getContainer(index).getConfiguration();
    config.applyPortOffset();
    return config.getPropertyValue(ServletPropertySet.PORT);
  }

  public int numContainers()
  {
    return containers.size();
  }

  public InstalledLocalContainer getContainer(int index)
  {
    return containers.get(index);
  }
  public ContainerInstall getContainerInstall(int index) { return installs.get(index); }
  public String getContainerDescription(int index) { return getContainerInstall(index).getContainerDescription() + ":" + getContainerPort(index); }
  public void startContainer(int index)
  {
    getContainer(index).start();
    System.out.println("Started container" + index + " " + getContainerDescription(index));
  }
  public void stopContainer(int index)
  {
//    try {
      getContainer(index).stop();
//    } catch (Exception e)
//    {
//      System.out.println(e);
//      for (InstalledLocalContainer container : containers)
//        System.out.println("Container: " + container.getState());
//    }
    System.out.println("Stopped container" + index + " " + getContainerDescription(index));
  }
  public void startContainers(int[] indexes)
  {
    for (int index : indexes)
      startContainer(index);
  }
  public void stopContainers(int[] indexes)
  {
    for (int index : indexes)
      stopContainer(index);
  }
  public void startAllContainers()
  {
    startContainers(IntStream.range(0, containers.size()).toArray());
  }
  public void stopAllContainers()
  {
    stopContainers(IntStream.range(0, containers.size()).toArray());
  }

  public void removeContainer(int index)
  {
    stopContainer(index);
    containers.remove(index);
    installs.remove(index);
  }
}
