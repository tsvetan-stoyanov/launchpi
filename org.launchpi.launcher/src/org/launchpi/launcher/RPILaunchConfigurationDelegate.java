package org.launchpi.launcher;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate;
import org.eclipse.ui.statushandlers.StatusManager;


public class RPILaunchConfigurationDelegate extends AbstractJavaLaunchConfigurationDelegate {

	@Override
	public void launch(ILaunchConfiguration configuration, String mode,
			ILaunch launch, IProgressMonitor monitor) throws CoreException {

		try {
			RemoteProcess remoteProcess = RemoteProcessFactory.createRemoteProcess(launch, this, configuration, mode, monitor);
			launch.addProcess(remoteProcess);
		} catch (Exception e) {
			StatusManager.getManager().handle(
					new Status(IStatus.ERROR, "org.launchpi.launcher", "Cannot start launch configuration", e), StatusManager.LOG);
		}
	}	
}