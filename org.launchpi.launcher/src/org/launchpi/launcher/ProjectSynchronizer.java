package org.launchpi.launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.LinkedList;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.utils.IOUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.rse.subsystems.files.core.servicesubsystem.IFileServiceSubSystem;
import org.eclipse.rse.subsystems.files.core.subsystems.IRemoteFile;
import org.launchpi.launcher.i18n.Messages;

public class ProjectSynchronizer {

	public static final String REMOTE_FOLDER_NAME = ".launchpi_projects"; //$NON-NLS-1$
	
	private IProject project;

	private IFileServiceSubSystem fileServiceSubsystem;

	private String baseFolderName;

	public ProjectSynchronizer(IProject project, String baseFolderName, IFileServiceSubSystem fileServiceSubsystem) {
		this.project = project;
		this.baseFolderName = baseFolderName;
		this.fileServiceSubsystem = fileServiceSubsystem;
	}
	
	public void synchronize(IProgressMonitor monitor) throws Exception{
		monitor.subTask(Messages.Progress_Synchronizing_CP);
		IJavaProject javaProject = JavaCore.create(project);
		
		IRemoteFile baseFolder = fileServiceSubsystem.getRemoteFileObject(baseFolderName, monitor);
		if (baseFolder.exists()) {
			fileServiceSubsystem.delete(baseFolder, monitor);
		}
		fileServiceSubsystem.createFolders(baseFolder, monitor);

		ClasspathResolver resolver = new ClasspathResolver(javaProject);
		Collection<File> classpathEntries = resolver.resolve();
		File classpathArchive = createClasspathArchive(classpathEntries);
		
		IRemoteFile remoteEntry = fileServiceSubsystem.getRemoteFileObject(baseFolder, classpathArchive.getName(), monitor);
		fileServiceSubsystem.upload(classpathArchive.getCanonicalPath(), remoteEntry, null, monitor);
		classpathArchive.delete();
	}
	
	private File createClasspathArchive(Collection<File> classpathEntries) throws IOException {
		File archiveFile = new File(System.getProperty("java.io.tmpdir"), project.getName() + ".tar"); //$NON-NLS-1$ //$NON-NLS-2$
		if (archiveFile.exists()) {
			archiveFile.delete();
		}
		
		FileOutputStream fos = null;
		ArchiveOutputStream os = null;
		try {
			fos = new FileOutputStream(archiveFile);
			os = new ArchiveStreamFactory().createArchiveOutputStream(ArchiveStreamFactory.TAR, fos);
			LinkedList<String> pathElements = new LinkedList<String>();
			for (File f : classpathEntries) {
				if (f.isFile()) {
					pathElements.addLast("lib"); //$NON-NLS-1$
					writeClasspathEntry(pathElements, f, os);
				} else {
					pathElements.addLast("classes"); //$NON-NLS-1$
					for (File child : f.listFiles()) {
						writeClasspathEntry(pathElements, child, os);
					}
				}
				pathElements.removeLast();
			}
			return archiveFile;
		} catch (ArchiveException e) {
			throw new IOException("Failed to create classpath archive", e); //$NON-NLS-1$
		}finally {
			if (os != null) {
				os.close();
			}
			if (fos != null) {
				fos.close();
			}
		}
	}
	
	private void writeClasspathEntry(LinkedList<String> pathElements, File entry, ArchiveOutputStream os) throws IOException {
		if (entry.isFile()) {
			os.putArchiveEntry(new TarArchiveEntry(entry, getPath(pathElements) + "/" + entry.getName())); //$NON-NLS-1$
			copy(entry, os);
			os.closeArchiveEntry();
		} else {
			pathElements.addLast(entry.getName());
			for (File child : entry.listFiles()) {
				writeClasspathEntry(pathElements, child, os);
			}
			pathElements.removeLast();
		}
	}
	
	private void copy(File entry, OutputStream out) throws IOException {
		FileInputStream in = null;
		try {
			in = new FileInputStream(entry);
			IOUtils.copy(in, out);
		}finally {
			if (in != null) {
				in.close();
			}
		}
	}
	
	private static String getPath(LinkedList<String> pathElements) {
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < pathElements.size(); i++) {
			if (i != 0) {
				buf.append('/');
			}
			buf.append(pathElements.get(i));
		}
		return buf.toString();
	}
	
}
