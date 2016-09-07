package com.pdaxrom.cctools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

public class ADialog extends Activity {
	private final static String TAG = "cctools-DialogWindow";
	private final Context context = this;
	private final Handler handler = new Handler();

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        
        String type = intent.getStringExtra("type");
        
        Log.w(TAG, "type = " + type);
        
        lock(intent);
        
        if (type.contentEquals("yesno")) {
        	dialogYesNo(intent);
        } else if (type.contentEquals("editbox")) {
        	dialogEditBox(intent);
        } else if (type.contentEquals("textview")) {
        	dialogTextView(intent);
        } else {
        	if (type.contentEquals("install")) {
        		String apkFile = intent.getStringExtra("package");
        		Intent newIntent = new Intent(Intent.ACTION_VIEW);
        		newIntent.setDataAndType(Uri.fromFile(new File(apkFile)), "application/vnd.android.package-archive");
        		startActivity(newIntent);
        	}
        	
        	unlock(intent);
        	
        	finish();
        }
    }
    
    private void dialogYesNo(final Intent intent) {
    	final String title = intent.getStringExtra("title"); 
    	final String message = intent.getStringExtra("message");
    	Runnable proc = new Runnable() {
    		public void run() {
    	    	new AlertDialog.Builder(context)
    	    	.setTitle(title)
    	    	.setMessage(message)
    	    	.setOnCancelListener(new DialogInterface.OnCancelListener() {					
					public void onCancel(DialogInterface dialog) {
						returnStrings(intent, "cancel");
					}
				})
    	    	.setPositiveButton(getString(R.string.button_yes), new DialogInterface.OnClickListener() {
    				public void onClick(DialogInterface dialog, int which) {
    					returnStrings(intent, "yes");
    				}
    			})
    			.setNeutralButton(getString(R.string.button_cancel), new DialogInterface.OnClickListener() {
    				public void onClick(DialogInterface dialog, int which) {
						returnStrings(intent, "cancel");
    				}
    			})
    	    	.setNegativeButton(getString(R.string.button_no), new DialogInterface.OnClickListener() {
    				public void onClick(DialogInterface dialog, int which) {
						returnStrings(intent, "no");
    				}
    			})
    			.show();
    		}
    	};
    	handler.post(proc);
    }

    private void dialogEditBox(final Intent intent) {
    	final String title = intent.getStringExtra("title"); 
    	final String message = intent.getStringExtra("message");
    	
    	Runnable proc = new Runnable() {
    		public void run() {
    			final EditText input = new EditText(context);
    	    	if (intent.getBooleanExtra("password", false)) {
    	    		input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    	    	} else {
    	    		input.setSingleLine(!intent.getBooleanExtra("multi", false));
    	    	}
    			new AlertDialog.Builder(context)
    		    .setTitle(title)
    		    .setMessage(message)
    		    .setView(input)
    	    	.setOnCancelListener(new DialogInterface.OnCancelListener() {					
					public void onCancel(DialogInterface dialog) {
						returnStrings(intent, "cancel");
					}
				})
    		    .setPositiveButton(getString(R.string.button_continue), new DialogInterface.OnClickListener() {
    		        public void onClick(DialogInterface dialog, int whichButton) {
    		            String value = input.getText().toString(); 
    		            returnStrings(intent, "ok", value);
    		        }
    		    }).setNegativeButton(getString(R.string.button_cancel), new DialogInterface.OnClickListener() {
    		        public void onClick(DialogInterface dialog, int whichButton) {
    		        	returnStrings(intent, "cancel");
    		        }
    		    }).show();
    		}
    	};
    	handler.post(proc);
    }
    
    private void dialogTextView(final Intent intent) {
    	final String title = intent.getStringExtra("title"); 
    	final String message = intent.getStringExtra("message");
    	final String text = intent.getStringExtra("text");
    	Runnable proc = new Runnable() {
    		public void run() {
    			final TextView output = new TextView(context);
    			output.setText(text);
    			new AlertDialog.Builder(context)
    		    .setTitle(title)
    		    .setMessage(message)
    		    .setView(output)
    	    	.setOnCancelListener(new DialogInterface.OnCancelListener() {					
					public void onCancel(DialogInterface dialog) {
						returnStrings(intent, "cancel");
					}
				})
    		    .setPositiveButton(getString(R.string.button_continue), new DialogInterface.OnClickListener() {
    		        public void onClick(DialogInterface dialog, int whichButton) {
    		            returnStrings(intent, "ok");
    		        }
    		    }).show();
    		}
    	};
    	handler.post(proc);
    }
        
    private void returnStrings(Intent intent, String status) {
    	String fileName = intent.getStringExtra("return");
    	
    	writeFile(fileName, status);

    	unlock(intent);
    	
		finish();
    }

    private void returnStrings(Intent intent, String status, String output) {
    	String fileName = intent.getStringExtra("return");
    	
    	writeFile(fileName + ".out", output);

    	writeFile(fileName, status);

    	unlock(intent);
    	
		finish();
    }

    private void writeFile(String name, String text) {
    	if (name == null) {
    		return;
    	}
    	
    	File temp = new File(name);

    	try {
    		if (temp.exists()) {
        		temp.delete();
        	}
			temp.createNewFile();
			FileOutputStream out = new FileOutputStream(temp);
			out.write(text.getBytes());
			out.flush();
			out.close();
		} catch (IOException e) {
			Log.e(TAG, "writeFile IOException " + e);
		}
    	
    }
    
    private void lock(Intent intent) {
    	String name = intent.getStringExtra("return");
    	
    	if (name == null) {
    		return;
    	}
    	
    	File temp = new File(name + ".lock");
    	
    	try {
			temp.createNewFile();
		} catch (IOException e) {
			Log.e(TAG, "lock exception " + e);
		}
    }

    private void unlock(Intent intent) {
    	String name = intent.getStringExtra("return");

    	if (name == null) {
    		return;
    	}
    	
    	File temp = new File(name + ".lock");
    	if (temp.exists()) {
    		temp.delete();
    	}
    }
}
