package org.launchpi.launcher;


import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.rse.core.RSECorePlugin;
import org.eclipse.rse.core.model.IHost;
import org.eclipse.rse.core.model.ISystemRegistry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

public class RPITab extends AbstractLaunchConfigurationTab {
	
	private Composite control;
	private ComboViewer rpiCombo;
	private Text debugPortTxt;
	private Text javaCmdTxt;
	
	@Override
	public Control getControl() {
		return control;
	}

	@Override
	public void createControl(Composite parent) {
		control = new Composite(parent, SWT.FILL);
		control.setLayout(new GridLayout(2, false));
		
		Label rpiLabel = new Label(control, SWT.LEFT);
		rpiLabel.setText("Raspberry PI Host:");
		rpiCombo = new ComboViewer(control, SWT.VERTICAL | SWT.DROP_DOWN | SWT.BORDER | SWT.READ_ONLY);
		rpiCombo.setContentProvider(ArrayContentProvider.getInstance());
		rpiCombo.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				IHost host = (IHost) element;
				return host.getName() + " - " + host.getDescription();
			}
		});
		rpiCombo.addSelectionChangedListener(new ISelectionChangedListener() {
			
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				setDirty(true);
				updateLaunchConfigurationDialog();
			}
		});
		
		Label cmdLabel = new Label(control, SWT.LEFT);
		cmdLabel.setText("Java command:");
		javaCmdTxt = new Text(control, SWT.BORDER);
		javaCmdTxt.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		Label portLabel = new Label(control, SWT.LEFT);
		portLabel.setText("Debug port:");
		debugPortTxt = new Text(control, SWT.BORDER);
		debugPortTxt.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		TextModifyListener modifyListener = new TextModifyListener();
		javaCmdTxt.addModifyListener(modifyListener);
		debugPortTxt.addModifyListener(modifyListener);
	}

	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
		configuration.setAttribute(RPIConfigurationAttributes.DEBUG_PORT, RPIConfigurationAttributes.DEFAULT_DEBUG_POST);
		configuration.setAttribute(RPIConfigurationAttributes.JAVA_CMD, RPIConfigurationAttributes.DEFAULT_JAVA_CMD);
		configuration.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_CONNECTOR, IJavaLaunchConfigurationConstants.ID_SOCKET_ATTACH_VM_CONNECTOR);
	}

	@Override
	public void initializeFrom(ILaunchConfiguration configuration) {
		ISystemRegistry registry = RSECorePlugin.getTheSystemRegistry();
		IHost[] hosts = registry.getHostsBySystemType(RSECorePlugin.getTheCoreRegistry().getSystemTypeById("org.eclipse.rse.systemtype.ssh"));
		rpiCombo.setInput(hosts);
		
		try {
			String cfgSystem = configuration.getAttribute(RPIConfigurationAttributes.SYSTEM, "");
			String cfgSystemProfileName = configuration.getAttribute(RPIConfigurationAttributes.SYSTEM_PROFILE, "");
			int cfgSystemDebugPort = configuration.getAttribute(RPIConfigurationAttributes.DEBUG_PORT, RPIConfigurationAttributes.DEFAULT_DEBUG_POST);
			for (IHost host : hosts) {
				if (host.getName().equals(cfgSystem) && host.getSystemProfileName().equals(cfgSystemProfileName)) {
					rpiCombo.setSelection(new StructuredSelection(host));
					break;
				}
			}
			debugPortTxt.setText(String.valueOf(cfgSystemDebugPort));
			javaCmdTxt.setText(configuration.getAttribute(RPIConfigurationAttributes.JAVA_CMD, RPIConfigurationAttributes.DEFAULT_JAVA_CMD));
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	@Override
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		IStructuredSelection selection = (IStructuredSelection) rpiCombo.getSelection();
		if (!selection.isEmpty()) {
			IHost host = (IHost) selection.getFirstElement();
			configuration.setAttribute(RPIConfigurationAttributes.SYSTEM, host.getName());
			configuration.setAttribute(RPIConfigurationAttributes.SYSTEM_PROFILE, host.getSystemProfileName());
			configuration.setAttribute(RPIConfigurationAttributes.DEBUG_PORT, Integer.valueOf(debugPortTxt.getText()));
			configuration.setAttribute(RPIConfigurationAttributes.JAVA_CMD, javaCmdTxt.getText());
			
			Map<String, String> argMap = new HashMap<String, String>();
	        argMap.put("hostname", host.getName());
	        argMap.put("port", debugPortTxt.getText());
			configuration.setAttribute(IJavaLaunchConfigurationConstants.ATTR_CONNECT_MAP, argMap);
		}
	}

	@Override
	public String getName() {
		return "Raspberry PI";
	}
	
	private class TextModifyListener implements ModifyListener {
		@Override
		public void modifyText(ModifyEvent event) {
			setDirty(true);
			updateLaunchConfigurationDialog();
		}
		
	}

}
