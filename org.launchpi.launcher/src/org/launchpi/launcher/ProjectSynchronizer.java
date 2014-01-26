package org.launchpi.launcher;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.rse.core.model.IHost;
import org.eclipse.rse.core.subsystems.ISubSystem;
import org.eclipse.rse.services.clientserver.messages.SystemElementNotFoundException;
import org.eclipse.rse.services.clientserver.messages.SystemMessageException;
import org.eclipse.rse.subsystems.files.core.servicesubsystem.IFileServiceSubSystem;
import org.eclipse.rse.subsystems.files.core.subsystems.IRemoteFile;
import org.launchpi.launcher.i18n.Messages;

public class ProjectSynchronizer {

	public static final String REMOTE_FOLDER_NAME = ".launchpi_projects"; //$NON-NLS-1$
	
	private IProject project;
	private IHost host;

	public ProjectSynchronizer(IProject project, IHost host) {
		this.project = project;
		this.host = host;
	}
	
	public void synchronize(IProgressMonitor monitor) throws Exception{
		IFileServiceSubSystem fileServiceSubsystem = null;
		try {
			monitor.subTask(Messages.Progress_Synchronizing_CP);
			fileServiceSubsystem = getFileServiceSubsystem();
			IJavaProject javaProject = JavaCore.create(project);
			
			String userHome = fileServiceSubsystem.getFileService().getUserHome().getAbsolutePath();
			IRemoteFile remoteUserHome = fileServiceSubsystem.getRemoteFileObject(userHome, monitor);
			IRemoteFile baseFolder = fileServiceSubsystem.getRemoteFileObject(remoteUserHome, REMOTE_FOLDER_NAME, monitor);
			try {
				fileServiceSubsystem.delete(baseFolder, monitor);
			} catch (SystemElementNotFoundException ex) {
				
			}

			IRemoteFile remoteBinFolder = fileServiceSubsystem.getRemoteFileObject(baseFolder, "bin", monitor); //$NON-NLS-1$
			IRemoteFile remoteLibFolder = fileServiceSubsystem.getRemoteFileObject(baseFolder, "lib", monitor); //$NON-NLS-1$
			fileServiceSubsystem.createFolders(remoteBinFolder, monitor);
			fileServiceSubsystem.createFolders(remoteLibFolder, monitor);

			synchronizeClasses(fileServiceSubsystem, javaProject, remoteBinFolder, monitor);
			synchronizeLibraries(fileServiceSubsystem, javaProject, remoteLibFolder, monitor);
		} finally {
			if (fileServiceSubsystem != null) {
				fileServiceSubsystem.uninitializeSubSystem(monitor);
			}
		}
	}
	
	private void synchronizeClasses(IFileServiceSubSystem fss, IJavaProject javaProject, IRemoteFile remoteFolder, IProgressMonitor monitor) throws JavaModelException, SystemMessageException, IOException {
		IPath outputLocation = javaProject.getOutputLocation();
		if (outputLocation != null) {
			File hostFile = javaProject.getProject().getWorkspace().getRoot().getFile(outputLocation).getLocation().toFile();
			RemoteFileUtils.copy(fss, hostFile, remoteFolder, monitor);
		}

		for (IClasspathEntry entry : javaProject.getResolvedClasspath(true)) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				outputLocation = entry.getOutputLocation();
				if (outputLocation != null) {
					File hostFile = javaProject.getProject().getWorkspace().getRoot().getFile(outputLocation).getLocation().toFile();
					RemoteFileUtils.copy(fss, hostFile, remoteFolder, monitor);
				}
			}
		}
	}

	private void synchronizeLibraries(IFileServiceSubSystem fss, IJavaProject javaProject, IRemoteFile remoteLibFolder, IProgressMonitor monitor) throws JavaModelException, SystemMessageException, IOException {
		for (IClasspathEntry entry: javaProject.getRawClasspath()) {
			if (entry.getEntryKind() != IClasspathEntry.CPE_LIBRARY) {
				continue;
			}
			
			entry = JavaCore.getResolvedClasspathEntry(entry);
			
			File entryFile = javaProject.getProject().getWorkspace().getRoot().getFile(entry.getPath()).getLocation().toFile();
			if (!entryFile.isDirectory()) {
				IRemoteFile remoteEntry = fss.getRemoteFileObject(remoteLibFolder, entryFile.getName(), monitor);
				fss.upload(entryFile.getCanonicalPath(), remoteEntry, null, monitor);
			}
		}
	}
	
	private IFileServiceSubSystem getFileServiceSubsystem() {
		for (ISubSystem subSystem : host.getSubSystems()) {
			if (subSystem instanceof IFileServiceSubSystem) {
				return (IFileServiceSubSystem) subSystem;
			}
		}
		throw new IllegalStateException(Messages.File_Service_Not_Found + host.getName());
	}
	
}
