package com.pdaxrom.cctools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.droidparts.widget.ClearableEditText;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.pdaxrom.build.BuildView;
import com.pdaxrom.build.BuildViewInterface;
import com.pdaxrom.editor.CodeEditor;
import com.pdaxrom.editor.CodeEditorInterface;
import com.pdaxrom.pkgmanager.PkgManagerActivity;
import com.pdaxrom.utils.FileDialog;
import com.pdaxrom.utils.LogItem;
import com.pdaxrom.utils.SelectionMode;
import com.pdaxrom.utils.Utils;
import com.pdaxrom.utils.XMLParser;
import com.pdaxrom.term.TermView;
import com.pdaxrom.term.TermViewInterface;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.FragmentTransaction;
import android.text.Html;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.widget.Toast;
import android.widget.ViewFlipper;

public class CCToolsActivity extends /*SherlockActivity*/ FlexiDialogActivity
								implements ActionBar.TabListener,
											OnSharedPreferenceChangeListener,
											CodeEditorInterface,
											TermViewInterface,
											BuildViewInterface,
											FlexiDialogInterface {
	private Context context = this;
	public static final String SHARED_PREFS_NAME = "cctoolsSettings";
	private static final String SHARED_PREFS_FILES_EDITPOS = "FilesPosition";
	private SharedPreferences mPrefs;
	private static final String website_url = "http://cctools.info";
	private String TAG = "cctools";
	private String buildBaseDir; // Project base directory
	private boolean buildAfterSave = false;
	private boolean buildAfterLoad = false;
	private ImageButton newButton;
	private ImageButton openButton;
	private ImageButton playButton;
	private ImageButton buildButton;
	private ImageButton logButton;
	private ImageButton terminalButton;
	private ImageButton saveButton;
	private ImageButton saveAsButton;
	private ImageButton undoButton;
	private ImageButton redoButton;
	private View buttBar;
	
	private ViewFlipper flipper;
	private List<View> tabViews = null;	
	private CodeEditor codeEditor = null;
	private TermView termView = null;
	private BuildView buildView = null;
	
	private static final int TAB_EDITOR = 1;
	private static final int TAB_TERMINAL = 2;
	private static final int TAB_BUILD = 3;
	
	private static final int REQUEST_OPEN = 1;
	private static final int REQUEST_SAVE = 2;
	
	private static final int WARN_SAVE_AND_NEW = 1;
	private static final int WARN_SAVE_AND_LOAD = 2;
	private static final int WARN_SAVE_AND_LOAD_POS = 3;
	private static final int WARN_SAVE_AND_BUILD = 4;
	private static final int WARN_SAVE_AND_BUILD_FORCE = 5;
	private static final int WARN_SAVE_AND_CLOSE = 6;
	
	private static final int TEXT_GOTO = Menu.CATEGORY_CONTAINER + 1;
	private static final int TEXT_FIND = Menu.CATEGORY_CONTAINER + 2;
	private static final int TEXT_UNDO = Menu.CATEGORY_CONTAINER + 3;
	private static final int TEXT_REDO = Menu.CATEGORY_CONTAINER + 4;
	
	private boolean forceTmpVal;

	private String showFileName;
	private int showFileLine;
	private int showFilePos;
	
	private static final int SERVICE_STOP = 0;
	private static final int SERVICE_START = 1;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        getSupportActionBar().setDisplayShowHomeEnabled(false);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        
        mPrefs = getSharedPreferences(SHARED_PREFS_NAME, 0);

        tabViews = new ArrayList<View>();
        flipper = (ViewFlipper) findViewById(R.id.flipper);

        int tabsLoaded = loadTabs();
        
        if (tabsLoaded == 0) {
        	newFile();
        }

        setFlexiDialogInterface(this);

        showInfoAndCheckToolchain();

        newButton = (ImageButton) findViewById(R.id.newButton);
        newButton.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		//warnSaveDialog(WARN_SAVE_AND_NEW);
        		newFile();
        	}
        });
        openButton = (ImageButton) findViewById(R.id.pathButton);
        openButton.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		warnSaveDialog(WARN_SAVE_AND_LOAD);
        	}
        });
        saveButton = (ImageButton) findViewById(R.id.saveButton);
        saveButton.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		saveFile();
        	}
        });

        saveAsButton = (ImageButton) findViewById(R.id.saveAsButton);
        saveAsButton.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		saveAsFile();
        	}
        });

        playButton = (ImageButton) findViewById(R.id.playButton);
        playButton.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		warnSaveDialog(WARN_SAVE_AND_BUILD_FORCE);
        	}
        });
        
        buildButton = (ImageButton) findViewById(R.id.buildButton);
        buildButton.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		warnSaveDialog(WARN_SAVE_AND_BUILD);
        	}
        });

        logButton = (ImageButton) findViewById(R.id.logButton);
        logButton.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		showLog();
        	}
        });
        
        terminalButton = (ImageButton) findViewById(R.id.terminalButton);
        terminalButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				addTab(TAB_TERMINAL);
				newTitle(getString(R.string.console_name));
			}
        });
        
        undoButton = (ImageButton) findViewById(R.id.undoButton);
        undoButton.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		if (codeEditor != null) {
        			codeEditor.undo();
        		}
        	}
        });

        redoButton = (ImageButton) findViewById(R.id.redoButton);
        redoButton.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		if (codeEditor != null) {
        			codeEditor.redo();
        		}
        	}
        });
        
        buttBar = (View) findViewById(R.id.toolButtonsBar);

        serviceStartStop(SERVICE_START);

        mPrefs.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(mPrefs, null);
        
        onNewIntent(getIntent());
    }
    
    protected void onNewIntent(Intent intent) {
    	Log.i(TAG, "onNewIntent !!!!!!!!!!");

        // Get intent, action and MIME type
        String action = intent.getAction();
        String type = intent.getType();

        if ((Intent.ACTION_VIEW.equals(action) ||
        	 Intent.ACTION_EDIT.equals(action)) &&
        	 type != null) {
            if (type.startsWith("text/")) {
            	Uri uri = intent.getData();
            	String fileName = uri.getPath();
            	Log.i(TAG, "Load external file " + fileName);
            	if (!findAndShowTab(TAB_EDITOR, fileName)) {
                	addTab(TAB_EDITOR);
                	if (codeEditor.loadFile(fileName)) {
        				loadFileEditPos(codeEditor);
        				newTitle(new File(fileName).getName());
                	}
            	}
            }
        } else {
            type = intent.getStringExtra("type");
            
            Log.w(TAG, "type = " + type);
            
            if (type != null) {
                if (type.contentEquals("build")) {
                	String cmdline = intent.getStringExtra("cmdline");
                	String workDir = intent.getStringExtra("workdir");
                	String name = intent.getStringExtra("name");
                	Log.i(TAG, "BUILD NAME " + name);
                	if (cmdline != null) {
                		if (!findAndShowTab(TAB_BUILD, name)) {
                			addTab(TAB_BUILD);
                			buildView.setName(name);
                		}
                		buildView.start(cmdline, workDir, getToolchainDir() + "/cctools", getPackageResourcePath(), getTempDir());
                	}
                } else if (type.contentEquals("module")) {
                	String path = intent.getStringExtra("path");
                	String workdir = intent.getStringExtra("workdir");
                	String file = intent.getStringExtra("file");
                	String uuid = intent.getStringExtra("dialogid");
                	
                	Log.i(TAG, "module " + path + " " + workdir + " " + file);
                	
                	dialogFromModule(path, workdir, file, uuid);
                } else if (type.contentEquals("terminal")) {
                	String cmdline = intent.getStringExtra("cmdline");
                	String workDir = intent.getStringExtra("workdir");
                	String title = intent.getStringExtra("title");
                	if (title == null) {
                		title = getString(R.string.console_name);
                	}
                	if (cmdline != null) {
                		Log.i(TAG, "CMDLINE = " + cmdline);
                		addTerminalTab(cmdline, workDir);
        				newTitle(title);
                	}                	
                }
            }
        }
    }
    
    private void updateEditorPrefs(SharedPreferences prefs, View tab) {
    	if (tab instanceof CodeEditor) {
    		CodeEditor editor = (CodeEditor) tab;
    		editor.setTextSize(Float.valueOf(prefs.getString("fontsize", "12")));
    		editor.showSyntax(prefs.getBoolean("syntax", true));
    		editor.drawLineNumbers(prefs.getBoolean("drawLineNumbers", true));
    		editor.drawGutterLine(prefs.getBoolean("drawLineNumbers", true));
    		editor.setAutoPair(prefs.getBoolean("autopair", true));
    		editor.setAutoIndent(prefs.getBoolean("autoindent", true));
    	} else if (tab instanceof TermView) {
    		TermView termView = (TermView) tab;
    		termView.setDensity(getResources().getDisplayMetrics());
            termView.setTextSize(Integer.valueOf(mPrefs.getString("console_fontsize", "12")));
    	}
    }
    
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		Log.i(TAG, "onSharedPreferenceChanged()");
		for (View tab: tabViews) {
			updateEditorPrefs(prefs, tab);
		}
		if (prefs.getBoolean("showToolBar", true)) {
			buttBar.setVisibility(View.VISIBLE);
		} else {
			buttBar.setVisibility(View.GONE);
		}
	}

	protected void onDestroy() {
		saveTabs();
        serviceStartStop(SERVICE_STOP);

		Log.i(TAG, "Clean temp directory");
		Utils.deleteDirectory(new File(getToolchainDir() + "/tmp"));
		super.onDestroy();
	}

	public void onBackPressed() {
		if (termView != null) {
			if (termView.isAlive()) {
				termView.hangup();
				
				return;
			}
		}
		boolean hasChanged = false;
		for (int i = 0; i < flipper.getChildCount(); i++) {
			CodeEditor code = (CodeEditor) flipper.getChildAt(i).findViewById(R.id.codeEditor);
			if (code != null) {
				if (code.hasChanged()) {
					hasChanged = true;
				}
			}
		}
		if (hasChanged) {
			exitDialog();
		} else {
			super.onBackPressed();
		}
	}
	
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			menu.add(0, TEXT_GOTO, 0, getString(R.string.menu_goto));
			menu.add(0, TEXT_FIND, 0, getString(R.string.menu_search));
			menu.add(0, TEXT_UNDO, 0, getString(R.string.menu_undo));
			menu.add(0, TEXT_REDO, 0, getString(R.string.menu_redo));
		}
		super.onCreateContextMenu(menu, v, menuInfo);
	}

	public boolean onContextItemSelected(android.view.MenuItem item) {
		switch (item.getItemId()) {
		case TEXT_GOTO:
			gotoDialog();
			break;
		case TEXT_FIND:
			searchDialog();
			break;
		case TEXT_UNDO:
			if (codeEditor != null) {
				codeEditor.undo();
			}
			break;
		case TEXT_REDO:
			if (codeEditor != null) {
				codeEditor.redo();
			}
			break;
		default:
			return super.onContextItemSelected(item);			
		}
		return true;
	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
		getSupportMenuInflater().inflate(R.menu.menu, menu);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			menu.add(0, TEXT_UNDO, 0, getString(R.string.menu_undo)).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
			menu.add(0, TEXT_REDO, 0, getString(R.string.menu_redo)).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
			menu.add(0, TEXT_GOTO, 0, getString(R.string.menu_goto)).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
			menu.add(0, TEXT_FIND, 0, getString(R.string.menu_search)).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
		}
    	return true;
    }
    
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        	case R.id.item_new:
        		//warnSaveDialog(WARN_SAVE_AND_NEW);
        		newFile();
        		break;
        	case R.id.item_open:
        		warnSaveDialog(WARN_SAVE_AND_LOAD);
        		break;
        	case R.id.item_save:
        		saveFile();
        		break;
        	case R.id.item_saveas:
        		saveAsFile();
        		break;
        	case R.id.item_close:
        		if (codeEditor != null) {
        			warnSaveDialog(WARN_SAVE_AND_CLOSE);
        			break;
        		} else if (termView != null) {
        			if (termView.isAlive()) {
        				termView.hangup();
        			}
        			termView.stop();
        		} else if (buildView != null) {
        			if (buildView.isRunning()) {
        				buildView.stop();
        			}
        			
        		}
    			int i = flipper.getDisplayedChild();
    			flipper.removeViewAt(i);
    			getSupportActionBar().removeTabAt(i);				
    			if (flipper.getChildCount() == 0) {
    				finish();
    			}
        		break;
        	case R.id.item_run:
        		warnSaveDialog(WARN_SAVE_AND_BUILD_FORCE);
        		break;
        	case R.id.item_build:
        		warnSaveDialog(WARN_SAVE_AND_BUILD);
        		break;
        	case R.id.item_buildlog:
        		showLog();
        		break;
        	case R.id.item_terminal:
        		addTab(TAB_TERMINAL);
				newTitle(getString(R.string.console_name));
        		break;
        	case R.id.item_pkgmgr:
        		packageManager();
        		break;
        	case R.id.prefs:
        		startActivity(new Intent(this, Preferences.class));
        		break;
        	case R.id.about:
        		aboutDialog();
        		break;
        	case TEXT_GOTO:
        		gotoDialog();
        		break;
        	case TEXT_FIND:
        		searchDialog();
        		break;
        	case TEXT_UNDO:
        		if (codeEditor != null) {
        			codeEditor.undo();
        		}
        		break;
        	case TEXT_REDO:
        		if (codeEditor != null) {
        			codeEditor.redo();
        		}
        		break;
        	case R.id.item_modules:
        		showModules();
        		break;
        }
        return true;
    }

	public void onTabSelected(Tab tab, FragmentTransaction ft) {
        Log.i(TAG, "onTabSelected ");
		flipper.setDisplayedChild(tab.getPosition());
        codeEditor = (CodeEditor) flipper.getChildAt(tab.getPosition()).findViewById(R.id.codeEditor);
        termView = (TermView) flipper.getChildAt(tab.getPosition()).findViewById(R.id.emulatorView);
        buildView = (BuildView) flipper.getChildAt(tab.getPosition()).findViewById(R.id.buildLog);
	}

	public void onTabUnselected(Tab tab, FragmentTransaction ft) {
	}

	public void onTabReselected(Tab tab, FragmentTransaction ft) {
	}

	public void textHasChanged(boolean hasChanged) {
		if (getSupportActionBar().getSelectedTab().getText() == null) {
			return;
		}
		String title = getSupportActionBar().getSelectedTab().getText().toString();
		if (title != null) {
			if (!hasChanged && title.startsWith("*")) {
				title = title.substring(1);
			} else if (hasChanged && !title.startsWith("*")) {
				title = "*" + title;
			}
			getSupportActionBar().getSelectedTab().setText(title);
		}
	}

	public void titleHasChanged(ActionBar.Tab tab, String title) {
//		if (getSupportActionBar().getSelectedTab().getText() == null) {
//			return;
//		}
//		Log.i(TAG, "New term title " + title);
		tab.setText(title);
	}

    private int loadTabs() {
	    SharedPreferences settings = getPreferences(Activity.MODE_PRIVATE);
	    int total = settings.getInt("TabsCount", 0);
	    int skipped = 0;
	    
	    if (total > 0) {
	    	for (int i = 0; i < total; i++) {
	    		String fileName = settings.getString("TabN" + i, "");
	    		if (fileName == "") {
	    			newFile();
	    		} else {
	    			if (new File(fileName).exists()) {
		    			addTab(TAB_EDITOR);
		            	if (codeEditor.loadFile(fileName)) {
		    				loadFileEditPos(codeEditor);
		    				newTitle(new File(fileName).getName());
		            	} else {
		            		newTitle(getString(R.string.new_file));
		            	}
	    			} else {
	    				skipped++;
	    			}
	    		}
	    	}
	    	int currentTab = settings.getInt("CurrentTab", 0);
	    	if (skipped > 0 && currentTab > flipper.getChildCount() - 1) {
	    		currentTab = 0;
	    	}
    		getSupportActionBar().setSelectedNavigationItem(currentTab);
	    	
	    }
	    
	    return total - skipped;
    }
    
    private void saveTabs() {
		SharedPreferences settings = getPreferences(Activity.MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();
		editor.putInt("TabsCount", flipper.getChildCount());
		if (flipper.getChildCount() > 0) {
			editor.putInt("CurrentTab", flipper.getDisplayedChild());
		}
		for (int i = 0; i < flipper.getChildCount(); i++) {
			CodeEditor code = (CodeEditor) flipper.getChildAt(i).findViewById(R.id.codeEditor);
			if (code != null) {
				String fileName = code.getFileName();
				if (fileName == null) {
					fileName = "";
				} else {
					saveFileEditPos(code);				
				}
				editor.putString("TabN" + i, fileName);
			} else {
				TermView term = (TermView) flipper.getChildAt(i).findViewById(R.id.emulatorView);
				if (term != null) {
					if (term.isAlive()) {
						term.hangup();
					}
					term.stop();
				}
			}
		}
		editor.commit();
    }
    
    private boolean findAndShowTab(int type, String newName) {
    	if (newName != null) {
        	for (int i = 0; i < flipper.getChildCount(); i++) {
        		String name;
        		if (type == TAB_EDITOR) {
            		CodeEditor code = (CodeEditor) flipper.getChildAt(i).findViewById(R.id.codeEditor);
            		if (code != null) {
                		name = code.getFileName();
            		} else {
            			continue;
            		}
        		} else if (type == TAB_BUILD) {
        			BuildView build = (BuildView) flipper.getChildAt(i).findViewById(R.id.buildLog);
        			if (build != null) {
        				name = build.getName();
        			} else {
        				continue;
        			}
        		} else {
        			break;
        		}
        		if (name != null && name.equals(newName)) {
        			getSupportActionBar().setSelectedNavigationItem(i);
        			return true;
        		}
        	}
    	}
    	return false;
    }
    
    private void addTab(int type) {
    	TermView term = null;
    	BuildView build = null;
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (type == TAB_EDITOR) {
        	View view = inflater.inflate(R.layout.editor, flipper, false);
            flipper.addView(view);
        	
            CodeEditor code = (CodeEditor) view.findViewById(R.id.codeEditor);
            updateEditorPrefs(mPrefs, code);
            code.setCodeEditorInterface(this);
            tabViews.add(code);
            registerForContextMenu(code);
        } else if (type == TAB_TERMINAL) {
    		String workDir = getToolchainDir() + "/cctools/home";
    		if (codeEditor != null) {
        		String fileName = codeEditor.getFileName();
        		if (fileName != null && (new File(fileName)).exists()) {
        			workDir = (new File(fileName)).getParentFile().getAbsolutePath();
        		}
    		}
    		
        	View view = inflater.inflate(R.layout.term, flipper, false);
        	flipper.addView(view);
        	
        	term = (TermView) view.findViewById(R.id.emulatorView);
        	tabViews.add(term);
//        	term.setTermViewInterface(this, tab);
        	term.start("-" + getShell(), workDir, getToolchainDir() + "/cctools");
        	updateEditorPrefs(mPrefs, term);
        	//registerForContextMenu(tabView);
        } else if (type == TAB_BUILD) {
        		View view = inflater.inflate(R.layout.build, flipper, false);
        		flipper.addView(view);
        		
        		build = (BuildView) view.findViewById(R.id.buildLog);
        		tabViews.add(build);
        		updateEditorPrefs(mPrefs, build);
        }

        ActionBar.Tab tab = getSupportActionBar().newTab();
        tab.setTabListener(this);
        getSupportActionBar().addTab(tab);
        getSupportActionBar().selectTab(tab);
        
        switch(type) {
        case TAB_TERMINAL:
        	term.setTermViewInterface(this, tab);
        	break;
        case TAB_BUILD:
        	build.setInterface(this, tab);
        	break;
        }
    }
    
    private void addTerminalTab(String cmdline, String workDir) {
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    	View view = inflater.inflate(R.layout.term, flipper, false);
    	flipper.addView(view);
    	
    	TermView term = (TermView) view.findViewById(R.id.emulatorView);
    	tabViews.add(term);
    	if (workDir == null) {
    		workDir = getToolchainDir() + "/cctools/home";
    	}
    	term.start(cmdline, workDir, getToolchainDir() + "/cctools");
    	updateEditorPrefs(mPrefs, term);
    	
        ActionBar.Tab tab = getSupportActionBar().newTab();
        tab.setTabListener(this);
        getSupportActionBar().addTab(tab);
        getSupportActionBar().selectTab(tab);
        
        term.setTermViewInterface(this, tab);        	
    }
    
    private String getLastOpenedDir() {
    	String lastDir = getPrefString("lastdir", Environment.getExternalStorageDirectory().getPath() + "/CCTools/Examples");
    	return lastDir;
    }
    
    private void setLastOpenedDir(String dir) {
    	setPrefString("lastdir", dir);
    }
    
    private void newTitle(String title) {
    	getSupportActionBar().getSelectedTab().setText(title);
    }
    
    private void newFile() {
    	addTab(TAB_EDITOR);
        if (codeEditor == null) {
        	Log.w(TAG, "new file codeEditor == null");
        } else {
        	Log.w(TAG, "new file codeEditor !!!");
        }

		newTitle(getString(R.string.new_file));
		buildAfterSave = false;
		buildAfterLoad = false;
		codeEditor.newFile();
		Toast.makeText(getBaseContext(), getString(R.string.new_file), Toast.LENGTH_SHORT).show();    	
    }
    
    private void loadFile() {
		Intent intent = new Intent(getBaseContext(), FileDialog.class);
		String dir = getLastOpenedDir();
		if (codeEditor != null) {
			String fileName = codeEditor.getFileName();
			if (fileName != null && new File(fileName).getParentFile().exists()) {
				dir = (new File(fileName)).getParent();
			}
		}
		if (dir == null || !new File(dir).exists()) {
			dir = Environment.getExternalStorageDirectory().getPath();
		}
		
		intent.putExtra(FileDialog.START_PATH, dir);
		intent.putExtra(FileDialog.SELECTION_MODE, SelectionMode.MODE_OPEN);
		startActivityForResult(intent, REQUEST_OPEN);
    }
    
    private void saveFile() {
    	if (codeEditor != null) {
        	String fileName = codeEditor.getFileName();
    		if (fileName == null || fileName.equals("")) {
    			String dir = getLastOpenedDir();
    			if (fileName != null && new File(dir).getParentFile().exists()) {
    				dir = (new File(fileName)).getParent();
    			}
    			if (dir == null || !new File(dir).exists()) {
    				dir = Environment.getExternalStorageDirectory().getPath();
    			}
        		Intent intent = new Intent(getBaseContext(), FileDialog.class);
        		intent.putExtra(FileDialog.START_PATH, dir);
        		intent.putExtra(FileDialog.SELECTION_MODE, SelectionMode.MODE_CREATE);
        		startActivityForResult(intent, REQUEST_SAVE);
    		} else {
    			if (codeEditor.saveFile(fileName)) {
    				saveFileEditPos(codeEditor);
    				Toast.makeText(getBaseContext(), getString(R.string.file_saved), Toast.LENGTH_SHORT).show();
    				setLastOpenedDir((new File (fileName)).getParent());
    			} else {
    				Toast.makeText(getBaseContext(), getString(R.string.file_not_saved), Toast.LENGTH_SHORT).show();
    			}
    			if (buildAfterSave) {
    				buildFile(forceTmpVal);
    				buildAfterSave = false;
    			}
    		}
    	}
    }
    
    private void saveAsFile() {
		Intent intent = new Intent(getBaseContext(), FileDialog.class);
		String dir = getLastOpenedDir();
		if (codeEditor != null) {
			String fileName = codeEditor.getFileName();
			if (fileName != null && new File(fileName).getParentFile().exists()) {
				dir = (new File(fileName)).getParent();
			}
			if (dir == null || !new File(dir).exists()) {
				dir = Environment.getExternalStorageDirectory().getPath();
			}
			intent.putExtra(FileDialog.START_PATH, dir);
			intent.putExtra(FileDialog.SELECTION_MODE, SelectionMode.MODE_CREATE);
			startActivityForResult(intent, REQUEST_SAVE);
		}
    }
    
    private void saveFileEditPos(CodeEditor code) {
    	SharedPreferences settings = getSharedPreferences(SHARED_PREFS_FILES_EDITPOS, 0);
    	SharedPreferences.Editor editor = settings.edit();
    	editor.putInt(code.getFileName(), code.getSelectionStart());
    	editor.commit();
    }

    private void loadFileEditPos(CodeEditor code) {
    	SharedPreferences settings = getSharedPreferences(SHARED_PREFS_FILES_EDITPOS, 0);
    	if (code.getText().toString().length() >= settings.getInt(code.getFileName(), 0)) {
    		code.setSelection(settings.getInt(code.getFileName(), 0));
    	}
    }

    private void build(boolean force) {
    	if (codeEditor == null || codeEditor.getFileName() == null || codeEditor.getFileName().equals("")) {
			buildAfterLoad = true;
			forceTmpVal = force;
			loadFile();
    	} else if (codeEditor.hasChanged() && codeEditor.getText().length() > 0) {
			buildAfterSave = true;
			forceTmpVal = force;
			saveFile();
		} else {
			buildFile(force);
		}
    }
    
    private void buildFile(final boolean force) {
    	if (codeEditor != null) {
    		new Thread() {
    			public void run() {
    	    		String argv[] = {
    	        			"build-helper",
    	        			"--openbuild",
    	        			Boolean.toString(force),
    	        			"--buildwindow",
    	        			Boolean.toString(!force),
    	        			"--executable",
    	        			Boolean.toString(force),
    	        			"--run",
    	        			Boolean.toString(force),
    	        			"--runwindow",
    	        			Boolean.toString(!mPrefs.getBoolean("force_run", true)),
    	        			codeEditor.getFileName()
    	        		};
    	    		
    	    		system(argv);
    			}
    		}.start();
    	}
    }
    
	static private final String KEY_FILE = "file";
	static private final String KEY_LINE = "line";
	static private final String KEY_POS  = "pos";
	static private final String KEY_TYPE = "type";
	static private final String KEY_MESG = "mesg";

	public class SimpleHtmlAdapter extends SimpleAdapter {
		public SimpleHtmlAdapter(Context context, List<HashMap<String, String>> items, int resource, String[] from, int[] to) {
			super(context, items, resource, from, to);
		}

	    public void setViewText (TextView view, String text) {
	        view.setText(Html.fromHtml(text),BufferType.SPANNABLE);
	    }
	}
	
    private void showLog() {
    	if (buildView == null) {
    		return;
    	}
    	if (buildView.getLog().isEmpty()) {
    		Toast.makeText(getBaseContext(), getString(R.string.log_empty), Toast.LENGTH_SHORT).show();
    		return;
    	}
    	ArrayList<HashMap<String, String>> menuItems = new ArrayList<HashMap<String, String>>();
    	for (LogItem item: buildView.getLog()) {
    		HashMap<String, String> map = new HashMap<String, String>();
    		map.put(KEY_FILE, item.getFile());
    		map.put(KEY_LINE, Integer.toString(item.getLine()));
    		map.put(KEY_POS, Integer.toString(item.getPos()));
    		map.put(KEY_TYPE, item.getType());
    		String color = "<font color=\"";
    		if (item.getType().contains("error")) {
    			color += "red\">ERROR: ";
    		} else {
    			color += "yellow\">WARNING: ";
    		}
    		map.put(KEY_MESG, color + item.getMessage() + "</font>");
    		menuItems.add(map);
    	}
    	final ListView listView = new ListView(this);
    	listView.setAdapter(new SimpleHtmlAdapter(
        		this,
        		menuItems,
        		R.layout.buildlog_item,
        		new String [] { KEY_FILE, KEY_LINE, KEY_POS, KEY_TYPE, KEY_MESG },
        		new int[] {R.id.buildlog_file, R.id.buildlog_line, R.id.buildlog_pos, R.id.buildlog_type, R.id.buildlog_mesg}
        	));
    	AlertDialog.Builder dialog = new AlertDialog.Builder(this);
    	dialog.setView(listView);
    	final AlertDialog alertDialog = dialog.create();
    	
    	listView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position,
					long id) {
				int line = Integer.parseInt(((TextView) view.findViewById(R.id.buildlog_line)).getText().toString());
				int pos = Integer.parseInt(((TextView) view.findViewById(R.id.buildlog_pos)).getText().toString());
				String name = ((TextView) view.findViewById(R.id.buildlog_file)).getText().toString();
				
				if (!name.startsWith("/")) {
					name = buildBaseDir + "/" + name;
				}
				
				if (codeEditor != null) {
					if (!(new File(codeEditor.getFileName())).getAbsolutePath().contentEquals((new File(name)).getAbsolutePath())) {
						alertDialog.cancel();
						showFileName = name;
						showFileLine = line;
						showFilePos = pos;
		            	Log.i(TAG, "Jump to file " + showFileName);
						warnSaveDialog(WARN_SAVE_AND_LOAD_POS);
					} else {
						if (pos > 0) {
							codeEditor.goToLinePos(line, pos);
						} else {
							codeEditor.goToLine(line);
						}
						alertDialog.cancel();
					}
				}
			}    		
    	});
    	alertDialog.show();
    }
    
    private void packageManager() {
    	Intent intent = new Intent(CCToolsActivity.this, PkgManagerActivity.class);
    	startActivity(intent);
    }
    
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	super.onActivityResult(requestCode, resultCode, data);
    	String fileName;
		if (resultCode == RESULT_OK) {
			if (requestCode == REQUEST_SAVE) {
    			fileName = data.getStringExtra(FileDialog.RESULT_PATH);
    			setLastOpenedDir((new File (fileName)).getParent());
    			if (codeEditor.saveFile(fileName)) {
    				saveFileEditPos(codeEditor);
    				Toast.makeText(getBaseContext(), getString(R.string.file_saved), Toast.LENGTH_SHORT).show();
    				if (buildAfterSave) {
    					buildAfterSave = false;
    					buildFile(forceTmpVal);
    				}
    			} else {
    				Toast.makeText(getBaseContext(), getString(R.string.file_not_saved), Toast.LENGTH_SHORT).show();
    				buildAfterSave = false;
    			}
				newTitle(new File(fileName).getName());
			} else if (requestCode == REQUEST_OPEN) {
    			fileName = data.getStringExtra(FileDialog.RESULT_PATH);
    			setLastOpenedDir((new File (fileName)).getParent());
    			if (!findAndShowTab(TAB_EDITOR, fileName)) {
        			addTab(TAB_EDITOR);
        			if (codeEditor.loadFile(fileName)) {
        				loadFileEditPos(codeEditor);
        				Toast.makeText(getBaseContext(), getString(R.string.file_loaded), Toast.LENGTH_SHORT).show();
        				if (buildAfterLoad) {
        					buildAfterLoad = false;
        					buildFile(forceTmpVal);
        				}
        			} else {
        				Toast.makeText(getBaseContext(), getString(R.string.file_not_loaded), Toast.LENGTH_SHORT).show();
        				buildAfterLoad = false;
        			}
        			newTitle(new File(fileName).getName());
    			}
    		}
    	}
    }

    private void exitDialog() {
    	new AlertDialog.Builder(this)
        .setIcon(android.R.drawable.ic_dialog_alert)
        .setTitle(getString(R.string.exit_dialog))
        .setMessage(getString(R.string.exit_dialog_text))
        .setPositiveButton(getString(R.string.exit_dialog_yes), new DialogInterface.OnClickListener() {
        	public void onClick(DialogInterface dialog, int which) {
        		finish();
        	}
        })
        .setNegativeButton(getString(R.string.exit_dialog_no), null)
        .show();
    }

    private void loadAndShowLinePos() {
    	if (findAndShowTab(TAB_EDITOR, showFileName)) {
			if (showFilePos > 0) {
				codeEditor.goToLinePos(showFileLine, showFilePos);
			} else {
				codeEditor.goToLine(showFileLine);
			}    		
    	} else {
        	addTab(TAB_EDITOR);
    		if (codeEditor.loadFile(showFileName)) {
    			Toast.makeText(getBaseContext(), getString(R.string.file_loaded), Toast.LENGTH_SHORT).show();
    			if (showFilePos > 0) {
    				codeEditor.goToLinePos(showFileLine, showFilePos);
    			} else {
    				codeEditor.goToLine(showFileLine);
    			}
    			newTitle(new File(showFileName).getName());
    		} else {
    			Toast.makeText(getBaseContext(), getString(R.string.file_not_loaded), Toast.LENGTH_SHORT).show();
    		}
    	}
    }
    
	private void warnSaveRequest(int req) {
		switch (req) {
		case WARN_SAVE_AND_NEW:
    		newFile();
    		break;
		case WARN_SAVE_AND_LOAD:
			loadFile();
			break;
		case WARN_SAVE_AND_LOAD_POS:
			loadAndShowLinePos();
			break;
		case WARN_SAVE_AND_BUILD:
			build(false);
			break;
		case WARN_SAVE_AND_BUILD_FORCE:
			build(true);
			break;
		case WARN_SAVE_AND_CLOSE:
			int i = flipper.getDisplayedChild();
			flipper.removeViewAt(i);
			getSupportActionBar().removeTabAt(i);				
			if (flipper.getChildCount() == 0) {
				finish();
			}
			break;
		}
	}
	
    private void warnSaveDialog(final int req) {
    	if (codeEditor == null || !codeEditor.hasChanged()) {
    		warnSaveRequest(req);
    		return;
    	}
    	
    	new AlertDialog.Builder(this)
        .setIcon(android.R.drawable.ic_dialog_alert)
        .setTitle(getString(R.string.save_warn_dialog))
        .setMessage(getString(R.string.save_warn_text))
        .setPositiveButton(getString(R.string.exit_dialog_yes), new DialogInterface.OnClickListener() {
        	public void onClick(DialogInterface dialog, int which) {
        		warnSaveRequest(req);
        	}
        })
        .setNegativeButton(getString(R.string.exit_dialog_no), null)
        .show();
    }
    
    private void aboutDialog() {
    	String versionName;
		try {
			versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0 ).versionName;
		} catch (NameNotFoundException e) {
			versionName = "1.0";
		}
		final TextView textView = new TextView(this);
		textView.setAutoLinkMask(Linkify.WEB_URLS);
		textView.setLinksClickable(true);
		textView.setTextSize(16f);
		textView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
		textView.setText(getString(R.string.about_dialog_text) +
									" " + 
									versionName + 
									"\n" + website_url + "\n" +
									getString(R.string.about_dialog_text2));
		textView.setMovementMethod(LinkMovementMethod.getInstance());
		new AlertDialog.Builder(this)
	    .setTitle(getString(R.string.about_dialog))
	    .setView(textView)
	    .show();
    }
    
    private void gotoDialog() {
    	final ClearableEditText input = new ClearableEditText(context);
    	input.setInputType(InputType.TYPE_CLASS_NUMBER);
    	input.setSingleLine(true);
    	new AlertDialog.Builder(context)
    	.setMessage(getString(R.string.goto_line))
    	.setView(input)
    	.setPositiveButton(getString(R.string.button_continue), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				try {
					codeEditor.goToLine(Integer.valueOf(input.getText().toString()));
				} catch (Exception e) {
					Log.e(TAG, "gotoDialog() " + e);
				}
			}
		})
		.setNegativeButton(getString(R.string.button_cancel), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				
			}
		})
		.show();
    }
    
    private void searchDialog() {
    	final ClearableEditText input = new ClearableEditText(context);
    	input.setInputType(InputType.TYPE_CLASS_TEXT);
    	input.setSingleLine(true);
    	input.setText(codeEditor.getLastSearchText());
    	input.setSelection(0, codeEditor.getLastSearchText().length());
    	new AlertDialog.Builder(context)
    	.setMessage(getString(R.string.search_string))
    	.setView(input)
    	.setPositiveButton(getString(R.string.button_continue), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				codeEditor.searchText(input.getText().toString());
			}
		})
		.setNegativeButton(getString(R.string.button_cancel), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				
			}
		})
		.show();    	
    }
    
    private void showModules() {
    	final ListView listView = new ListView(this);
    	//final Spinner spinner = new Spinner(this);
    	List<String> list = new ArrayList<String>();
    	final List<String> modules = new ArrayList<String>();

    	FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				String lowercaseName = name.toLowerCase(getResources().getConfiguration().locale);
				if (lowercaseName.endsWith(".xml")) {
					return true;
				} else {
					return false;
				}
			}
		};

		String[] rulesDirs = {
			getToolchainDir() + "/cctools/share/modules",
			getSDHomeDir() + "/share/modules"
		};
		
		for (String rulesDir: rulesDirs) {
			File dir = new File(rulesDir);
			if (dir.exists() && dir.isDirectory()) {
				for (String fileName: dir.list(filter)) {
					XMLParser xmlParser = new XMLParser();
					String xml = xmlParser.getXmlFromFile(rulesDir + "/" + fileName);
					if (xml == null) {
						continue;
					}
					Document doc = xmlParser.getDomElement(xml);
					if (doc == null) {
						Log.i(TAG, "bad xml file " + rulesDir + "/" + fileName);
						continue;
					}
					NodeList nl = doc.getElementsByTagName("cctools-module");
					Element e = (Element) nl.item(0);
					if (e == null) {
						continue;
					}
					String title = getBuiltinVariable(getLocalizedAttribute(e, "title"));
					if (title != null && ! title.equals("")) {
						list.add(title);
						modules.add(rulesDir + "/" + fileName);
					}
				}
			}
		}
		
		if (modules.size() == 0) {
			// no modules installed
			return;
		}
		
    	ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
    	        android.R.layout.simple_list_item_1, list);
    	
    	listView.setAdapter(adapter);

		
		
		ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, list);
		dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		
		final AlertDialog dialog = new AlertDialog.Builder(this)
		.setTitle(getText(R.string.module_select))
