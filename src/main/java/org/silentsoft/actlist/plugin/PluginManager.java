package org.silentsoft.actlist.plugin;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.silentsoft.actlist.BizConst;
import org.silentsoft.actlist.CommonConst;
import org.silentsoft.actlist.application.App;
import org.silentsoft.actlist.plugin.messagebox.MessageBox;
import org.silentsoft.core.util.ObjectUtil;
import org.silentsoft.io.memory.SharedMemory;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.VBox;

public class PluginManager {

	public static boolean install(File file) throws Exception {
		if (file == null) {
			return false;
		}
		
		HashMap<String, URLClassLoader> pluginMap = (HashMap<String, URLClassLoader>) SharedMemory.getDataMap().get(BizConst.KEY_PLUGIN_MAP);
		
		boolean shouldCopy = true;
		if (file.getPath().equals(Paths.get(System.getProperty("user.dir"), "plugins", file.getName()).toString())) {
			if (pluginMap.containsKey(file.getName())) {
				MessageBox.showError(App.getStage(), "You can not select an already loaded plugin !");
				return false;
			}
			
			shouldCopy = false;
		}
		Path source = Paths.get(file.toURI());
		Path target = Paths.get(System.getProperty("user.dir"), "plugins", file.getName());
		if (shouldCopy) {
			Files.copy(source, target);
		}
		
		URLClassLoader urlClassLoader = null;
		
		boolean isErrorOccur = false;
		try {
			Class<?> pluginClass = null;
			InputStream inputStream = null;
			
			urlClassLoader = new URLClassLoader(new URL[]{ target.toUri().toURL() });
			
			try {
				URL manifestURL = urlClassLoader.findResource(JarFile.MANIFEST_NAME);
				inputStream = manifestURL.openStream();
				
				Manifest manifest = new Manifest(inputStream);
				String mainClass = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS).trim();
				if (ObjectUtil.isNotEmpty(mainClass)) {
					pluginClass = urlClassLoader.loadClass(mainClass);
				}
			} catch (Exception | Error e) {
				e.printStackTrace();
			} finally {
				if (pluginClass == null) {
					pluginClass = urlClassLoader.loadClass(BizConst.PLUGIN_CLASS_NAME);
				}
				
				if (inputStream != null) {
					inputStream.close();
				}
			}
			
			if (ActlistPlugin.class.isAssignableFrom(pluginClass) == false) {
				isErrorOccur = true;
			}
		} catch (Exception | Error e) {
			e.printStackTrace();
			isErrorOccur = true;
		} finally {
			if (urlClassLoader != null) {
				// close the file handle. this file will be loaded within load() method. not this time.
				urlClassLoader.close();
			}
		}
		
		if (isErrorOccur) {
			if (shouldCopy) {
				// remove it if copied
				Files.delete(target);
			}
			
			MessageBox.showError(App.getStage(), "This file is not kind of Actlist plugin !");
			return false;
		}
		
		return true;
	}
	
	public static void delete(String pluginFileName) throws Exception {
		unload(pluginFileName);
		Files.delete(Paths.get(System.getProperty("user.dir"), "plugins", pluginFileName));
	}
	
	public static void load(String pluginFileName, boolean activated) throws Exception {
		load(pluginFileName, activated, null);
	}
	
	public static void load(String pluginFileName, boolean activated, Integer index) throws Exception {
		URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{ Paths.get(System.getProperty("user.dir"), "plugins", pluginFileName).toUri().toURL() });
		
		Class<?> pluginClass = null;
		InputStream inputStream = null;
		
		try {
			URL manifestURL = urlClassLoader.findResource(JarFile.MANIFEST_NAME);
			inputStream = manifestURL.openStream();
			
			Manifest manifest = new Manifest(inputStream);
			String mainClass = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS).trim();
			if (ObjectUtil.isNotEmpty(mainClass)) {
				pluginClass = urlClassLoader.loadClass(mainClass);
			}
		} catch (Exception | Error e) {
			e.printStackTrace();
		} finally {
			if (pluginClass == null) {
				pluginClass = urlClassLoader.loadClass(BizConst.PLUGIN_CLASS_NAME);
			}
			
			if (inputStream != null) {
				inputStream.close();
			}
		}
		
		if (ActlistPlugin.class.isAssignableFrom(pluginClass)) {
			HashMap<String, URLClassLoader> pluginMap = (HashMap<String, URLClassLoader>) SharedMemory.getDataMap().get(BizConst.KEY_PLUGIN_MAP);
			boolean shouldClearPromptLabel = (pluginMap.size() == 0);
			pluginMap.put(pluginFileName, urlClassLoader);
			
			FXMLLoader fxmlLoader = new FXMLLoader(PluginComponent.class.getResource(PluginComponent.class.getSimpleName().concat(CommonConst.EXTENSION_FXML)));
			Node component = fxmlLoader.load();
			PluginComponent pluginComponent = ((PluginComponent) fxmlLoader.getController());
			
			pluginComponent.initialize(pluginFileName, (Class<? extends ActlistPlugin>) pluginClass, activated);
			component.setUserData(pluginComponent);
			
			VBox componentBox = (VBox) SharedMemory.getDataMap().get(BizConst.KEY_COMPONENT_BOX);
			if (shouldClearPromptLabel) {
				// remove the 'No plugins available.' prompt label
				componentBox.getChildren().clear();
			}
			if (index == null) {
				componentBox.getChildren().add(component);
			} else {
				componentBox.getChildren().add(index, component);
			}
		}
	}
	
	public static void unload(String pluginFileName) throws Exception {
		VBox componentBox = (VBox) SharedMemory.getDataMap().get(BizConst.KEY_COMPONENT_BOX);
		for (int i=0, j=componentBox.getChildren().size(); i<j; i++) {
			PluginComponent pluginComponent = (PluginComponent) componentBox.getChildren().get(i).getUserData();
			if (pluginComponent.getPluginFileName().equals(pluginFileName)) {
				pluginComponent.clear();
				
				componentBox.getChildren().remove(i);
				
				HashMap<String, URLClassLoader> pluginMap = (HashMap<String, URLClassLoader>) SharedMemory.getDataMap().get(BizConst.KEY_PLUGIN_MAP);
				pluginMap.get(pluginFileName).close();
				pluginMap.remove(pluginFileName);
				
				break;
			}
		}
	}
	
}
