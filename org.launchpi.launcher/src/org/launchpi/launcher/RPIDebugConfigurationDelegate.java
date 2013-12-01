package org.launchpi.launcher;


import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.internal.launching.JavaRemoteApplicationLaunchConfigurationDelegate;

public class RPIDebugConfigurationDelegate extends JavaRemoteApplicationLaunchConfigurationDelegate{

	
	//IJavaLaunchConfigurationConstants.ID_SOCKET_ATTACH_VM_CONNECTOR
	@Override
	public void launch(ILaunchConfiguration configuration, String mode,
			ILaunch launch, IProgressMonitor monitor) throws CoreException {

		try {
			RemoteProcess remoteProcess = RemoteProcessFactory.createRemoteProcess(launch, this, configuration, mode, monitor);
			launch.addProcess(remoteProcess);
			try {
				monitor.subTask("Waiting for debug connection to the remote host");
				Thread.sleep(3000);
				monitor.worked(1);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			super.launch(configuration, mode, launch, monitor);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
