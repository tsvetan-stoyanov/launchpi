package org.launchpi.launcher;

public interface RPIConfigurationAttributes {
	
	public static final String SYSTEM = "rpiSystem";
	public static final String DEBUG_PORT = "rpiSystemDebugPort";
	public static final String RUN_AS_ROOT = "rpiRunAsRoot";
	public static final String SYSTEM_PROFILE = "rpiSystemProfile";
	
	public static final int DEFAULT_DEBUG_POST = 4000;
	public static final boolean DEFAULT_RUN_AS_ROOT = true;
}
