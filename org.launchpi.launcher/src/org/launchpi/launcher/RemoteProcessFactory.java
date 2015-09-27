package org.launchpi.launcher;


import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate;
import org.eclipse.rse.core.RSECorePlugin;
import org.eclipse.rse.core.model.IHost;
import org.eclipse.rse.core.model.ISystemRegistry;
import org.eclipse.rse.core.subsystems.ISubSystem;
import org.eclipse.rse.services.shells.IHostShell;
import org.eclipse.rse.services.shells.IShellService;
import org.eclipse.rse.shells.ui.RemoteCommandHelpers;
import org.eclipse.rse.subsystems.files.core.servicesubsystem.IFileServiceSubSystem;
import org.eclipse.rse.subsystems.shells.core.subsystems.IRemoteCmdSubSystem;
import org.eclipse.rse.subsystems.shells.core.subsystems.servicesubsystem.IShellServiceSubSystem;
import org.launchpi.launcher.i18n.Messages;

public class RemoteProcessFactory {
	
	private AbstractJavaLaunchConfigurationDelegate delegate;
	private ILaunchConfiguration configuration;
	private String mode;
	private ILaunch launch;

	public RemoteProcessFactory(ILaunch launch, AbstractJavaLaunchConfigurationDelegate delegate, ILaunchConfiguration configuration, String mode) {
		this.launch = launch;
		this.delegate = delegate;
		this.configuration = configuration;
		this.mode = mode;
	}
	
	public RemoteProcess createRemoteProcess(IProgressMonitor monitor) throws Exception{
		String cfgSystem = configuration.getAttribute(RPIConfigurationAttributes.SYSTEM, ""); //$NON-NLS-1$
		String cfgSystemProfileName = configuration.getAttribute(RPIConfigurationAttributes.SYSTEM_PROFILE, ""); //$NON-NLS-1$
		
		ISystemRegistry registry = RSECorePlugin.getTheSystemRegistry();
		IHost host = registry.getHost(registry.getSystemProfile(cfgSystemProfileName), cfgSystem);
		
		if (host == null) {
			throw new IllegalStateException(Messages.Host_Not_Found);
		}
		
		IFileServiceSubSystem fileServiceSubsystem = null;
		try {
			monitor.subTask(Messages.Progress_Init_Connection);
			IRemoteCmdSubSystem cmdSubSystem = RemoteCommandHelpers.getCmdSubSystem(host);
			cmdSubSystem.connect(monitor, false);
			monitor.worked(1);
	
			fileServiceSubsystem = getFileServiceSubsystem(host);
			IProject project = delegate.getJavaProject(configuration).getProject();
			String workingFolder = getWorkingFolder(fileServiceSubsystem, project);
			String homeFolder = fileServiceSubsystem.getFileService().getUserHome().getAbsolutePath();
			ProjectSynchronizer synchronizer = new ProjectSynchronizer(project, workingFolder, fileServiceSubsystem, cmdSubSystem);
			synchronizer.synchronize(monitor);
	
			String cmd = buildCommandLine(homeFolder);
			monitor.subTask(Messages.Progress_Launching_Java);
			IShellService shellService = getShellService(host);
			IHostShell shell = shellService.runCommand(workingFolder, cmd, new String[0], new NullProgressMonitor()); //$NON-NLS-1$
			monitor.worked(1);
			return new RemoteProcess(launch, shell, cmdSubSystem);
		} finally {
			if (fileServiceSubsystem != null) {
				fileServiceSubsystem.uninitializeSubSystem(monitor);
			}
		}
	}
	
	private String buildCommandLine(String homeFolder) throws CoreException {
		StringBuilder cmdBuf = new StringBuilder();
		addRunAsRootOption(cmdBuf);
		addEnvironmentVariables(cmdBuf, homeFolder);
		cmdBuf.append(" java "); //$NON-NLS-1$
		addDebugOptions(cmdBuf);
		addVMArguments(cmdBuf);
		addClasspath(cmdBuf);
		addMainType(cmdBuf);
		addArguments(cmdBuf);
		cmdBuf.append(" ; exit"); //$NON-NLS-1$
		return cmdBuf.toString();
	}
	
