package uk.co.metricrat.webviewextra;

// Juan Antonio Villalpando  - kio4.com
// com.KIO4_WebViewExtra
// TIMAI2 - ai2.metricrat.co.uk
// Enero 2023.


import android.annotation.*;
import android.app.*;
import android.content.*;
import android.graphics.drawable.*;
import android.os.*;
import android.util.Base64;
import android.webkit.*;
import android.webkit.DownloadListener;
import android.widget.*;
import com.google.appinventor.components.runtime.OnPauseListener;
import com.google.appinventor.components.runtime.OnResumeListener;

import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.runtime.*;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import com.google.appinventor.components.runtime.util.*;

import java.io.*;
import java.io.File;
import java.util.*;

import static java.lang.System.currentTimeMillis;


public class WebViewExtra extends AndroidNonvisibleComponent implements Component, ActivityResultListener, OnPauseListener, OnResumeListener {

  private final ComponentContainer container;
  private final Activity activity;
  private final Context context;

  private ValueCallback<Uri[]> mFilePathCallback;
  private static final int RESULT_OK = 1;
  protected int requestCode;
  private WebView mWebView;
  private WebSettings settings;
  public CookieManager cookieManager;
  private String nameFile = "";
  private String filePath = "";
  private String pkgName = "";
  private String urlFile = "";
  BroadcastReceiver broadcastReceiver = null;
  private int downloadDirectory = 1;
  private boolean uploadDirectory = false;
  private String[] asdFiles;
  private String asdPath;
  boolean transparentbg = false;
  private JavaScriptInterface jsInterface;
  private String blobMimeType = "";


  public WebViewExtra(ComponentContainer container) {
    super(container.$form());
    this.container = container;
    context = (Context) container.$context();
    activity = (Activity) container.$context();
    cookieManager = CookieManager.getInstance();

    broadcastReceiver = new BroadcastReceiver() {
      public void onReceive(Context context, Intent intent) {
        Downloaded(urlFile, nameFile);
      }
    };

    form.registerForOnPause(this);
    form.registerForOnResume(this);
  }

  @Override
  public void onResume() {
    if (Build.VERSION.SDK_INT >= 26) {
      activity.registerReceiver(broadcastReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), 2);
    } else {
      activity.registerReceiver(broadcastReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }
  }

  @Override
  public void onPause() {
    try {
      activity.unregisterReceiver(broadcastReceiver);
    } catch(Throwable ignored) {
    }
  }

//TRANSBACK//

  @DesignerProperty(editorType = "boolean", defaultValue = "False")
  @SimpleProperty(description = "make the background of the webview transparent", userVisible = false)
  public void TransparentBackground(boolean transp) {
    transparentbg = transp;
  }

