package org.launchpi.launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.utils.IOUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.rse.services.clientserver.messages.SystemMessageException;
import org.eclipse.rse.services.files.IFileService;
import org.eclipse.rse.subsystems.files.core.servicesubsystem.IFileServiceSubSystem;
import org.eclipse.rse.subsystems.files.core.subsystems.IRemoteFile;
import org.eclipse.rse.subsystems.shells.core.model.RemoteCommandShellOperation;
import org.eclipse.rse.subsystems.shells.core.subsystems.IRemoteCmdSubSystem;
import org.launchpi.launcher.i18n.Messages;

public class ProjectSynchronizer {

	private static final String LIB_DIR = "lib"; //$NON-NLS-1$
	private static final String CLASSES_DIR = "classes"; //$NON-NLS-1$

	public static final String REMOTE_FOLDER_NAME = ".launchpi_projects"; //$NON-NLS-1$

	private final IProject project;

	private final IFileServiceSubSystem fileServiceSubsystem;
	private final IRemoteCmdSubSystem cmdSubSystem;

	private final String baseFolderName;
	private IRemoteFile baseFolder;

	public ProjectSynchronizer(IProject project, String baseFolderName, IFileServiceSubSystem fileServiceSubsystem,
			IRemoteCmdSubSystem cmdSubSystem) {
		this.project = project;
		this.baseFolderName = baseFolderName;
		this.fileServiceSubsystem = fileServiceSubsystem;
		this.cmdSubSystem = cmdSubSystem;
	}

	public void synchronize(IProgressMonitor monitor) throws Exception {
		monitor.subTask(Messages.Progress_Synchronizing_CP);
		IJavaProject javaProject = JavaCore.create(project);

		baseFolder = fileServiceSubsystem.getRemoteFileObject(baseFolderName, monitor);
		if (!baseFolder.exists())
			fileServiceSubsystem.createFolders(baseFolder, monitor);

		ClasspathResolver resolver = new ClasspathResolver(javaProject);
		Collection<File> classpathEntries = resolver.resolve();
		
		synchronizeLibraries(baseFolder, classpathEntries, monitor);
		synchronizeClasses(baseFolder, classpathEntries, monitor);
	}

	private void synchronizeClasses(IRemoteFile baseFolder, Collection<File> classpathEntries, IProgressMonitor monitor)
			throws Exception {
		IRemoteFile classFolder = fileServiceSubsystem.getRemoteFileObject(baseFolder, CLASSES_DIR, monitor);
		if (classFolder.exists())
			fileServiceSubsystem.delete(classFolder, monitor);
		
		File classpathArchive = createClassesArchive(classpathEntries);

		IRemoteFile remoteArchive = fileServiceSubsystem.getRemoteFileObject(baseFolder, classpathArchive.getName(), monitor);
		fileServiceSubsystem.upload(classpathArchive.getCanonicalPath(), remoteArchive, null, new SubProgressMonitor(monitor, 0));
		
		String extractClassesCmd = "tar -xf \"" + classpathArchive.getName() + "\""; //$NON-NLS-1$ //$NON-NLS-2$
		runCommandAndBlock(baseFolder, extractClassesCmd, monitor);
		
		fileServiceSubsystem.delete(remoteArchive, monitor);
		
		classpathArchive.delete();
	}

	private void synchronizeLibraries(IRemoteFile baseFolder, Collection<File> classpathEntries, IProgressMonitor monitor)
			throws Exception, SystemMessageException {
		IRemoteFile libFolder = fileServiceSubsystem.getRemoteFileObject(baseFolder, LIB_DIR, monitor);
		if (!libFolder.exists())
			fileServiceSubsystem.createFolders(libFolder, monitor);

		RemoteLibraryContainer remoteLibraryContainer = new RemoteLibraryContainer(libFolder, monitor);
		LocalLibraryContainer localLibraryContainer = new LocalLibraryContainer(classpathEntries);

		List<IRemoteFile> remoteToDelete = remoteLibraryContainer.calculateDifference(localLibraryContainer);
		List<File> localToUpload = localLibraryContainer.calculateDifference(remoteLibraryContainer);

		remoteLibraryContainer.deleteAll(remoteToDelete);
		remoteLibraryContainer.uploadAll(localToUpload);
	}
	
	private void runCommandAndBlock(IRemoteFile baseFolder, String cmd, IProgressMonitor monitor) throws InterruptedException {
		final Object waitMon = new Object();
		final boolean[] done = new boolean[1];
		RemoteCommandShellOperation untar = new RemoteCommandShellOperation(null, cmdSubSystem, baseFolder) {
			
			@Override
			public void handleOutputChanged(String command, Object output) {
			}
			
			@Override
			public void handleCommandFinished(String cmd) {
				done[0] = true;
				synchronized (waitMon) {
					waitMon.notify();
				}
			}
		};
		untar.run();
		untar.sendCommand(cmd);
		synchronized(waitMon) {
			while (!done[0]) waitMon.wait(5000);
		}
		untar.finish();
	}

