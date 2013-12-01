package org.launchpi.launcher;


import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
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
import org.eclipse.rse.subsystems.shells.core.subsystems.IRemoteCmdSubSystem;
import org.eclipse.rse.subsystems.shells.core.subsystems.servicesubsystem.IShellServiceSubSystem;

public class RemoteProcessFactory {

	
	public static RemoteProcess createRemoteProcess(ILaunch launch, AbstractJavaLaunchConfigurationDelegate delegate, ILaunchConfiguration configuration, String mode, IProgressMonitor monitor) throws Exception{
		String cfgSystem = configuration.getAttribute("rpiSystem", "");
		String cfgSystemProfileName = configuration.getAttribute("rpiSystemProfile", "");
		String mainClass = configuration.getAttribute("org.eclipse.jdt.launching.MAIN_TYPE", "");
		String projectName = configuration.getAttribute("org.eclipse.jdt.launching.PROJECT_ATTR", "");
		
		ISystemRegistry registry = RSECorePlugin.getTheSystemRegistry();
		IHost host = registry.getHost(registry.getSystemProfile(cfgSystemProfileName), cfgSystem);
		
		monitor.subTask("Initializing connection to remote host");
		IRemoteCmdSubSystem ss = RemoteCommandHelpers.getCmdSubSystem(host);
		ss.connect(monitor, false);
		monitor.worked(1);
				
		IShellService shellService = getShellService(host);
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		ProjectSynchronizer synchronizer = new ProjectSynchronizer(project, host);
		synchronizer.synchronize(monitor);

		String javaCmd = null;
		if (ILaunchManager.RUN_MODE.equals(mode)) {
			javaCmd = "java " + mainClass;
		} else if (ILaunchManager.DEBUG_MODE.equals(mode)) {
			String debugPort = String.valueOf(configuration.getAttribute("rpiSystemDebugPort", 0));
			javaCmd = "java -Xdebug -Xrunjdwp:transport=dt_socket,address=" + debugPort + ",server=y,suspend=y " + mainClass;
		}
		
		monitor.subTask("Launching remote java process");
		IHostShell shell = shellService.runCommand("/home/pi/.rpi_comp", javaCmd + " && exit", new String[0], new NullProgressMonitor());
		monitor.worked(1);
		return new RemoteProcess(launch, shell, ss);

	}
	
	private static IShellService getShellService(IHost host) {
		for (ISubSystem subSystem : host.getSubSystems()) {
			if (subSystem instanceof IShellServiceSubSystem) {
				return ((IShellServiceSubSystem) subSystem).getShellService();
			}
		}
		throw new IllegalStateException("Cannot find shell service for host " + host.getName());
	}
}
