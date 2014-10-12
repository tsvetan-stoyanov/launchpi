package org.launchpi.launcher;


import java.io.IOException;

import org.eclipse.core.runtime.ListenerList;
import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.eclipse.debug.core.model.IStreamsProxy2;
import org.eclipse.rse.services.shells.IHostOutput;
import org.eclipse.rse.services.shells.IHostShell;
import org.eclipse.rse.services.shells.IHostShellChangeEvent;
import org.eclipse.rse.services.shells.IHostShellOutputListener;
import org.eclipse.rse.services.shells.IHostShellOutputReader;

public class RemoteProcessStreamsProxy implements IStreamsProxy2{
	
	private IHostShell shell;
	private StreamMonitor outputStreamMonitor;
	private StreamMonitor errorStreamMonitor;

	public RemoteProcessStreamsProxy(IHostShell shell) {
		this.shell = shell;
		outputStreamMonitor = new StreamMonitor(shell.getStandardOutputReader());
		errorStreamMonitor = new StreamMonitor(shell.getStandardErrorReader());
	}

	@Override
	public IStreamMonitor getErrorStreamMonitor() {
		return errorStreamMonitor;
	}

	@Override
	public IStreamMonitor getOutputStreamMonitor() {
		return outputStreamMonitor;
	}

	@Override
	public void write(String input) throws IOException {
		shell.writeToShell(input);
	}

	@Override
	public void closeInputStream() {
		outputStreamMonitor.stop();
		errorStreamMonitor.stop();
	}

	private static class StreamMonitor implements IStreamMonitor, IHostShellOutputListener {
		
		private ListenerList listeners;
		
		private StringBuilder contents;

		private IHostShellOutputReader shellReader;

		public StreamMonitor(IHostShellOutputReader shellReader) {
			this.shellReader = shellReader;
			shellReader.addOutputListener(this);
			listeners = new ListenerList();
			contents = new StringBuilder();
		}
		
		@Override
		public void addListener(IStreamListener listener) {
			listeners.add(listener);
		}

		@Override
		public String getContents() {
			return contents.toString();
		}

		@Override
		public void removeListener(IStreamListener listener) {
			listeners.remove(listener);
		}

		@Override
		public void shellOutputChanged(IHostShellChangeEvent event) {
			IHostOutput[] lines = event.getLines();
			StringBuilder buf = new StringBuilder();
			for (IHostOutput line : lines) {
				String lineContent = line.getString();
				if (lineContent.length() == 0) {
					continue;
				}
				buf.append(lineContent);
				buf.append(System.getProperty("line.separator"));
			}
			
			String newContent = null;
			if (buf.length() != 0) {
				newContent = buf.toString();
				contents.append(newContent);
			} else {
				newContent = "";
			}
			
			for (Object listener : listeners.getListeners()) {
				IStreamListener streamListener = (IStreamListener) listener;
				streamListener.streamAppended(newContent, this);
			}

		}
		
		public void stop() {
			shellReader.finish();
		}
		
	}
}
