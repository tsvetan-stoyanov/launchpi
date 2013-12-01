package org.launchpi.launcher;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.rse.core.model.IHost;
import org.eclipse.rse.core.subsystems.ISubSystem;
import org.eclipse.rse.services.clientserver.messages.SystemElementNotFoundException;
import org.eclipse.rse.services.files.IFileService;
import org.eclipse.rse.subsystems.files.core.servicesubsystem.IFileServiceSubSystem;

public class ProjectSynchronizer {

	private IProject project;
	private IHost host;

	public ProjectSynchronizer(IProject project, IHost host) {
		this.project = project;
		this.host = host;
	}
	
	public void synchronize(IProgressMonitor monitor) throws Exception{
		IFileService fileService = null;
		try {
			monitor.subTask("Synchronizing project classpath");
			fileService = getFileService();
			fileService.initService(null);
			IJavaProject javaProject = JavaCore.create(project);
			IPath outputLocation = javaProject.getOutputLocation();
			IFolder outputFolder = ResourcesPlugin.getWorkspace().getRoot().getFolder(outputLocation);
			List<String> parents = new ArrayList<String>();
			List<String> files = new ArrayList<String>();
			processOutputFolder(outputFolder, parents, files);
			
			try {
				fileService.delete("/home/pi", ".rpi_comp", null);
			} catch (SystemElementNotFoundException ex) {
				
			}
			
			fileService.createFolder("/home/pi", ".rpi_comp", null);
			File[] targetFiles = listFilesDeep(outputFolder.getLocation().toFile());
			String[] remoteParents = new String[parents.size()];
			int ndx = 0;
			for (String parent : parents) {
				remoteParents[ndx++] = "/home/pi/.rpi_comp" + parent;
				if (parent.startsWith("/")) {
					parent = parent.substring(1);
				}
				fileService.createFolder("/home/pi/.rpi_comp", parent, null);
			}
			String[] remoteFiles = files.toArray(new String[files.size()]);
			boolean[] binary = new boolean[targetFiles.length];
			Arrays.fill(binary, true);
			String[] empty = new String[targetFiles.length];
			Arrays.fill(empty, "");
			fileService.uploadMultiple(targetFiles, remoteParents, remoteFiles, binary, empty, empty, new NullProgressMonitor());
			monitor.worked(1);
		} finally {
			if (fileService != null) {
				fileService.uninitService(null);
			}
		}
	}
	
	private IFileService getFileService() {
		for (ISubSystem subSystem : host.getSubSystems()) {
			if (subSystem instanceof IFileServiceSubSystem) {
				//((IFileServiceSubSystem) subSystem).connect(new NullProgressMonitor(), false);
				return ((IFileServiceSubSystem) subSystem).getFileService();
			}
		}
		throw new IllegalStateException("File service not found for host " + host.getName());
	}
	
	private void processOutputFolder(IFolder outputFolder, List<String> parents, List<String> files) throws CoreException {
		processFolder(outputFolder, outputFolder, parents, files);
	}
	
	private void processFolder(IFolder rootFolder, IFolder folder, List<String> parents, List<String> files) throws CoreException {
		for (IResource member : folder.members()) {
			if (member.getType() == IResource.FOLDER) {
				processFolder(rootFolder, (IFolder) member, parents, files);
			} else if (member.getType() == IResource.FILE) {
				files.add(member.getName());
				int rootSize = rootFolder.getLocation().toString().length();
				parents.add(member.getParent().getLocation().toString().substring(rootSize));
			}
		}
	}
	
	private static File[] listFilesDeep(File parent) {
		List<File> files = new ArrayList<File>();
		listDeep(parent, files);
		return files.toArray(new File[files.size()]);
	}
	
	private static void listDeep(File parent, List<File> files) {
		for (File file : parent.listFiles()) {
			if (file.isDirectory()) {
				listDeep(file, files);
			} else {
				files.add(file);
			}
		}
	}
}
