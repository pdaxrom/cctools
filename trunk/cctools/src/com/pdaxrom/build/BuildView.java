package com.pdaxrom.build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.pdaxrom.utils.LogItem;
import com.pdaxrom.utils.Utils;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.TextView;

public class BuildView extends TextView {
	private static final String TAG = "cctools-build";

	private boolean isRunning = false;
	
	private String cctoolsDir;
	private String cctoolsResDir;
	private String cmdline;
	private String workDir;
	private String tmpDir;

	private int mProcId;
	private FileDescriptor mFd;
	private int mExitCode;

	private Thread cmdThread;
	
    private Handler handler = new Handler();

	private ArrayList<LogItem> errorsList = null;

	public BuildView(Context context) {
		super(context);
		init();
	}

	public BuildView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public BuildView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs);
		init();
	}
		
	private void init() {
		Log.i(TAG, "cctools-build created!");
		isRunning = false;
	}
	
	public boolean isRunning() {
		return isRunning;
	}

	public ArrayList<LogItem> getLog() {
		return errorsList;
	}
	
    private void output(final String str) {
    	Runnable proc = new Runnable() {
    		public void run() {
    			append(str);
    		}
    	};
    	handler.post(proc);
    }

    public void start(String cmdline, String workDir, String cctoolsDir, String cctoolsResDir, String tmpDir) {
    	if (isRunning) {
    		Log.w(TAG, "BuildView start - already started!");
    		return;
    	}
        isRunning = true;
    	this.cmdline = cmdline;
    	this.workDir = workDir;
    	this.cctoolsDir = cctoolsDir;
    	this.cctoolsResDir = cctoolsResDir;
    	this.tmpDir = tmpDir;
    	
    	errorsList = new ArrayList<LogItem>();
    	
        cmdThread = new MyThread();
        cmdThread.start();
    }
    
    public void stop() {
		Log.i(TAG, "Finish cmdline thread before activity exit");
		if (cmdThread != null && cmdThread.isAlive()) {
			cmdThread.interrupt();
			Utils.hangupProcessGroup(mProcId);
			isRunning = false;
		}
    }
    
    public class MyThread extends Thread {
    	public void run() {
    		try {
    			Log.i(TAG, "execute " + cmdline + "\n");
    			
    			String libSuffix = "/lib";
    			
    			if (Build.CPU_ABI.startsWith("arm64") || Build.CPU_ABI.startsWith("mips64")
    					|| Build.CPU_ABI.startsWith("x86_64")) {
    				libSuffix = "/lib64";
    			}

    			String[] envp = {
    					"PWD=" + workDir,
    					"TMPDIR=" + tmpDir,
    					"PATH=" + cctoolsDir + "/bin:" + cctoolsDir + "/sbin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin",
    					"ANDROID_ASSETS=/system/app",
    					"ANDROID_BOOTLOGO=1",				
    					"ANDROID_DATA=" + cctoolsDir + "/var/dalvik",
    					"ANDROID_ROOT=/system",
    					"CCTOOLSDIR=" + cctoolsDir,
    					"CCTOOLSRES=" + cctoolsResDir,
    					"LD_LIBRARY_PATH=" + cctoolsDir + "/lib:/system" + libSuffix + ":/vendor" + libSuffix,
    					"HOME=" + cctoolsDir + "/home",
    					"PS1=''"
    					};
    			String shell = getShell();
    	        shell = shell.replaceAll("\\s+", " ");
    			String[] argv = shell.split("\\s+");
    			int[] pId = new int[1];
    			mFd = Utils.createSubProcess(workDir, argv[0], argv, envp, pId);
    			mProcId = pId[0];
    			if (mProcId > 0) {
        			try {
        				Utils.setPtyUTF8Mode(mFd, true);
        				Utils.setPtyWindowSize(mFd, 64, 128, 0, 0);
        				FileInputStream fis = new FileInputStream(mFd);
        				BufferedReader procout = new BufferedReader(new InputStreamReader(fis));
        				FileOutputStream procin = new FileOutputStream(mFd);
        				Thread execThread = new Thread() {
        					public void run() {
        						Log.i(TAG, "Waiting for hangup session");
        						mExitCode = Utils.waitFor(mProcId);
        						Log.i(TAG, "Subprocess exited: " + mExitCode);
        					}
        				};
        				execThread.start();
        				while (fis.available() == 0) ;
        				procin.write(new String("export PS1=''\n").getBytes());
        				Pattern pat1 = Pattern.compile("^(\\S+):(\\d+):(\\d+): (\\S+|\\S+ \\S+): (.*)$");
        				Pattern pat2 = Pattern.compile("^(\\S+):(\\d+): (\\S+|\\S+ \\S+): (.*)$");
        				Pattern patClearNewLine = Pattern.compile("(\\x08)\\1+");
        				errorsList.clear();
        				cmdline = "exec " + cmdline + "\n";
        				procin.write(cmdline.getBytes());
        				int skipStrings = 2; //skip echos from two command strings
        				do {
        					String errstr = null;
        					try {
        						errstr = procout.readLine();
        						
        						//Log.i(TAG, "read: " + errstr);
        						
        						// remove escape sequence
        						errstr = errstr.replaceAll("\u001b\\[([0-9]|;)*m", "");
        						// remove clearing new line
        						Matcher m = patClearNewLine.matcher(errstr);
        						if (m.find()) {
        							int length = m.end() - m.start();
        							if (m.start() > length) {
        								errstr = errstr.substring(0, m.start() - length) + errstr.substring(m.end());
        							}
        						}
        					} catch (IOException e) {
        						break;
        					}
    						if (errstr == null) {
    							break;
    						}
    						Matcher m = pat1.matcher(errstr);
    						if (m.find()) {
    							Log.e(TAG, "out " + m.group(1) + "|" + m.group(2) + "|" + m.group(3) + "|" + m.group(4) + "|" + m.group(5));
    							int idx = errorsList.size() - 1;
    							if (idx >= 0 &&
    								errorsList.get(idx).getFile().contentEquals(m.group(1)) &&
    								errorsList.get(idx).getType().contentEquals(m.group(4)) &&
    								errorsList.get(idx).getLine() == Integer.parseInt(m.group(2)) &&
    								errorsList.get(idx).getPos() == Integer.parseInt(m.group(3))) {
    								errorsList.get(idx).setMessage(errorsList.get(idx).getMessage() + " " + m.group(5));
    							} else {
    								LogItem item = new LogItem(m.group(4), m.group(1), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), m.group(5));
    								errorsList.add(item);
    							}
    						} else {
    							m = pat2.matcher(errstr);
    							if (m.find()) {
        							Log.e(TAG, "out " + m.group(1) + "|" + m.group(2) + "|" + m.group(3) + "|" + m.group(4));    								
        							int idx = errorsList.size() - 1;
        							if (idx >= 0 &&
        								errorsList.get(idx).getFile().contentEquals(m.group(1)) &&
        								errorsList.get(idx).getType().contentEquals(m.group(3)) &&
        								errorsList.get(idx).getLine() == Integer.parseInt(m.group(2))) {
        								errorsList.get(idx).setMessage(errorsList.get(idx).getMessage() + " " + m.group(4));
        							} else {
        								LogItem item = new LogItem(m.group(3), m.group(1), Integer.parseInt(m.group(2)), -1, m.group(4));
        								errorsList.add(item);
        							}
    							}
    						}
    						if (skipStrings > 0) {
    							skipStrings--;
    						} else {
    							output(errstr + "\n");
    						}
    						Log.i(TAG, errstr);
        				} while(execThread.isAlive() || procout.ready());
        				
        				//FIXME: set tab title
						if (mExitCode != 0) {
							//output(getString(R.string.build_error) + " " + mExitCode + "\n");
					        //showTitle(getString(R.string.buildwindow_name_error) + " - " + fileName);
						} else {
					        //showTitle(getString(R.string.buildwindow_name_done) + " - " + fileName);
						}

						Log.e(TAG, "process exit code " + mExitCode);
						procin.close();
        				procout.close();
        			} catch (IOException ie) {
        				Log.e(TAG, "exception " + ie);
        			}    				
    			}    			
    		} catch (Exception ie) {
    			Log.e(TAG, "exec() " + ie);
    		}
    		isRunning = false;
    		//output("\n" + getString(R.string.build_done) +"\n\n");
    	}
    }
     
	private String getShell() {
		String[] shellList = {
				cctoolsDir + "/bin/bash",
				cctoolsDir + "/bin/ash",
		};
		
		for (String shell: shellList) {
			if ((new File(shell)).exists()) {
				return shell;
			}
		}
		return "/system/bin/sh";
	}

}