  //FUNCTIONS//
  @SuppressLint("SetJavaScriptEnabled")
  @SimpleFunction(description = "Sets the Webviewer to use, attach the component block")
  public void SetWebviewer(WebViewer webViewer) {

    mWebView = (WebView) webViewer.getView();
    settings = mWebView.getSettings();
    cookieManager.setAcceptThirdPartyCookies(mWebView, true);
    settings.setJavaScriptEnabled(true);
    settings.setJavaScriptCanOpenWindowsAutomatically(true);
    settings.setLoadWithOverviewMode(true);
    settings.setUseWideViewPort(true);
    settings.setAllowFileAccess(true);
    settings.setDomStorageEnabled(true);
    settings.setBuiltInZoomControls(false);
    settings.setDisplayZoomControls(false);
    settings.setSupportZoom(false);
    settings.setMediaPlaybackRequiresUserGesture(false);
    String ua = settings.getUserAgentString();
    settings.setUserAgentString(ua.replace("; wv", "").replace("Mobile ", "").replace("Version/4.0", ""));
    jsInterface = new JavaScriptInterface(context.getApplicationContext());
    mWebView.addJavascriptInterface(jsInterface, "Android");
    mWebView.setWebChromeClient(new ChromeClient());

    if (transparentbg) {
      mWebView.setBackgroundColor(0);
      Drawable background = mWebView.getBackground();
      if (background != null) {
        background.setAlpha(0);
      }
    }

    mWebView.setDownloadListener(new DownloadListener() {
      @Override
      public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {

        if (url.startsWith("blob")) {
          blobMimeType = mimeType;
          //https://techblogs.42gears.com/download-the-file-with-blob-url-in-android-webview/
          mWebView.loadUrl(jsInterface.GetBase64StringFromBlobUrl(url, blobMimeType));

        } else {

          DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
          req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
          String cookies = CookieManager.getInstance().getCookie(url);
          req.addRequestHeader("cookie", cookies);
          urlFile = url;
          if (contentDisposition.contains("filename=")) {
            nameFile = contentDisposition.split("filename=\"")[1].split("\"")[0];
          } else {
            nameFile = URLUtil.guessFileName(url, contentDisposition, null);
          }

          if (downloadDirectory == 1) {
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nameFile);
          } else if (downloadDirectory == 2) {
              req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOCUMENTS, nameFile);
            } else if (downloadDirectory == 3) {
              req.setDestinationInExternalFilesDir(context, "/", nameFile);
              //https://stackoverflow.com/a/26621853/2030549
            }

          if (downloadDirectory == 1) {
            filePath = "/storage/emulated/0/Download/" + nameFile;
          } else if (downloadDirectory == 2) {
            filePath = "/storage/emulated/0/Documents/" + nameFile;
          } else if (downloadDirectory == 3) {
            pkgName = context.getPackageName();
            filePath = "/storage/emulated/0/Android/data/" + pkgName + "/files/" + nameFile;
          }

          DownloadDataDebug(url, userAgent, contentDisposition, mimeType, nameFile, filePath);
          DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
          dm.enqueue(req);
          Toast.makeText(context.getApplicationContext(), "Downloading....", Toast.LENGTH_SHORT).show();
        }
      }
    });
  }


  public class ChromeClient extends WebChromeClient {
    public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePath, WebChromeClient.FileChooserParams fileChooserParams) {

      if (mFilePathCallback != null) {
        mFilePathCallback.onReceiveValue(null);
      }
      mFilePathCallback = filePath;

      click();
      return true;
    }
  }

  public void click() {
    BeforePicking(fileType);
    if (requestCode == 0) {
      requestCode = container.$form().registerForActivityResult(this);
    }
    if (uploadDirectory == false) {
      container.$context().startActivityForResult(getIntent(), requestCode);
    } else {
      container.$context().startActivityForResult(getIntentLPI(), requestCode);
    }
  }

  protected Intent getIntentLPI() {
    Intent lpi = new Intent();
    lpi.setClassName(container.$context(), "com.google.appinventor.components.runtime.ListPickerActivity");
    lpi.putExtra("com.google.appinventor.components.runtime.ListPickerActivity.list", asdFiles);
    return lpi;
  }

  protected Intent getIntent() {
    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType(fileType);
    return Intent.createChooser(i, "File Chooser");
  }

  public void resultReturned(int requestCode, int resultCode, Intent data) {
    Uri[] results = null;
    String dataString;

    if (resultCode == Activity.RESULT_OK) {
      if (uploadDirectory) {
        Uri uri = Uri.parse(asdPath + data.getStringExtra("com.google.appinventor.components.runtime.ListPickerActivity.selection"));
        dataString = String.valueOf(uri);
      } else {
        dataString = data.getDataString();
      }

      if (dataString != null) {
        results = new Uri[]{Uri.parse(dataString)};
        AfterPicking(getFileName(Uri.parse(dataString)));
      }
    }
    mFilePathCallback.onReceiveValue(results);
    mFilePathCallback = null;
  }

  public String getFileName(Uri uri) {
    String result = null;
    if (uri.getScheme().equals("content")) {
      try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
        if (cursor != null && cursor.moveToFirst()) {
          result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
        }
      }
    }
    if (result == null) {
      result = uri.getPath();
      int cut = result.lastIndexOf('/');
      if (cut != -1) {
        result = result.substring(cut + 1);
      }
    }
    return result;
  }


  @SimpleFunction(description = "Returns file type for picking files for upload. Will only apply if selecting from Shared Directories.")
  public void FileType(String filetype) {
    if (!filetype.equals("*/*")) {
      fileType = filetype;
    }
    BeforePicking(fileType);
  }

  @SimpleFunction(description = "Set true or false for ZoomControls in the webviewer - false by default.")
  public void ZoomControls(boolean enabled) {
    settings.setBuiltInZoomControls(enabled);
    //settings.setDisplayZoomControls(enabled);
    settings.setSupportZoom(enabled);
  }

  @SimpleFunction (description="Zoom In on the webviewer")
  public void ZoomIn() {
    mWebView.zoomIn();
  }

  @SimpleFunction (description="Zoom Out on the webviewer")
  public void ZoomOut() {
    mWebView.zoomOut();
  }

  @SimpleFunction(description = "Set true or false for WideViewPort in the webviewer, that is, enable support for the viewport HTML meta tag or should use a wide viewport - true by default.")
  public void WideViewportMode(boolean enabled) {
    settings.setUseWideViewPort(enabled);
  }

  @SimpleFunction(description = "Set true or false for Media Playback Gestures in the webviewer, 'false' by default, e.g. no gesture required")
  public void MediaPlaybackGesture(boolean enabled) {
    settings.setMediaPlaybackRequiresUserGesture(enabled);
  }

  @SimpleFunction(description = "Set true or false for LoadWithOverview in the webviewer, that is, zooms out the content to fit on screen by width - true by default.")
  public void LoadWithOverviewMode(boolean enabled) {
    settings.setLoadWithOverviewMode(enabled);
  }

  @SimpleFunction(description = "Sets the desired directory for downloaded files. Set 1 for DOWNLOAD. Set 2 for DOCUMENTS. Set 3 for ASD. Default = 1, so " +
          "this block not required if happy with DOWNLOAD " +
          "directory.")
  public void DownloadTo(int dir) {
    downloadDirectory = dir;
  }

  @SimpleFunction(description = "Set true to upload from ASD, or false to use Shared directories.  - false by default.")
  public void UploadFromASD(boolean enabled) {
    uploadDirectory = enabled;
  }

  @SimpleFunction(description = "return all files in ASD")
  public void GetAsdFiles(YailList fileNames, String pathToASD) {
    asdPath = pathToASD;
    asdFiles = fileNames.toStringArray();
    String stringAsdFiles = toString(asdFiles);
    GotASDFiles(stringAsdFiles);
  }

  public static String toString(Object[] a) {
    if (a == null)
      return "null";

    int iMax = a.length - 1;
    if (iMax == -1)
      return "[]";

    StringBuilder b = new StringBuilder();
    b.append('[');
    for (int i = 0; ; i++) {
      b.append(String.valueOf(a[i]));
      if (i == iMax)
        return b.append(']').toString();
      b.append(", ");
    }
  }

