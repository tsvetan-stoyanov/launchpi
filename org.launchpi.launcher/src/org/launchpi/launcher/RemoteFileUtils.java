package org.launchpi.launcher;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.rse.services.clientserver.messages.SystemMessageException;
import org.eclipse.rse.subsystems.files.core.servicesubsystem.IFileServiceSubSystem;
import org.eclipse.rse.subsystems.files.core.subsystems.IRemoteFile;

public class RemoteFileUtils {

	public static void copy(IFileServiceSubSystem fss, File srcFolder, IRemoteFile destFolder, IProgressMonitor monitor) throws SystemMessageException, IOException {
		copy(fss, srcFolder, destFolder.getCanonicalPath(), monitor);
	}

	/**
	 * Copies all children of src into dest
	 * @param fss
	 * @param src
	 * @param destFolderPath
	 * @throws SystemMessageException 
	 * @throws IOException 
	 */
	public static void copy(IFileServiceSubSystem fss, File srcFolder, String destFolderPath, IProgressMonitor monitor) throws SystemMessageException, IOException {
		
		Collection<File> hostFiles = FileUtils.listFiles(srcFolder, null, true);
		
		int srcFolderNameSize = srcFolder.getCanonicalPath().length();

		for (File hostFile : hostFiles) {
			String hostFilePath = hostFile.getCanonicalPath();
			String remoteFileNameFull = destFolderPath + hostFilePath.substring(srcFolderNameSize);
			IRemoteFile remoteFile = fss.getRemoteFileObject(remoteFileNameFull, monitor);
			IRemoteFile remoteFolder = remoteFile.getParentRemoteFile();
			if (!remoteFolder.exists()) {
				fss.createFolders(remoteFolder, monitor);
			}
			fss.upload(hostFilePath, remoteFile, null, monitor);
		}
	}

}
