package org.launchpi.launcher;


import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.internal.launching.JavaRemoteApplicationLaunchConfigurationDelegate;
import org.launchpi.launcher.i18n.Messages;

@SuppressWarnings("restriction")
public class RPIDebugConfigurationDelegate extends JavaRemoteApplicationLaunchConfigurationDelegate{

	@Override
	public void launch(ILaunchConfiguration configuration, String mode,
			ILaunch launch, IProgressMonitor monitor) throws CoreException {

		try {
			RemoteProcess remoteProcess = new RemoteProcessFactory(launch, this, configuration, mode).createRemoteProcess(monitor);
			launch.addProcess(remoteProcess);
			monitor.subTask(Messages.Progress_Waiting_Debug_Connection);
			Thread.sleep(3000);
			monitor.worked(1);
			super.launch(configuration, mode, launch, monitor);
		} catch (Exception e) {
			LaunchPlugin.reportError(Messages.Start_Failed, e);
		}
	}
}
