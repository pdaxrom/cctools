package com.pdaxrom.term;

import java.io.File;
import java.util.List;

import com.actionbarsherlock.app.ActionBar;
import com.pdaxrom.utils.CommandLineSupport;

import android.R;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;
import jackpal.androidterm.emulatorview.EmulatorView;
import jackpal.androidterm.emulatorview.TermSession;

public class TermView extends EmulatorView {
	private final static String TAG = "cctools-termview";
	
	private ShellTermSession session;
	private String cmdLine;
	private String workDir;
	private String cctoolsDir;

	private Context context;

	private boolean isRunning = false;

	private TermViewInterface termViewInterface = null;
	private ActionBar.Tab tab;
	
    private Handler mMsgHandler = new Handler() {
	    @Override
	    public void handleMessage(Message msg) {
	    	switch(msg.what) {
	    	case ShellTermSession.MSG_FINISH:
	    		if (isRunning) {
		    		Log.i(TAG, "Message - Process exited!!!");
		    		//showTitle(getString(R.string.console_name) + " - " + getString(R.string.console_finished));
		    		if (termViewInterface != null) {
		    			termViewInterface.titleHasChanged(tab, "- " + session.getTitle() + " -");
		    		}
		    		isRunning = false;
	    		}
	    		break;
	    	case ShellTermSession.MSG_TITLE:
	    		Log.i(TAG, "Title changed to " + session.getTitle());
	    		if (termViewInterface != null) {
	    			termViewInterface.titleHasChanged(tab, session.getTitle());
	    		}
	    		break;
	    	}
	    }
	};

	public TermView(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;
	}

	public TermView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		this.context = context;
	}

	public TermView(Context context, TermSession session, DisplayMetrics metrics) {
		super(context, session, metrics);
		this.context = context;
	}

	public boolean onSingleTapUp(MotionEvent e) {
		InputMethodManager imm = (InputMethodManager) getContext()
									.getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.showSoftInput(this, InputMethodManager.SHOW_FORCED);

		return super.onSingleTapUp(e);
	}

	public void setTermViewInterface(TermViewInterface termViewInterface, ActionBar.Tab tab) {
		this.termViewInterface = termViewInterface;
		this.tab = tab;
	}
	
	public void start(String cmdLine, String workDir, String cctoolsDir) {
		this.cmdLine = cmdLine;
		this.workDir = workDir;
		this.cctoolsDir = cctoolsDir;
		session = createShellTermSession();
		attachSession(session);

	}
	
	public void stop() {
		this.termViewInterface = null;
		if (session != null) {
			session.finish();
		}
	}
	
	public void hangup() {
		if (isRunning) {
			session.hangup();
		}
	}
	
	public boolean isAlive() {
		return isRunning;
	}
	
	@SuppressLint("NewApi")
	private ShellTermSession createShellTermSession() {
		//cmdLine = cmdLine.replaceAll("\\s+", " ");
		Log.i(TAG, "Shell sesion for " + cmdLine + "\n");
		
		String libSuffix = "/lib";
		
		if (Build.CPU_ABI.startsWith("arm64") || Build.CPU_ABI.startsWith("mips64")
				|| Build.CPU_ABI.startsWith("x86_64")) {
			libSuffix = "/lib64";
		}
		
		String[] envp = {
				"TMPDIR=" + Environment.getExternalStorageDirectory().getPath(),
				"PATH=" + cctoolsDir + "/bin:" + cctoolsDir + "/sbin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin",
				"ANDROID_ASSETS=/system/app",
				"ANDROID_BOOTLOGO=1",				
				"ANDROID_DATA=" + cctoolsDir + "/var/dalvik",
				"ANDROID_ROOT=/system",
				"CCTOOLSDIR=" + cctoolsDir,
				"CCTOOLSRES=" + context.getPackageResourcePath(),
				"LD_LIBRARY_PATH=" + cctoolsDir + "/lib:/system" + libSuffix + ":/vendor" + libSuffix,
				"HOME=" + cctoolsDir + "/home",
				"SHELL=" + getShell(cctoolsDir),
				"TERM=xterm",
				"PS1=$ ",
				};
		List<String> argv = CommandLineSupport.split(cmdLine);
		
//		String[] argv = cmdLine.split("\\s+");

		Log.i(TAG, "argv " + argv);
		Log.i(TAG, "envp " + envp);
		
		isRunning = true;
		
		return new ShellTermSession(argv.toArray(new String[argv.size()]), envp, workDir, mMsgHandler);
	}
	
	private String getShell(String toolchainDir) {
		String[] shellList = {
				toolchainDir + "/bin/bash",
				toolchainDir + "/bin/ash",
		};
		
		for (String shell: shellList) {
			if ((new File(shell)).exists()) {
				return shell;
			}
		}

		return "/system/bin/sh";
	}

}