package org.launchpi.launcher;


import java.io.IOException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.eclipse.jdt.internal.launching.JavaRemoteApplicationLaunchConfigurationDelegate;
import org.launchpi.launcher.i18n.Messages;

@SuppressWarnings("restriction")
public class RPIDebugConfigurationDelegate extends JavaRemoteApplicationLaunchConfigurationDelegate{
	
	private static final String DEBUG_READY_MESSAGE = "Listening for transport dt_socket at address:";
	
	private static final int REMOTE_VM_ATTACH_TIMEOUT = 30000;

	@Override
	public void launch(ILaunchConfiguration configuration, String mode,
			ILaunch launch, IProgressMonitor monitor) throws CoreException {

		try {
			RemoteProcess remoteProcess = new RemoteProcessFactory(launch, this, configuration, mode).createRemoteProcess(monitor);
			DebugReadyListener debugReadyListener = new DebugReadyListener();
			IStreamMonitor outputStreamMonitor = remoteProcess.getStreamsProxy().getOutputStreamMonitor();
			outputStreamMonitor.addListener(debugReadyListener);
			launch.addProcess(remoteProcess);
			monitor.subTask(Messages.Progress_Waiting_Debug_Connection);
			long maxTime = System.currentTimeMillis() + REMOTE_VM_ATTACH_TIMEOUT;
			while (!remoteProcess.isTerminated()) {
				if (System.currentTimeMillis() > maxTime) {
					remoteProcess.terminate();
					throw new IOException(Messages.Debug_Connection_Failed);
				}
				if (debugReadyListener.isRemoteVmReadyForDebug()) {
					outputStreamMonitor.removeListener(debugReadyListener);
					super.launch(configuration, mode, launch, monitor);
					break;
				}
			}
			monitor.worked(1);
		} catch (Exception e) {
			LaunchPlugin.reportError(Messages.Start_Failed, e);
		}
	}

	private class DebugReadyListener implements IStreamListener {
		
		private boolean remoteVmReadyForDebug;
		
		public boolean isRemoteVmReadyForDebug() {
			return remoteVmReadyForDebug;
		}

		@Override
		public void streamAppended(String text, IStreamMonitor monitor) {
			if (!remoteVmReadyForDebug && text.startsWith(DEBUG_READY_MESSAGE)) {
				remoteVmReadyForDebug = true;
			}
		}
		
	}
}