	private File createClassesArchive(Collection<File> classpathEntries) throws IOException {
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
				if (f.isDirectory()) {
					pathElements.addLast(CLASSES_DIR);
					for (File child : f.listFiles()) {
						writeClasspathEntry(pathElements, child, os);
					}
					pathElements.removeLast();
				}
			}
			return archiveFile;
		} catch (ArchiveException e) {
			throw new IOException("Failed to create classpath archive", e); //$NON-NLS-1$
		} finally {
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
		} finally {
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

	private abstract class LibraryContainer<T> {

		private Map<String, T> librariesByName;
		private Map<FileVersionIdentifier, T> librariesByIdentifier;

		protected void process() throws Exception {
			Collection<T> libraries = buildFileList();

			librariesByIdentifier = new HashMap<FileVersionIdentifier, T>(libraries.size());
			librariesByName = new HashMap<String, T>(libraries.size());
			for (T lib : libraries) {
				FileVersionIdentifier identifier = buildFileVersionIdentifier(lib);
				librariesByIdentifier.put(identifier, lib);
				librariesByName.put(identifier.fileName, lib);
			}
		}

		protected abstract Collection<T> buildFileList() throws Exception;

		protected abstract FileVersionIdentifier buildFileVersionIdentifier(T file);

		public <R> List<T> calculateDifference(LibraryContainer<R> other) {
			Set<FileVersionIdentifier> keys = difference(this.librariesByIdentifier.keySet(),
					other.librariesByIdentifier.keySet());

			List<T> diff = new ArrayList<T>(keys.size());
			for (FileVersionIdentifier key : keys) {
				diff.add(this.librariesByName.get(key.fileName));
			}
			return diff;
		}

		private <A> Set<A> difference(Set<A> a, Set<A> b) {
			Set<A> result = new HashSet<A>(a);
			result.removeAll(b);
			return result;
		}
	}

	private class RemoteLibraryContainer extends LibraryContainer<IRemoteFile> {

		private final IRemoteFile libFolder;
		private final IProgressMonitor monitor;

		public RemoteLibraryContainer(IRemoteFile libFolder, IProgressMonitor monitor) throws Exception {
			this.libFolder = libFolder;
			this.monitor = monitor;
			process();
		}

		@Override
		protected Collection<IRemoteFile> buildFileList() throws Exception {
			return Arrays.asList(fileServiceSubsystem.list(libFolder, IFileService.FILE_TYPE_FILES, monitor));
		}

		@Override
		protected FileVersionIdentifier buildFileVersionIdentifier(IRemoteFile file) {
			FileVersionIdentifier identifier = new FileVersionIdentifier(file.getName(), file.getLength(),
					file.getLastModified());
			return identifier;
		}

		public void deleteAll(List<IRemoteFile> toDelete) throws SystemMessageException {
			if (toDelete.isEmpty())
				return;
			fileServiceSubsystem.deleteBatch(toDelete.toArray(new IRemoteFile[toDelete.size()]), monitor);
		}

		public void uploadAll(List<File> upload) throws Exception {
			SubProgressMonitor uploadAllMonitor = new SubProgressMonitor(monitor, 0,
					SubProgressMonitor.PREPEND_MAIN_LABEL_TO_SUBTASK);
			uploadAllMonitor.beginTask(Messages.Progress_Uploading_Jar, upload.size());

			for (File file : upload) {
				String localPath = file.getCanonicalPath();
				long lastModified = file.lastModified();
				uploadAllMonitor.subTask(file.getName());
				IRemoteFile remote = fileServiceSubsystem.getRemoteFileObject(libFolder, file.getName(), uploadAllMonitor);
				String remotePath = remote.getAbsolutePath();
				SubProgressMonitor subMonitor = new SubProgressMonitor(uploadAllMonitor, 0,
						SubProgressMonitor.PREPEND_MAIN_LABEL_TO_SUBTASK);
				fileServiceSubsystem.upload(localPath, null, remotePath, null, subMonitor);
				subMonitor.done();
				fileServiceSubsystem.setLastModified(remote, lastModified, uploadAllMonitor);
				uploadAllMonitor.worked(1);
			}
			uploadAllMonitor.done();
		}
	}

	private class LocalLibraryContainer extends LibraryContainer<File> {

		private final Collection<File> classpathEntries;

		public LocalLibraryContainer(Collection<File> classpathEntries) throws Exception {
			this.classpathEntries = classpathEntries;
			process();
		}

		@Override
		protected Collection<File> buildFileList() {
			Collection<File> onlyLibraries = new ArrayList<File>();
			for (File file : classpathEntries) {
				if (file.isFile())
					onlyLibraries.add(file);
			}
			return onlyLibraries;
		}

		@Override
		protected FileVersionIdentifier buildFileVersionIdentifier(File file) {
			FileVersionIdentifier identifier = new FileVersionIdentifier(file.getName(), file.length(), file.lastModified());
			return identifier;
		}
	}

	private static class FileVersionIdentifier {

		public final String fileName;
		public final long length;
		public final long lastModified;

		public FileVersionIdentifier(String fileName, long length, long lastModified) {
			this.fileName = fileName;
			this.length = length;
			// some (remote) file systems do not support the same level of time resolution
			this.lastModified = ((long) lastModified / 1000) * 1000; 
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((fileName == null) ? 0 : fileName.hashCode());
			result = prime * result + (int) (lastModified ^ (lastModified >>> 32));
			result = prime * result + (int) (length ^ (length >>> 32));
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof FileVersionIdentifier))
				return false;
			FileVersionIdentifier other = (FileVersionIdentifier) obj;
			if (fileName == null) {
				if (other.fileName != null)
					return false;
			} else if (!fileName.equals(other.fileName))
				return false;
			if (lastModified != other.lastModified)
				return false;
			if (length != other.length)
				return false;
			return true;
		}

	}
}