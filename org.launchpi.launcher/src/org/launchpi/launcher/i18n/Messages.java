package org.launchpi.launcher.i18n;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = "org.launchpi.launcher.i18n.messages"; //$NON-NLS-1$
	public static String Start_Failed;
	public static String Shell_Service_Not_Found;
	public static String Choose_Host;
	public static String Create_Config_Failed;
	public static String Debug_Port;
	public static String Display;
	public static String Enter_Host_Name;
	public static String File_Service_Not_Found;
	public static String Host;
	public static String Host_Exists;
	public static String Host_Not_Found;
	public static String Invalid_Debug_Port;
	public static String New_Host;
	public static String New_RPI_Host;
	public static String Progress_Init_Connection;
	public static String Progress_Launching_Java;
	public static String Progress_Synchronizing_CP;
	public static String Progress_Waiting_Debug_Connection;
	public static String Progress_Uploading_Jar;
	public static String Raspberry_PI;
	public static String Run_As_Root;
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}
