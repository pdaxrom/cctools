package com.pdaxrom.cctools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NativeActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.pdaxrom.utils.FileDialog;
import com.pdaxrom.utils.SelectionMode;
import com.pdaxrom.utils.Utils;
import com.pdaxrom.utils.XMLParser;

public class FlexiDialogActivity extends SherlockActivity {
	private final static String TAG = "FlexiDialog";
	
	private final static int REQUEST_DIALOG_FILE_SELECTOR = 1000;
	private final static int MAX_REQUESTS_FROM_FILE_DIALOG = 20;
	
	protected static final String PKGS_LISTS_DIR = "/installed/";

	private Context context = this;
	private FlexiDialogInterface flexiDialogInterface = null;
	
    List<NamedView> namedViews = null;
    private int fileSelectorId;
    
	private String toolchainDir;
	private String sdHomeDir;
	private String tmpDir;
	private String filesDir;
	private String serviceDir;
    private String homeDir;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        sdHomeDir = Environment.getExternalStorageDirectory().getPath() + "/CCTools";
        tmpDir = sdHomeDir + "/tmp";
        filesDir = sdHomeDir + "/backup";

        if (!(new File(sdHomeDir)).exists()) {
        	(new File(sdHomeDir)).mkdir();
        }
        if (!(new File(tmpDir)).exists()) {
        	(new File(tmpDir)).mkdir();
        }
        if (!(new File(filesDir)).exists()) {
        	(new File(filesDir)).mkdir();
        }
        
        toolchainDir = getCacheDir().getParentFile().getAbsolutePath() + "/root";
        if (!(new File(toolchainDir)).exists()) {
        	(new File(toolchainDir)).mkdir();
        }
        
        String dalvikCache = toolchainDir + "/cctools/var/dalvik/dalvik-cache";
        if (!(new File(dalvikCache)).exists()) {
        	(new File(dalvikCache)).mkdirs();
        }
        
        updateClassPathEnv();
        
        if (!(new File(toolchainDir + PKGS_LISTS_DIR)).exists()) {
        	(new File(toolchainDir + PKGS_LISTS_DIR)).mkdir();
        }

        serviceDir = toolchainDir + "/cctools/services";
        if (!(new File(serviceDir)).exists()) {
        	(new File(serviceDir)).mkdirs();
        }

        homeDir = toolchainDir + "/cctools/home";
        if (!(new File(homeDir)).exists()) {
        	(new File(homeDir)).mkdir();
        }

