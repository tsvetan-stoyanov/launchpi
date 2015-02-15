package org.launchpi.launcher;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

public class ClasspathResolver {
	
	IJavaProject project;

	public ClasspathResolver(IJavaProject project) {
		this.project = project;
	}
	
	public Collection<File> resolve() throws JavaModelException {
		Collection<IClasspathEntry> resolvedClasspath = getResolvedClasspath();
		return getClasspathFiles(resolvedClasspath);
	}
	
	private Collection<File> getClasspathFiles(Collection<IClasspathEntry> classpathEntries) throws JavaModelException {
		List<File> files = new ArrayList<File>();
		for (IClasspathEntry classpathEntry : classpathEntries) {
			files.addAll(getFiles(classpathEntry));
		}
		return files;
	}
	
	private Collection<File> getFiles(IClasspathEntry classpathEntry) throws JavaModelException {
		int entryKind = classpathEntry.getEntryKind();
		ArrayList<File> files = new ArrayList<File>(1);
		
		if (entryKind == IClasspathEntry.CPE_SOURCE) {
			IPath outputLocation = classpathEntry.getOutputLocation();
			if (outputLocation == null) {
				outputLocation = project.getOutputLocation();
			}
			files.add(getResource(outputLocation).getLocation().toFile());
		} else if (entryKind == IClasspathEntry.CPE_LIBRARY) {
			IPath outputLocation = classpathEntry.getPath();
			IResource resource = getResource(outputLocation);
			files.add(resource != null ? resource.getLocation().toFile() : outputLocation.toFile());
		} else if (entryKind == IClasspathEntry.CPE_PROJECT) {
			IPath outputLocation = classpathEntry.getPath();
			IProject refProject = (IProject) getResource(outputLocation);
			IJavaProject refJavaProject = JavaCore.create(refProject);
			ClasspathResolver resolver = new ClasspathResolver(refJavaProject);
			files.addAll(resolver.resolve());
		}
		return files;
	}
	
	private Collection<IClasspathEntry> getResolvedClasspath() throws JavaModelException {
		List<IClasspathEntry> resolvedClasspath = new ArrayList<IClasspathEntry>(Arrays.asList(project.getResolvedClasspath(true)));
		removeSystemContainers(resolvedClasspath);
		return resolvedClasspath;
	}
	
	private void removeSystemContainers(List<IClasspathEntry> resolvedClasspath) throws JavaModelException {
		IClasspathEntry[] rawClasspath = project.getRawClasspath();
		
		for (IClasspathEntry classpathEntry : rawClasspath) {
			if (classpathEntry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
				IClasspathContainer container = JavaCore.getClasspathContainer(classpathEntry.getPath(), project);
				if (container.getKind() != IClasspathContainer.K_APPLICATION) {
					for (IClasspathEntry containerEntry : container.getClasspathEntries()) {
						resolvedClasspath.remove(containerEntry);
					}
				}
			}
		}
	}
	
	private IResource getResource(IPath path) {
		return ResourcesPlugin.getWorkspace().getRoot().findMember(path, false);
	}
}