	private String getWorkingFolder(IFileServiceSubSystem fileServiceSubsystem, IProject project) throws CoreException {
		return MessageFormat.format("{0}/{1}/{2}",  //$NON-NLS-1$
				fileServiceSubsystem.getFileService().getUserHome().getAbsolutePath(), ProjectSynchronizer.REMOTE_FOLDER_NAME, getProjectName());
	}

	private IShellService getShellService(IHost host) {
		for (ISubSystem subSystem : host.getSubSystems()) {
			if (subSystem instanceof IShellServiceSubSystem) {
				return ((IShellServiceSubSystem) subSystem).getShellService();
			}
		}
		throw new IllegalStateException(Messages.Shell_Service_Not_Found + host.getName());
	}
	
	private IFileServiceSubSystem getFileServiceSubsystem(IHost host) {
		for (ISubSystem subSystem : host.getSubSystems()) {
			if (subSystem instanceof IFileServiceSubSystem) {
				return (IFileServiceSubSystem) subSystem;
			}
		}
		throw new IllegalStateException(Messages.File_Service_Not_Found + host.getName());
	}
	
	private void addRunAsRootOption(StringBuilder cmdBuf) throws CoreException {
		if (getRunAsRoot()) {
			cmdBuf.append(" sudo "); //$NON-NLS-1$
		}
	}
	
	private boolean getRunAsRoot() throws CoreException {
		return configuration.getAttribute(RPIConfigurationAttributes.RUN_AS_ROOT,  RPIConfigurationAttributes.DEFAULT_RUN_AS_ROOT);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void addEnvironmentVariables(StringBuilder buf, String homeFolder) throws CoreException {
		Map<String, String> env = configuration.getAttribute(ILaunchManager.ATTR_ENVIRONMENT_VARIABLES, (Map) null);
		if (env == null) {
			env = new HashMap<String, String>();
		}
		addDisplayVariables(env, homeFolder);
		buf.append(" "); //$NON-NLS-1$
		for (Entry<String, String> entry : env.entrySet()) {
			String value = entry.getValue().replaceAll("\"", "\\\""); //$NON-NLS-1$ //$NON-NLS-2$
			buf.append(entry.getKey()).append("=\"").append(value).append("\" "); //$NON-NLS-1$ //$NON-NLS-2$
		}
		buf.append(" "); //$NON-NLS-1$
	}
	
	private void addDisplayVariables(Map<String, String> env, String homeFolder) throws CoreException {
		if (!env.containsKey("DISPLAY")) {
			String display = configuration.getAttribute(RPIConfigurationAttributes.DISPLAY, RPIConfigurationAttributes.DEFAULT_DISPLAY);
			if (display.length() != 0) {
				env.put("DISPLAY", display);	
				if (getRunAsRoot() && !env.containsKey("XAUTHORITY")) {
					env.put("XAUTHORITY", homeFolder + "/.Xauthority");
				}
			}
		}
	}
	
	private void addDebugOptions(StringBuilder buf) throws CoreException {
		if (ILaunchManager.DEBUG_MODE.equals(mode)) {
			int debugPort = configuration.getAttribute(RPIConfigurationAttributes.DEBUG_PORT, RPIConfigurationAttributes.DEFAULT_DEBUG_POST);
			buf.append(" -Xdebug -Xrunjdwp:transport=dt_socket,address=").append(debugPort).append(",server=y,suspend=y "); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
	
	private void addVMArguments(StringBuilder cmdBuf) throws CoreException {
		for (String arg : DebugPlugin.parseArguments(delegate.getVMArguments(configuration))) {
			cmdBuf.append(' ').append(arg.trim());
		}
	}
	
	private void addClasspath(StringBuilder cmdBuf) throws CoreException {
		cmdBuf.append(" -cp classes:lib/'*' "); //$NON-NLS-1$
	}
	
	private String getProjectName() throws CoreException {
		return delegate.getJavaProject(configuration).getProject().getName();
	}
	
	private void addMainType(StringBuilder cmdBuf) throws CoreException {
		cmdBuf.append(" ").append(delegate.getMainTypeName(configuration)).append(" "); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	private void addArguments(StringBuilder cmdBuf) throws CoreException {
		for (String arg : DebugPlugin.parseArguments(delegate.getProgramArguments(configuration))) {
			cmdBuf.append(' ').append(arg.trim());
		}
	}
}