        createShellProfile();
    }
    
    protected String getToolchainDir() {
    	return toolchainDir;
    }
    
    protected String getTempDir() {
    	return tmpDir;
    }
    
    protected String getSDHomeDir() {
    	return sdHomeDir;
    }
    
    protected String getServiceDir() {
    	return serviceDir;
    }
    
    protected void setFlexiDialogInterface(FlexiDialogInterface main) {
    	flexiDialogInterface = main;
    }
    
    public class NamedView {
    	private View	view;
    	private String	name;
    	private int		id;
    	
    	NamedView(View view, String name) {
    		this.view = view;
    		this.name = name;
    		this.id = -1;
    	}

    	NamedView(View view, String name, int id) {
    		this.view = view;
    		this.name = name;
    		this.id = id;
    	}
    	
    	public View getView() {
    		return view;
    	}
    	
    	public String getName() {
    		return name;
    	}
    	
    	public int getId() {
    		return id;
    	}
    }

    private View getNamedView(List<NamedView> views, String name) {
    	for (NamedView view: views) {
    		if (view.getName().equals(name)) {
    			return view.getView();
    		}
    	}
    	return null;
    }

    private View getNamedView(List<NamedView> views, int id) {
    	for (NamedView view: views) {
    		if (view.getId() == id) {
    			return view.getView();
    		}
    	}
    	return null;
    }

    private String getVariable(List<NamedView> views, String name) {
    	for (NamedView view: views) {
    		if (name.contains("@" + view.getName() + "@")) {
				EditText edit = (EditText) view.getView();
				if (edit != null) {
	    			name = name.replace("@" + view.getName() + "@", edit.getText().toString());
				}
    		}
    	}
    	
    	return name;
    }
    
    private String getBuiltinVariable(String name) {
    	if (flexiDialogInterface != null) {
    		name = flexiDialogInterface.getBuiltinVariable(name);
    	}

    	if (name.contains("$root_dir$")) {
    		name = name.replace("$root_dir$", toolchainDir + "/cctools");
    	}
    	
    	if (name.contains("$toolchain_dir$")) {
    		name = name.replace("$toolchain_dir$", toolchainDir);
    	}
    	
    	if (name.contains("$tmp_dir$")) {
    		name = name.replace("$tmp_dir$", tmpDir);
    	}
    	
    	if (name.contains("$sd_dir$")) {
    		name = name.replace("$sd_dir$", sdHomeDir);
    	}

    	if (name.contains("$home_dir$")) {
    		name = name.replace("$home_dir$", homeDir);
    	}
    	
    	return name;
    }
    
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	super.onActivityResult(requestCode, resultCode, data);
    	
		if (resultCode == RESULT_OK) {
			if (requestCode >= REQUEST_DIALOG_FILE_SELECTOR &&
					requestCode < REQUEST_DIALOG_FILE_SELECTOR +
						MAX_REQUESTS_FROM_FILE_DIALOG) {
				EditText edit = (EditText) getNamedView(namedViews, 
						requestCode - REQUEST_DIALOG_FILE_SELECTOR);
				if (edit != null) {
					edit.setText(data.getStringExtra(FileDialog.RESULT_PATH));
				}
			}
		}

    }
    
    protected String getLocalizedAttribute(Element e, String attr) {
		String language = getResources().getConfiguration().locale.getLanguage();

		String value = getBuiltinVariable(e.getAttribute(attr + "-" + language));
		if (value.length() == 0) {
			value = getBuiltinVariable(e.getAttribute(attr));
		}
		
		return value;
    }
    
    /**
     * Show module actions
     * @param nl NodeList
     */
    
    private void showModuleActions(final NodeList nl) {
    	final ListView listView = new ListView(this);
    	List<String> list = new ArrayList<String>();

    	for (int i = 0; i < nl.getLength(); i++) {
    		Element e = (Element) nl.item(i);
    		String title = getBuiltinVariable(getLocalizedAttribute(e, "title"));
    		list.add(title);
    	}
    	
    	ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
    	        android.R.layout.simple_list_item_1, list);
    	
    	listView.setAdapter(adapter);
    	
		final AlertDialog dialog = new AlertDialog.Builder(this)
		.setTitle(getText(R.string.module_select))
		.setMessage(getText(R.string.module_select_message))
		.setView(listView)
		.setCancelable(true)
		.show();
		
    	listView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				Log.i(TAG, "selected action " + position);
				dialog.dismiss();
				showAction((Element) nl.item(position));
			}
    	});
    }
    
    /**
     * Create Dialog popup window from xml template
     * @param ruleFile xml template file
     * @param lastOpenedDir last opened directory
     */
    protected void dialogFromModule(String ruleFile, final String lastOpenedDir) {
		XMLParser xmlParser = new XMLParser();
		String xml = xmlParser.getXmlFromFile(ruleFile);
		if (xml != null) {
			Document doc = xmlParser.getDomElement(xml);
			if (doc != null) {
				NodeList nl = doc.getElementsByTagName("action");
				if (nl.getLength() > 1) {
					showModuleActions(nl);
					return;
				}
				showAction((Element) nl.item(0));
			}
		}
    }
    
    /**
     * Show action dialog
     * @param nl
     */
    void showAction(Element e) {
		String title = getBuiltinVariable(getLocalizedAttribute(e, "title"));
		NodeList nl = e.getElementsByTagName("view");

		namedViews = new ArrayList<NamedView>();
		
        TableLayout table = new TableLayout(context);
        table.setColumnStretchable(1, true);
        
        fileSelectorId = 0;
		for (int i = 0; i < nl.getLength(); i++) {
			Element ne = (Element) nl.item(i);
			Log.i(TAG, "-- " + ne.getTagName());
			Log.i(TAG, "--- " + ne.getAttribute("type"));
			Log.i(TAG, "--- " + ne.getAttribute("title"));
			Log.i(TAG, "--- " + ne.getAttribute("name"));
			if (ne.getAttribute("type").equals("edit")) {
				TableRow row = new TableRow(context);
				
				TextView view = new TextView(context);
				view.setText(getBuiltinVariable(getLocalizedAttribute(ne, "title")));
				
				EditText edit = new EditText(context);
				edit.setInputType(edit.getInputType() & ~InputType.TYPE_TEXT_FLAG_MULTI_LINE);
				edit.setHint(getBuiltinVariable(getLocalizedAttribute(ne, "hint")));
				//edit.setText(getBuiltinVariable(getLocalizedAttribute(ne, "value")));

				namedViews.add(new NamedView(edit, ne.getAttribute("name")));

				row.addView(view);
				row.addView(edit);
				table.addView(row);
			} else if (ne.getAttribute("type").equals("dirpath") || ne.getAttribute("type").equals("filepath")) {
				TableRow row = new TableRow(context);

				TextView view = new TextView(context);
				view.setText(getBuiltinVariable(getLocalizedAttribute(ne, "title")));
				
				EditText edit = new EditText(context);
				edit.setInputType(edit.getInputType() & ~InputType.TYPE_TEXT_FLAG_MULTI_LINE);
				edit.setHint(getBuiltinVariable(getLocalizedAttribute(ne, "hint")));
				//edit.setText(getBuiltinVariable(getLocalizedAttribute(ne, "value")));
				
				namedViews.add(new NamedView(edit, ne.getAttribute("name"), fileSelectorId));
				
				ImageButton button = new ImageButton(context);
				button.setImageResource(R.drawable.folder);
				
				if (ne.getAttribute("type").equals("dirpath")) {
					button.setOnClickListener(new OnClickListener() {
						private int id = fileSelectorId++;
						
			        	public void onClick(View v) {
			        		Intent intent = new Intent(getBaseContext(), FileDialog.class);
			        		String dir = getBuiltinVariable("$current_dir$");
			        		if (dir == null || !new File(dir).exists()) {
			        			dir = Environment.getExternalStorageDirectory().getPath();
			        		}
			        		intent.putExtra(FileDialog.START_PATH, dir);
			        		intent.putExtra(FileDialog.SELECTION_MODE, SelectionMode.MODE_SELECT_DIR);
			        		startActivityForResult(intent, REQUEST_DIALOG_FILE_SELECTOR + id);    	
			        		
			        	}
			        });
				} else {
					button.setOnClickListener(new OnClickListener() {
						private int id = fileSelectorId++;
						
			        	public void onClick(View v) {
			        		Intent intent = new Intent(getBaseContext(), FileDialog.class);
			        		String dir = getBuiltinVariable("$current_dir$");
			        		if (dir == null || !new File(dir).exists()) {
			        			dir = Environment.getExternalStorageDirectory().getPath();
			        		}
			        		intent.putExtra(FileDialog.START_PATH, dir);
			        		intent.putExtra(FileDialog.SELECTION_MODE, SelectionMode.MODE_OPEN);
			        		startActivityForResult(intent, REQUEST_DIALOG_FILE_SELECTOR + id);    	
			        		
			        	}
			        });							
				}
				
				row.addView(view);
				row.addView(edit);
				row.addView(button);
				table.addView(row);
			}
		}
		
		View view = LayoutInflater.from(this).inflate(R.layout.module_dialog, null);
		LinearLayout layout = (LinearLayout) view.findViewById(R.id.moduleLayout);
		layout.addView(table);
		
		nl = e.getElementsByTagName("command");
		e = (Element) nl.item(0);
		final String execAttr = e.getAttribute("exec");
		final String intentName = e.getAttribute("intent");
		final NodeList nlExtras = e.getElementsByTagName("extra");
		
		new AlertDialog.Builder(context)
		.setTitle(title)
//		.setMessage(title)
		.setView(view)
		.setPositiveButton(getText(R.string.button_continue), new DialogInterface.OnClickListener() {
			private String exec = execAttr;
			@SuppressLint("NewApi")
			public void onClick(DialogInterface dialog, int which) {
				if (exec.length() == 0) {
					Intent intent = null;
					if (intentName.contentEquals("BuildActivity")) {
						intent = new Intent(FlexiDialogActivity.this, BuildActivity.class);
					} else if (intentName.contentEquals("TermActivity")) {
						intent = new Intent(FlexiDialogActivity.this, TermActivity.class);
					} else if (intentName.contentEquals("NativeActivity")) {
						intent = new Intent(FlexiDialogActivity.this, NativeActivity.class);
					} else if (intentName.contentEquals("LauncherConsoleActivity")) {
						intent = new Intent(FlexiDialogActivity.this, LauncherConsoleActivity.class);
					} else if (intentName.contentEquals("LauncherNativeActivity")) {
						intent = new Intent(FlexiDialogActivity.this, LauncherNativeActivity.class);
					}
					
					for (int i=0; i < nlExtras.getLength(); i++) {
						Element ne = (Element) nlExtras.item(i);
						String type = ne.getAttribute("type");
						String name = ne.getAttribute("name");
						String value = getBuiltinVariable(ne.getAttribute("value"));
						value = getVariable(namedViews, value);
						
						Log.i(TAG, "intentName " + intentName);
						Log.i(TAG, "type " + type);
						Log.i(TAG, "name " + name + " = " + value);								
						
						if (type.contentEquals("boolean")) {
							intent.putExtra(name, Boolean.valueOf(value));
						} else if (type.contentEquals("int")) {
							intent.putExtra(name, Integer.valueOf(value));
						} else if (type.contentEquals("long")) {
							intent.putExtra(name, Long.valueOf(value));
						} else if (type.contentEquals("float")) {
							intent.putExtra(name, Float.valueOf(value));
						} else {
							intent.putExtra(name, value);
						}
					}
					
					startActivity(intent);
				} else {
					exec.replaceAll("\\s+", " ");
					String[] argv = exec.split("\\s+");
					for (int i = 0; i < argv.length; i++) {
						if (argv[i].startsWith("@") && argv[i].endsWith("@")) {
							String var = argv[i].substring(1, argv[i].length() - 1);
							EditText edit = (EditText) getNamedView(namedViews, var);
							if (edit != null) {
								argv[i] = edit.getText().toString();
							}
						} else {
							argv[i] = getBuiltinVariable(argv[i]);
						}
						
						Log.i(TAG, ":: " + argv[i]);
					}
					system(argv, false);
				}
				namedViews = null;
			}
		})
		.setCancelable(true)
		.show();
    }
    
    /**
     * Execute a shell command
     * @param argv
     */
    protected void system(String[] argv) {
    	system(argv, true);
    }
    
    /**
     * Execute a shell command
     * @param argv
     * @param waitForFinish
     */
	protected void system(String[] argv, boolean waitForFinish) {
		String cctoolsDir = toolchainDir + "/cctools";
		String bootClassPath = getEnv(cctoolsDir, "BOOTCLASSPATH");
		if (bootClassPath == null) {
			bootClassPath = Utils.getBootClassPath();
		}
		if (bootClassPath == null) {
			bootClassPath = "/system/framework/core.jar:/system/framework/ext.jar:/system/framework/framework.jar:/system/framework/android.policy.jar:/system/framework/services.jar"; 
		}
		String[] envp = {
				"TMPDIR=" + Environment.getExternalStorageDirectory().getPath(),
				"PATH=" + cctoolsDir + "/bin:" + cctoolsDir + "/sbin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin",
				"ANDROID_ASSETS=/system/app",
				"ANDROID_BOOTLOGO=1",				
				"ANDROID_DATA=" + cctoolsDir + "/var/dalvik",
				"ANDROID_PROPERTY_WORKSPACE=" + getEnv(cctoolsDir, "ANDROID_PROPERTY_WORKSPACE"),
				"ANDROID_ROOT=/system",
				"BOOTCLASSPATH=" + bootClassPath,
				"CCTOOLSDIR=" + cctoolsDir,
				"CCTOOLSRES=" + getPackageResourcePath(),
				"LD_LIBRARY_PATH=" + cctoolsDir + "/lib:/system/lib:/vendor/lib",
				"HOME=" + cctoolsDir + "/home",
				"SHELL=" + getShell(),
				"TERM=xterm",
				"PS1=$ ",
				"SDDIR=" + sdHomeDir,
				"EXTERNAL_STORAGE=" + Environment.getExternalStorageDirectory().getPath(),
				};
		try {
			Process p = Runtime.getRuntime().exec(argv, envp);
			if (waitForFinish) {
				BufferedReader in = new BufferedReader(  
						new InputStreamReader(p.getErrorStream()));  
				String line = null;  
				while ((line = in.readLine()) != null) {  
					Log.i(TAG, "stderr: " + line);
				}			
				p.waitFor();
			}
		} catch (Exception e) {
			Log.i(TAG, "Exec exception " + e);
		}		
	}
	
	protected void updateClassPathEnv() {
		String cpEnvDir = toolchainDir + "/cctools/etc";
		if (! (new File(cpEnvDir)).exists()) {
			(new File(cpEnvDir)).mkdirs();
		}
		try {
			String env = "CCTOOLSCP=" + getPackageResourcePath() + "\n";
			FileOutputStream outf = new FileOutputStream(cpEnvDir + "/cp.env");
			outf.write(env.getBytes());
			outf.close();
		} catch (IOException e) {
			Log.e(TAG, "create cp.env " + e);
		}	
	}
	
	protected String getEnv(String baseDir, String variable) {
		String ret = null;
		String[] envp = {
				"TMPDIR=" + Environment.getExternalStorageDirectory().getPath(),
				"PATH=" + baseDir + "/bin:" + baseDir + "/sbin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin",
				"ANDROID_ASSETS=/system/app",
				"ANDROID_BOOTLOGO=1",				
				"ANDROID_DATA=" + baseDir + "/var/dalvik",
				"ANDROID_ROOT=/system",
				"CCTOOLSDIR=" + baseDir,
				"CCTOOLSRES=" + getPackageResourcePath(),
				"LD_LIBRARY_PATH=" + baseDir + "/lib",
				"HOME=" + baseDir + "/home",
				"SHELL=" + getShell(),
				"TERM=xterm",
				"PS1=$ ",
				"SDDIR=" + sdHomeDir,
				"EXTERNAL_STORAGE=" + Environment.getExternalStorageDirectory().getPath(),
				};
		String[] argv = { "/system/bin/sh", "-c", "set"};
		int[] pId = new int[1];
		FileDescriptor fd = Utils.createSubProcess(baseDir, argv[0], argv, envp, pId);
		FileInputStream fis = new FileInputStream(fd);
		DataInputStream in = new DataInputStream(fis);
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		String line = "";
		try {
			while((line = reader.readLine()) != null) {
				if (line.startsWith(variable + "=")) {
					if (line.indexOf("=") != -1) {
						ret = line.substring(line.indexOf("=") + 1);
						break;
					}
				}
			}
			in.close();
			Utils.waitFor(pId[0]);
		} catch (Exception e) {
			Log.e(TAG, "exception " + e);
		}
		return ret;
	}
	
	protected String getShell() {
		String[] shellList = {
				toolchainDir + "/cctools/bin/bash",
				toolchainDir + "/cctools/bin/ash",
		};
		
		for (String shell: shellList) {
			if ((new File(shell)).exists()) {
				return shell;
			}
		}
		return "/system/bin/sh";
	}
	
	void createShellProfile() {
		if (!new File(homeDir + "/.profile").exists()) {
			try {
				FileOutputStream fos = new FileOutputStream(homeDir + "/.profile");
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos));
				writer.write("export PATH=" + toolchainDir + "/cctools/sbin:" + toolchainDir + "/cctools/bin:/system/xbin:/system/bin:$PATH");
				writer.newLine();
				writer.write("export LD_LIBRARY_PATH=" + toolchainDir + "/cctools/lib:$LD_LIBRARY_PATH");
				writer.newLine();
				writer.close();
				return;
			} catch (Exception e) {
				System.err.println("Cannot write BuildConfig.java " + e);
			}			
		}
	}
}