//		.setMessage(getText(R.string.module_select_message))
		.setView(listView)
		.setCancelable(true)
		.show();
		
    	listView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				Log.i(TAG, "selected action " + position);
				dialog.dismiss();
				String dialogid = null;
				if (codeEditor != null) {
					dialogid = codeEditor.getFileName();
					if (dialogid != null) {
						dialogid = new File(dialogid).getName();
					}
				}
				dialogFromModule(modules.get(position), null, null, dialogid);
			}
    	});
    }

	public String getBuiltinVariable(String name) {
		if (name.contains("$current_file$")) {
			String file = "";
			if (codeEditor != null) {
				file = codeEditor.getFileName();
				if (file == null) {
					file = "";
				}
			}
			name = name.replace("$current_file$", new File(file).getName());
		}

		if (name.contains("$current_dir$")) {
			String dir = "";
			if (codeEditor != null) {
				String file = codeEditor.getFileName();
				if (file != null) {
					dir = new File(file).getParent();
				}
			}
			name = name.replace("$current_dir$", dir);
		}

		return name;
	}	
    
    private void showInfoAndCheckToolchain() {
    	PackageInfo packageInfo;
		try {
			packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
	    	int versionCode = packageInfo.versionCode;
	    	if (getPrefString("versionCode").equals(String.valueOf(versionCode))) {
	    		installOrUpgradeToolchain();
	    	} else {
	    		setPrefString("versionCode", String.valueOf(versionCode));
	    		String language = getResources().getConfiguration().locale.getLanguage();
	    		
	    		InputStream stream = null;
	    		try {
					stream = getAssets().open("whatsnew-" + language);
				} catch (IOException e) {
					Log.e(TAG, "Assets file whatsnew" + language + " not found");
					stream = null;
				}
	    		if (stream == null) {
		    		try {
						stream = getAssets().open("whatsnew");
					} catch (IOException e) {
						Log.e(TAG, "Assets file whatsnew not found");
						installOrUpgradeToolchain();
						return;
					}
	    		}

	    		StringBuilder buf = new StringBuilder();
	    		BufferedReader in = new BufferedReader(new InputStreamReader(stream));
	    		String str;
	    		String message = "";
	    		
	    		try {
					while((str = in.readLine()) != null) {
						buf.append(str + "\n");
					}
					in.close();
					message = buf.toString();
				} catch (IOException e) {
					Log.e(TAG, "Error reading whatsnew file");
				}
	    		
	    		final TextView textView = new TextView(this);
	    		textView.setAutoLinkMask(Linkify.WEB_URLS);
	    		textView.setLinksClickable(true);
	    		textView.setMovementMethod(LinkMovementMethod.getInstance());
	    		textView.setText(message);
	    		
	    		new AlertDialog.Builder(this)
	    		.setIcon(android.R.drawable.ic_dialog_info)
	    		.setTitle(getString(R.string.whatisnew))
	    		.setView(textView)
	    		.setNeutralButton(getString(R.string.button_continue), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
			    		installOrUpgradeToolchain();
					}
	    		})
				.setCancelable(false)
				.show();
	    		
	    	}
		} catch (NameNotFoundException e) {
			Log.e(TAG, "Package name not found: " + e);
		}
    }
    
    private void installOrUpgradeToolchain() {
		if (!getPrefString("toolchain_installed").equals("yes")) {
			installToolchainPackage();
		} else {
			if (mPrefs.getBoolean("updater", true)) {
				Intent intent = new Intent(this, PkgManagerActivity.class);
				intent.putExtra(PkgManagerActivity.INTENT_CMD, PkgManagerActivity.CMD_UPDATE);
				startActivity(intent);
			}
		}
    }

	private int toolchainPackageToInstall = 0;
    private void installToolchainPackage() {
    	final String[] toolchainPackage = {
    			"build-essential-clang-compact",
    			"build-essential-gcc-compact",
    			"build-essential-fortran-compact",
    			"build-essential-gcc-avr",
    			"build-essential-mingw-w64",
    			"build-essential-luajit"
    	};
    	
    	setPrefString("toolchain_installed", "yes");
    	
		final Spinner spinner = new Spinner(context);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
		        R.array.toolchain_selector, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);	
		
		spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> arg0, View view,
					int position, long id) {
				Log.i(TAG, "selected " + toolchainPackage[position]);
				toolchainPackageToInstall = position;
			}
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});
		
		new AlertDialog.Builder(this)
		.setTitle(getText(R.string.toolchain_selector))
		.setMessage(getText(R.string.toolchain_selectormsg))
		.setView(spinner)
		.setPositiveButton(getText(R.string.pkg_install), new DialogInterface.OnClickListener() {			
			public void onClick(DialogInterface dialog, int which) {
	        	Intent intent = new Intent(context, PkgManagerActivity.class);
	        	intent.putExtra(PkgManagerActivity.INTENT_CMD, PkgManagerActivity.CMD_INSTALL);
	        	intent.putExtra(PkgManagerActivity.INTENT_DATA, toolchainPackage[toolchainPackageToInstall]);
	        	startActivity(intent);
			}
		})
		.setCancelable(false)
		.show();
    }
   
	private void serviceStartStop(final int cmd) {
		String serviceCmd;
		if (cmd == SERVICE_START) {
			serviceCmd = "start";
		} else {
			serviceCmd = "stop";
		}
		Log.i(TAG, "Console services " + serviceCmd);
		File dir = new File(getServiceDir());
		if (dir.exists()) {
			String services[] = dir.list();
			for (final String service: services) {
				Log.i(TAG, "Service " + service + " " + serviceCmd);
				new Thread() {
					public void run() {
						String serviceCmd;
						if (cmd == SERVICE_START) {
							serviceCmd = "start";
						} else {
							serviceCmd = "stop";
						}
						String[] argv = {
							getServiceDir() + "/" + service,
							serviceCmd
						};
						system(argv);
					}
				}.start();
			}			
		}
	}
}
