package org.launchpi.launcher;


import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.eclipse.debug.core.model.IStreamsProxy;
import org.eclipse.rse.core.RSECorePlugin;
import org.eclipse.rse.core.events.ISystemResourceChangeEvent;
import org.eclipse.rse.core.events.ISystemResourceChangeEvents;
import org.eclipse.rse.core.events.ISystemResourceChangeListener;
import org.eclipse.rse.core.events.SystemResourceChangeEvent;
import org.eclipse.rse.core.model.ISystemRegistry;
import org.eclipse.rse.services.shells.AbstractHostShellOutputReader;
import org.eclipse.rse.services.shells.IHostShell;
import org.eclipse.rse.subsystems.shells.core.subsystems.IRemoteCmdSubSystem;

public class RemoteProcess implements IProcess, ISystemResourceChangeListener{
	
	private Map<String, String> attributes;
	
	private ILaunch launch;

	private IHostShell shell;

	private RemoteProcessStreamsProxy streamsProxy;
	
	private boolean terminated;
	
	private IRemoteCmdSubSystem cmdSubSystem;

	public RemoteProcess(ILaunch launch, IHostShell shell, IRemoteCmdSubSystem cmdSubSystem) {
		this.launch = launch;
		this.shell = shell;
		this.cmdSubSystem = cmdSubSystem;
		attributes = new HashMap<String, String>();
		
		streamsProxy = new RemoteProcessStreamsProxy(shell);
		streamsProxy.getOutputStreamMonitor().addListener(new OutputStreamListener());
		getSystemRegistry().addSystemResourceChangeListener(this);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Object getAdapter(Class adapter) {
		return null;
	}

	@Override
	public boolean canTerminate() {
		return !terminated;
	}

	@Override
	public boolean isTerminated() {
		return !shell.isActive();
	}

	@Override
	public void terminate() throws DebugException {
		streamsProxy.closeInputStream();
		shell.exit();
		((AbstractHostShellOutputReader) shell.getStandardOutputReader()).interrupt();
		((AbstractHostShellOutputReader) shell.getStandardErrorReader()).interrupt();
		try {
			cmdSubSystem.disconnect();
		} catch (Exception e) {
			throw new DebugException(new Status(
					IStatus.WARNING, LaunchPlugin.PLUGIN_ID, "Cannot disconnect command subsystem", e)); //$NON-NLS-1$
		}
		terminated = true;
		getSystemRegistry().removeSystemResourceChangeListener(this);
		DebugPlugin.getDefault().fireDebugEventSet(new DebugEvent[] {new DebugEvent(this, DebugEvent.TERMINATE)});
	}

	@Override
	public String getLabel() {
		return "Raspberry PI Process"; //$NON-NLS-1$
	}

	@Override
	public ILaunch getLaunch() {
		return launch;
	}

	@Override
	public IStreamsProxy getStreamsProxy() {
		return streamsProxy;
	}

	@Override
	public void setAttribute(String key, String value) {
		attributes.put(key, value);
		
	}

	@Override
	public String getAttribute(String key) {
		return attributes.get(key);
	}

	@Override
	public int getExitValue() throws DebugException {
		return 0;
	}
	
	public void systemResourceChanged(ISystemResourceChangeEvent evt) {
		if (evt.getType() == ISystemResourceChangeEvents.EVENT_COMMAND_SHELL_FINISHED) {
			Object src = evt.getSource();
			if (this.equals(src)) { 
				try {
					terminate();
				} catch (DebugException e) {
					throw new IllegalStateException(e);
				}
			}
		}
	}
	
	private ISystemRegistry getSystemRegistry() {
		return RSECorePlugin.getTheSystemRegistry();
	}
	
	private class OutputStreamListener implements IStreamListener {

		@Override
		public void streamAppended(String text, IStreamMonitor monitor) {
			if (text.length() == 0 && !shell.isActive()) {
				getSystemRegistry().fireEvent(
						new SystemResourceChangeEvent(RemoteProcess.this, ISystemResourceChangeEvents.EVENT_COMMAND_SHELL_FINISHED, cmdSubSystem));
			}
		}
		
	}

}
