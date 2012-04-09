package org.bimserver.test.framework;

/******************************************************************************
 * Copyright (C) 2009-2012  BIMserver.org
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.bimserver.BimServer;
import org.bimserver.BimServerConfig;
import org.bimserver.LocalDevPluginLoader;
import org.bimserver.models.store.ServerState;
import org.bimserver.shared.LocalDevelopmentResourceFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestFramework {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestFramework.class);
	
	private final List<File> files = new ArrayList<File>();
	private final Set<VirtualUser> virtualUsers = new HashSet<VirtualUser>();
	private final Random random = new Random();
	private final TestConfiguration testConfiguration;

	private BimServer bimServer;
	
	public TestFramework(TestConfiguration testConfiguration) {
		this.testConfiguration = testConfiguration;
	}
	
	public void start() {
		if (testConfiguration.isStartEmbeddedBimServer()) {
			File home = new File("home");
			if (testConfiguration.isCleanEnvironmentFirst()) {
				try {
					FileUtils.forceDelete(home);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			BimServerConfig bimServerConfig = new BimServerConfig();
			bimServerConfig.setStartEmbeddedWebServer(true);
			bimServerConfig.setHomeDir(home);
			bimServerConfig.setPort(8080);
			bimServerConfig.setResourceFetcher(new LocalDevelopmentResourceFetcher());
			bimServerConfig.setClassPath(System.getProperty("java.class.path"));
			bimServer = new BimServer(bimServerConfig);
			try {
				LocalDevPluginLoader.loadPlugins(bimServer.getPluginManager());
				bimServer.start();
				// Convenience, setup the server to make sure it is in RUNNING state
				if (bimServer.getServerInfo().getServerState() == ServerState.NOT_SETUP) {
					bimServer.getSystemService().setup("http://localhost", "localhost", "Administrator", "admin@bimserver.org", "admin");
				}
				
				// Change a setting to normal users can create projects
				bimServer.getSettingsManager().getSettings().setAllowUsersToCreateTopLevelProjects(true);
			} catch (Exception e) {
				LOGGER.error("", e);
			}
		}
		if (!testConfiguration.getOutputFolder().exists()) {
			testConfiguration.getOutputFolder().mkdir();
		}
		initFiles();
		VirtualUserFactory virtualUserFactory = new VirtualUserFactory(this, testConfiguration.getBimServerClientFactory());
		for (int i=0; i<testConfiguration.getNrVirtualUsers(); i++) {
			VirtualUser virtualUser = virtualUserFactory.create("" + i);
			virtualUsers.add(virtualUser);
		}
		for (VirtualUser virtualUser : virtualUsers) {
			virtualUser.start();
		}
	}

	private void initFiles() {
		File ifcFiles = testConfiguration.getIfcFiles();
		if (ifcFiles.isDirectory()) {
			for (File file : ifcFiles.listFiles()) {
				if (file.isFile() && !file.getName().contains(".svn")) {
					files.add(file);
				}
			}
		} else {
			files.add(ifcFiles);
		}
	}

	public synchronized File getRandomFile() {
		return files.get(random.nextInt(files.size()));
	}

	public synchronized void unsubsribe(VirtualUser virtualUser) {
		virtualUsers.remove(virtualUser);
		if (virtualUsers.isEmpty()) {
			if (testConfiguration.isStartEmbeddedBimServer()) {
				bimServer.stop();
			}
		}
	}

	public TestConfiguration getTestConfiguration() {
		return testConfiguration;
	}
}