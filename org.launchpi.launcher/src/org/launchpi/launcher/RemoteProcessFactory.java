package org.launchpi.launcher;


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
import org.eclipse.rse.services.files.IFileService;
import org.eclipse.rse.services.shells.IHostShell;
import org.eclipse.rse.services.shells.IShellService;
import org.eclipse.rse.shells.ui.RemoteCommandHelpers;
import org.eclipse.rse.subsystems.files.core.servicesubsystem.IFileServiceSubSystem;
import org.eclipse.rse.subsystems.shells.core.subsystems.IRemoteCmdSubSystem;
import org.eclipse.rse.subsystems.shells.core.subsystems.servicesubsystem.IShellServiceSubSystem;
import org.launchpi.launcher.i18n.Messages;

public class RemoteProcessFactory {

	
	public static RemoteProcess createRemoteProcess(ILaunch launch, AbstractJavaLaunchConfigurationDelegate delegate, ILaunchConfiguration configuration, String mode, IProgressMonitor monitor) throws Exception{
		String cfgSystem = configuration.getAttribute(RPIConfigurationAttributes.SYSTEM, ""); //$NON-NLS-1$
		String cfgSystemProfileName = configuration.getAttribute(RPIConfigurationAttributes.SYSTEM_PROFILE, ""); //$NON-NLS-1$
		
		ISystemRegistry registry = RSECorePlugin.getTheSystemRegistry();
		IHost host = registry.getHost(registry.getSystemProfile(cfgSystemProfileName), cfgSystem);
		
		if (host == null) {
			throw new IllegalStateException(Messages.Host_Not_Found);
		}
		monitor.subTask(Messages.Progress_Init_Connection);
		IRemoteCmdSubSystem ss = RemoteCommandHelpers.getCmdSubSystem(host);
		ss.connect(monitor, false);
		monitor.worked(1);
				
		IShellService shellService = getShellService(host);
		IProject project = delegate.getJavaProject(configuration).getProject();
		ProjectSynchronizer synchronizer = new ProjectSynchronizer(project, host);
		synchronizer.synchronize(monitor);

		String cmd = buildCommandLine(delegate, configuration, mode);
		monitor.subTask(Messages.Progress_Launching_Java);
		IHostShell shell = shellService.runCommand(getFileService(host).getUserHome().getAbsolutePath() + "/" + ProjectSynchronizer.REMOTE_FOLDER_NAME, cmd, new String[0], new NullProgressMonitor()); //$NON-NLS-1$
		monitor.worked(1);
		return new RemoteProcess(launch, shell, ss);

	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static String buildCommandLine(AbstractJavaLaunchConfigurationDelegate delegate, ILaunchConfiguration configuration, String mode) throws CoreException {
		StringBuilder cmdBuf = new StringBuilder();
		
		boolean runAsRoot = configuration.getAttribute(RPIConfigurationAttributes.RUN_AS_ROOT,  RPIConfigurationAttributes.DEFAULT_RUN_AS_ROOT);
		if (runAsRoot) {
			cmdBuf.append("sudo "); //$NON-NLS-1$
		}
		
		Map<String, String> env = configuration.getAttribute(ILaunchManager.ATTR_ENVIRONMENT_VARIABLES, (Map) null);
		if (env != null) {
			for (Entry<String, String> entry : env.entrySet()) {
				String value = entry.getValue().replaceAll("\"", "\\\""); //$NON-NLS-1$ //$NON-NLS-2$
				cmdBuf.append(entry.getKey()).append("=\"").append(value).append("\" "); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		
		cmdBuf.append("java "); //$NON-NLS-1$
		
		if (ILaunchManager.DEBUG_MODE.equals(mode)) {
			int debugPort = configuration.getAttribute(RPIConfigurationAttributes.DEBUG_PORT, RPIConfigurationAttributes.DEFAULT_DEBUG_POST);
			cmdBuf.append(" -Xdebug -Xrunjdwp:transport=dt_socket,address=").append(debugPort).append(",server=y,suspend=y"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		for (String arg : DebugPlugin.parseArguments(delegate.getVMArguments(configuration))) {
			cmdBuf.append(' ').append(arg.trim());
		}
		cmdBuf.append(" -cp bin:lib/'*'"); //$NON-NLS-1$
		cmdBuf.append(' ').append(delegate.getMainTypeName(configuration));
		
		for (String arg : DebugPlugin.parseArguments(delegate.getProgramArguments(configuration))) {
			cmdBuf.append(' ').append(arg.trim());
		}

		cmdBuf.append(" ; exit"); //$NON-NLS-1$
		return cmdBuf.toString();
		
	}
	
	private static IShellService getShellService(IHost host) {
		for (ISubSystem subSystem : host.getSubSystems()) {
			if (subSystem instanceof IShellServiceSubSystem) {
				return ((IShellServiceSubSystem) subSystem).getShellService();
			}
		}
		throw new IllegalStateException(Messages.Shell_Service_Not_Found + host.getName());
	}
	
	private static IFileService getFileService(IHost host) throws Exception {
		for (ISubSystem subSystem : host.getSubSystems()) {
			if (subSystem instanceof IFileServiceSubSystem) {
				subSystem.connect(null, true);
				return ((IFileServiceSubSystem) subSystem).getFileService();
			}
		}
		throw new IllegalStateException(Messages.File_Service_Not_Found + host.getName());
	}
}
