package org.launchpi.launcher;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate;
import org.launchpi.launcher.i18n.Messages;


public class RPILaunchConfigurationDelegate extends AbstractJavaLaunchConfigurationDelegate {

	@Override
	public void launch(ILaunchConfiguration configuration, String mode,
			ILaunch launch, IProgressMonitor monitor) throws CoreException {

		try {
			RemoteProcess remoteProcess = new RemoteProcessFactory(launch, this, configuration, mode).createRemoteProcess(monitor);
			launch.addProcess(remoteProcess);
		} catch (Exception e) {
			LaunchPlugin.reportError(Messages.Start_Failed, e);
		}
	}	
}