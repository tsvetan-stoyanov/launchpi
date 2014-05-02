package org.launchpi.launcher;


import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.rse.core.IRSESystemType;
import org.eclipse.rse.core.RSECorePlugin;
import org.eclipse.rse.core.model.IHost;
import org.eclipse.rse.core.model.ISystemRegistry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.launchpi.launcher.i18n.Messages;

public class RPITab extends AbstractLaunchConfigurationTab {
	
	private Composite control;
	private ComboViewer rpiCombo;
	private Text debugPortTxt;
	private Text displayTxt;
	private Button runAsRootBtn;
	private Button addHostBtn;
	
	@Override
	public Control getControl() {
		return control;
	}

	@Override
	public void createControl(Composite parent) {
		control = new Composite(parent, SWT.FILL);
		control.setLayout(new GridLayout(3, false));
		
		Label rpiLabel = new Label(control, SWT.LEFT);
		rpiLabel.setText(Messages.Host);
		rpiCombo = new ComboViewer(control, SWT.VERTICAL | SWT.DROP_DOWN | SWT.BORDER | SWT.READ_ONLY);
		rpiCombo.getCombo().setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		rpiCombo.setContentProvider(ArrayContentProvider.getInstance());
		rpiCombo.setLabelProvider(new RPISystemLabelProvider());
		rpiCombo.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				setDirty(true);
				updateLaunchConfigurationDialog();
			}
		});
		
		addHostBtn = new Button(control, SWT.NONE);
		addHostBtn.setText(Messages.New_Host);
		addHostBtn.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				createNewHost();
			}
		});
		
		Label portLabel = new Label(control, SWT.LEFT);
		portLabel.setText(Messages.Debug_Port);
		debugPortTxt = new Text(control, SWT.BORDER);
		debugPortTxt.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		
		Label displayLabel = new Label(control, SWT.LEFT);
		displayLabel.setText(Messages.Display);
		displayTxt = new Text(control, SWT.BORDER);
		displayTxt.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		
		Label cmdLabel = new Label(control, SWT.LEFT);
		cmdLabel.setText(Messages.Run_As_Root);
		runAsRootBtn = new Button(control, SWT.CHECK);
		
		runAsRootBtn.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				configurationChanged();
			}
		});
		debugPortTxt.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				configurationChanged();
			}
		});
		displayTxt.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				configurationChanged();
			}
		});
	}

	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
		configuration.setAttribute(RPIConfigurationAttributes.DEBUG_PORT, RPIConfigurationAttributes.DEFAULT_DEBUG_POST);
		configuration.setAttribute(RPIConfigurationAttributes.RUN_AS_ROOT, RPIConfigurationAttributes.DEFAULT_RUN_AS_ROOT);
		configuration.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_CONNECTOR, IJavaLaunchConfigurationConstants.ID_SOCKET_ATTACH_VM_CONNECTOR);
		configuration.setAttribute(RPIConfigurationAttributes.DISPLAY, RPIConfigurationAttributes.DEFAULT_DISPLAY);
	}

	@Override
	public void initializeFrom(ILaunchConfiguration configuration) {
		IHost[] hosts = getHosts();
		
		try {
			String cfgSystem = configuration.getAttribute(RPIConfigurationAttributes.SYSTEM, ""); //$NON-NLS-1$
			String cfgSystemProfileName = configuration.getAttribute(RPIConfigurationAttributes.SYSTEM_PROFILE, ""); //$NON-NLS-1$
			int cfgSystemDebugPort = configuration.getAttribute(RPIConfigurationAttributes.DEBUG_PORT, RPIConfigurationAttributes.DEFAULT_DEBUG_POST);
			boolean runAsRoot = configuration.getAttribute(RPIConfigurationAttributes.RUN_AS_ROOT, RPIConfigurationAttributes.DEFAULT_RUN_AS_ROOT);
			IHost cfgHost = getHost(hosts, cfgSystemProfileName, cfgSystem);
			String display = configuration.getAttribute(RPIConfigurationAttributes.DISPLAY, RPIConfigurationAttributes.DEFAULT_DISPLAY);
			
			updateRPICombo(hosts, cfgHost);
			debugPortTxt.setText(String.valueOf(cfgSystemDebugPort));
			displayTxt.setText(display);
			runAsRootBtn.setSelection(runAsRoot);
		} catch (CoreException e) {
			LaunchPlugin.reportError(Messages.Start_Failed, e);
		}
		
	}

	@Override
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		String hostName = null;
		IStructuredSelection selection = (IStructuredSelection) rpiCombo.getSelection();
		if (!selection.isEmpty()) {
			IHost host = (IHost) selection.getFirstElement();
			hostName = host.getName();
			configuration.setAttribute(RPIConfigurationAttributes.SYSTEM, hostName);
			configuration.setAttribute(RPIConfigurationAttributes.SYSTEM_PROFILE, host.getSystemProfileName());
		}
		
		try {
			configuration.setAttribute(RPIConfigurationAttributes.DEBUG_PORT, Integer.valueOf(debugPortTxt.getText()));
		} catch (NumberFormatException ex) {
			
		}

		configuration.setAttribute(RPIConfigurationAttributes.RUN_AS_ROOT, runAsRootBtn.getSelection());
		configuration.setAttribute(RPIConfigurationAttributes.DISPLAY, displayTxt.getText().trim());
		
		Map<String, String> argMap = new HashMap<String, String>();
        argMap.put("hostname", hostName); //$NON-NLS-1$
        argMap.put("port", debugPortTxt.getText()); //$NON-NLS-1$
		configuration.setAttribute(IJavaLaunchConfigurationConstants.ATTR_CONNECT_MAP, argMap);
	}

	@Override
	public String getName() {
		return Messages.Raspberry_PI;
	}
	
	@Override
	public Image getImage() {
		return LaunchPlugin.getDefault().getRPITabImage();
	}
	
	@Override
	public boolean isValid(ILaunchConfiguration configuration) {
		setMessage(null);
		setErrorMessage(null);
		
		if (rpiCombo.getSelection().isEmpty()) {
			setErrorMessage(Messages.Choose_Host);
			return false;
		}
		
		try {
			Integer.valueOf(debugPortTxt.getText());
		} catch (NumberFormatException ex) {
			setErrorMessage(Messages.Invalid_Debug_Port);
			return false;
		}
		
		return true;
	}
	
	private void configurationChanged() {
		setDirty(true);
		updateLaunchConfigurationDialog();
	}
	
	private IHost[] getHosts() {
		ISystemRegistry registry = RSECorePlugin.getTheSystemRegistry();
		return registry.getHostsBySystemType(RSECorePlugin.getTheCoreRegistry().getSystemTypeById(IRSESystemType.SYSTEMTYPE_SSH_ONLY_ID));
	}
	
	private void updateRPICombo(IHost[] hosts, IHost selectedHost) {
		rpiCombo.setInput(hosts);
		if (selectedHost != null) {
			rpiCombo.setSelection(new StructuredSelection(selectedHost));
		}
	}
	
	private IHost getHost(IHost[] hosts, String systemProfileName, String hostName) {
		for (IHost host : hosts) {
			if (host.getName().equals(hostName)) {
				if (systemProfileName == null || host.getSystemProfileName().equals(systemProfileName)) {
					return host;
				}
			}
		}
		return null;
	}
	
	private void createNewHost() {
		final IHost[] hosts = getHosts();
		IInputValidator validator = new HostNameInputValidator(hosts);
		InputDialog inputDialog = new InputDialog(getShell(), Messages.New_RPI_Host, Messages.Enter_Host_Name, "", validator); //$NON-NLS-3$ //$NON-NLS-1$
		int result = inputDialog.open();
		if (result == Dialog.OK) {
			String hostName = inputDialog.getValue();
			inputDialog.close();
			try {
				IHost host = RSECorePlugin.getTheSystemRegistry().createHost(
						RSECorePlugin.getTheCoreRegistry().getSystemTypeById(IRSESystemType.SYSTEMTYPE_SSH_ONLY_ID), hostName, hostName, hostName);
				updateRPICombo(getHosts(), host);
			} catch (Exception ex) {
				LaunchPlugin.reportError(Messages.Create_Config_Failed, ex);
			}
		}

	}
	
	private static class RPISystemLabelProvider extends LabelProvider {
		@Override
		public String getText(Object element) {
			IHost host = (IHost) element;
			String hostName = host.getName();
			String description = host.getDescription();
			if (description != null && !description.isEmpty() && !description.equals(hostName)) {
				StringBuilder buf = new StringBuilder(description);
				buf.append(" - ").append(hostName); //$NON-NLS-1$
				return buf.toString();
			}
			return hostName;
		}		
	}
	
	private class HostNameInputValidator implements IInputValidator {
		private IHost[] hosts;
		
		public HostNameInputValidator(IHost[] hosts) {
			this.hosts = hosts;
		}
		
		@Override
		public String isValid(String newText) {
			if (getHost(hosts, null, newText) != null) {
				return MessageFormat.format(Messages.Host_Exists, newText); //$NON-NLS-2$
			}
			return null;
		}		
	}

}