//FUNCTIONS END//

//PROPERTIES//




  @SimpleProperty(description = "Get the current setting for UserAgent in the webviewer")
  public String GetUserAgentString() {
    return settings.getUserAgentString();
  }

  @SimpleProperty(description = "Get the current setting for Zoom Controls in the webviewer")
  public boolean ZoomControls() {
    return settings.getDisplayZoomControls();
  }

  List<String> fileTypes = Arrays.asList("*/*", "image/*", "audio/*", "video/*", "application/pdf", "image/png", "image/jpg", "text/*", "text/csv");

  @SimpleProperty(description = "List of file types to select from")
  public List<String> FileTypes() {
    return fileTypes;
  }

  String fileType = "*/*";

//PROPERTIES END//

//EVENTS//

  @SimpleEvent(description = "Do something before picking a file for upload, and returns filetype chosen for selecting file for upload")
  public void BeforePicking(String fileTypeChosen) {
    EventDispatcher.dispatchEvent(this, "BeforePicking", fileTypeChosen);
  }

  @SimpleEvent(description = "Do something after selecting a file for upload, and returns filename AFTER selecting for upload")
  public void AfterPicking(String nameFile) {
    EventDispatcher.dispatchEvent(this, "AfterPicking", nameFile);
  }

  @SimpleEvent(description = "Returns download data, intended for developer use, this runs once the download is initiated, NOT when it is completed")
  public void DownloadDataDebug(String url, String userAgent, String contentDisposition, String mimeType, String filename, String filepath) {
    EventDispatcher.dispatchEvent(this, "DownloadDataDebug", url, userAgent, contentDisposition, mimeType, filename, filepath);
  }

  @SimpleEvent(description = "Returns url and filename AFTER download. This event ONLY works with the compiled app. In development (companion) use the " +
          "DownloadDataDebug event instead, for testing.")
  public void Downloaded(String url, String filename) {
    EventDispatcher.dispatchEvent(this, "Downloaded", url, filename);
  }

  @SimpleEvent(description = "got Files in ASD")
  public void GotASDFiles(String files) {
    EventDispatcher.dispatchEvent(this, "GotASDFiles", files);
  }

  //EVENTS END//

  //##############################################
  //# JAVASCRIPT INTERFACE AND BLOB URL DOWNLOAD #
  //##############################################

  public class JavaScriptInterface {
    private final Context context;

    public JavaScriptInterface(Context context) {
      this.context = context;
    }

    @android.webkit.JavascriptInterface
    public void getBase64FromBlobData(String base64Data) throws IOException, InterruptedException {
      Toast.makeText(context.getApplicationContext(), "got base64 from blob", Toast.LENGTH_SHORT).show();
      activity.runOnUiThread(new Runnable() {
        public void run() {
          try {
            convertBase64StringToFileAndStoreIt(base64Data, blobMimeType);
          } catch (IOException e) {
            throw new RuntimeException(e);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        }
      });
    }

    public String GetBase64StringFromBlobUrl(String blobUrl, String mimeType) {
      return "javascript: var xhr = new XMLHttpRequest();" +
              "xhr.open('GET', '" + blobUrl + "', true);" +
              "xhr.setRequestHeader('Content-type','" + mimeType + ";charset=UTF-8');" +
              "xhr.responseType = 'blob';" +
              "xhr.onload = function(e) {" +
              "    if (this.status == 200) {" +
              "        var blobFile = this.response;" +
              "        var reader = new FileReader();" +
              "        reader.readAsDataURL(blobFile);" +
              "        reader.onloadend = function() {" +
              "            base64data = reader.result;" +
              "            window.Android.getBase64FromBlobData(base64data);" +
              "        }" +
              "    }" +
              "};" +
              "xhr.send();";
    }

    public void convertBase64StringToFileAndStoreIt(String base64String, String mimeType) throws IOException, InterruptedException {
      BufferedOutputStream output = null;
      String newTime = String.valueOf(currentTimeMillis());
      MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
      String extension = mimeTypeMap.getExtensionFromMimeType(mimeType);
      String filenameBlob = "file_" + newTime + "." + extension;
      final java.io.File dwldsPath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + filenameBlob);
      String source = base64String.split(",")[1];
      byte[] fileAsBytes = Base64.decode(source, 0);
      FileOutputStream os = new FileOutputStream(dwldsPath, false);
      os.write(fileAsBytes);
      os.flush();

      Toast.makeText(context.getApplicationContext(), "Downloading blob to file ...", Toast.LENGTH_SHORT).show();
      Downloaded(urlFile, "/storage/emulated/0/Download/" + filenameBlob);

    }

  }

}