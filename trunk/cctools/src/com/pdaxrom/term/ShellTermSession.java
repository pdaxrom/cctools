package com.pdaxrom.term;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.pdaxrom.utils.Utils;

import jackpal.androidterm.emulatorview.TermSession;
import jackpal.androidterm.emulatorview.UpdateCallback;

public class ShellTermSession extends TermSession {
	private final static String TAG = "ShellTermSession";
	
	public final static int MSG_FINISH = 1;
	public final static int MSG_TITLE = 2;
	
	private int mProcId;
	private FileDescriptor mFd;
    private Thread watcherThread;

	private Handler mMsgHandler;
	
	private UpdateCallback utf8ModeChangedListener = new UpdateCallback() {
		public void onUpdate() {
			Utils.setPtyUTF8Mode(mFd, getUTF8Mode());
		}
	};
	
	private UpdateCallback titleChangedCallback = new UpdateCallback() {
		public void onUpdate() {
			mMsgHandler.sendEmptyMessage(MSG_TITLE);
		}
	};
	
	public ShellTermSession(String[] argv, String envp[], String cwd, Handler handler) {
		super();
		
		mMsgHandler = handler;
		
		setDefaultUTF8Mode(true);
		
		createSubProcess(argv, envp, cwd);
		
        watcherThread = new Thread() {
            @Override
            public void run() {
               Log.i(TAG, "waiting for: " + mProcId);
               int result = Utils.waitFor(mProcId);
               Log.i(TAG, "Subprocess exited: " + result);
               //mMsgHandler.sendMessage(mMsgHandler.obtainMessage(PROCESS_EXITED, result));
               mMsgHandler.sendEmptyMessage(MSG_FINISH);
            }
       };
       watcherThread.setName("Process watcher");
	}
	
	@Override
	public void finish() {
		Log.i(TAG, "finish()");
	    Utils.hangupProcessGroup(mProcId);
	    Utils.close(mFd);
	    super.finish();
	}

	public void hangup() {
	    Utils.hangupProcessGroup(mProcId);
	}
	
	public int getPid()
	{
		return mProcId;
	}
	
	private void createSubProcess(String[] argv, String[] envp, String cwd) {
		int[] pId = new int[1];
		String cmd = argv[0];
		/* detect shell login */
		if (cmd.startsWith("-")) {
			cmd = cmd.substring(1);
			int lastSlash = cmd.lastIndexOf("/");
			if (lastSlash > 0 && lastSlash < cmd.length() - 1) {
				argv[0] = "-" + cmd.substring(lastSlash + 1);
			}
		}
		mFd = Utils.createSubProcess(cwd, cmd, argv, envp, pId);
		mProcId = pId[0];
		if (mProcId > 0) {
			setTermIn(new FileInputStream(mFd));
			setTermOut(new FileOutputStream(mFd));
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD &&
				(cmd.endsWith("/sh") || cmd.endsWith("/ash") || cmd.endsWith("/bash"))) {
				try {
					while (getTermIn().available() == 0) ;
					getTermOut().write(new String(". ~/.profile\n").getBytes());
				} catch (IOException e) {
					Log.e(TAG, "load profile");
				}
			}
		}
	}
	
	@Override
	public void initializeEmulator(int columns, int rows) {
		super.initializeEmulator(columns, rows);
		
		Utils.setPtyUTF8Mode(mFd, getUTF8Mode());
		setUTF8ModeUpdateCallback(utf8ModeChangedListener);

		setTitleChangedListener(titleChangedCallback);
		
        watcherThread.start();
	}
	
	@Override
	public void updateSize(int columns, int rows) {
		Utils.setPtyWindowSize(mFd, rows, columns, 0, 0);
		super.updateSize(columns, rows);
	}
}
