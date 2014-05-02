package org.launchpi.launcher;

import java.net.URL;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.ui.statushandlers.StatusManager;
import org.osgi.framework.Bundle;

public class LaunchPlugin extends AbstractUIPlugin {
	
	public static final String PLUGIN_ID = "org.launchpi.launcher"; //$NON-NLS-1$
	
	private static LaunchPlugin INSTANCE;

	public LaunchPlugin() {
		super();
		INSTANCE = this;
	}
	
	public static LaunchPlugin getDefault() {
		return INSTANCE;
	}
	
	@Override
	protected void initializeImageRegistry(ImageRegistry reg) {
        Bundle bundle = Platform.getBundle(PLUGIN_ID);
        IPath path = new Path("images/rpitab.png");
        URL url = FileLocator.find(bundle, path, null);
        ImageDescriptor desc = ImageDescriptor.createFromURL(url);
        reg.put("rpi_tab", desc);
	}
	
	public Image getRPITabImage() {
		return getImageRegistry().get("rpi_tab");
	}
	
	public static void reportError(String message, Exception cause) {
		IStatus status = new Status(IStatus.ERROR, PLUGIN_ID, message, cause);
		StatusManager statusManager = StatusManager.getManager();
		statusManager.handle(status, StatusManager.LOG);
		statusManager.handle(status, StatusManager.BLOCK);
	}

}
