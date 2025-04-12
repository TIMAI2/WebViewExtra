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
import android.provider.*;
import android.util.*;
import android.webkit.*;
import android.webkit.DownloadListener;
import android.widget.*;
import androidx.core.content.*;
import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.runtime.*;
import android.net.Uri;
import android.database.Cursor;
import com.google.appinventor.components.runtime.util.*;
import kawa.lib.*;

import java.io.*;
import java.io.File;
import java.util.*;
import java.util.Base64;


public class WebViewExtra extends AndroidNonvisibleComponent implements Component, ActivityResultListener {
  private final ComponentContainer container;
  private final Activity activity;
  private final Context context;

  private JavaScriptInterface jsInterface;

  private ValueCallback<Uri[]> mFilePathCallback;
  private static final int RESULT_OK = 1;
  protected int requestCode;
  private WebSettings settings;
  public CookieManager cookieManager;
  private String nameFile = "";
  private String urlFile = "";
  BroadcastReceiver broadcastReceiver = null;
  private boolean downloadDirectory = false;
  private boolean uploadDirectory = false;
  private String[] asdFiles;
  private String asdPath;
  boolean transparentbg = false;


  public WebViewExtra(ComponentContainer container) {
    super(container.$form());
    this.container = container;
    context = (Context) container.$context();
    activity = (Activity) container.$context();
    cookieManager = CookieManager.getInstance();
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
    broadcastReceiver = new BroadcastReceiver() {
      public void onReceive(Context context, Intent intent) {
        Downloaded(urlFile, nameFile);
      }
    };

    activity.registerReceiver(broadcastReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

    WebView mWebView = (WebView) webViewer.getView();
    settings = mWebView.getSettings();
    cookieManager.setAcceptThirdPartyCookies(mWebView, true);
    settings.setJavaScriptEnabled(true);
    settings.setJavaScriptCanOpenWindowsAutomatically(true);
    settings.setLoadWithOverviewMode(true);
    settings.setUseWideViewPort(true);
    settings.setAllowFileAccess(true);
    settings.setDomStorageEnabled(true);
    settings.setBuiltInZoomControls(true);
    settings.setDisplayZoomControls(true);
    settings.setSupportZoom(true);
    String ua = settings.getUserAgentString();
    settings.setUserAgentString(ua.replace("; wv", "").replace("Mobile ", "").replace("Version/4.0", ""));
    settings.setDefaultTextEncodingName("utf-8");
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

        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        String cookies = CookieManager.getInstance().getCookie(url);
        req.addRequestHeader("cookie", cookies);
        urlFile = url;
        Log.d("url= ", urlFile);
        Log.d("contdisp= ", contentDisposition);
//        if (contentDisposition.contains("filename=")) {
//          nameFile = contentDisposition.split("filename=\"")[1].split("\"")[0];
//        } else {
          nameFile = URLUtil.guessFileName(url, contentDisposition, null);
//        }
        Log.d("nameFile= ", nameFile);
        if (downloadDirectory == true) {
          req.setDestinationInExternalFilesDir(context, "/", nameFile);
          //https://stackoverflow.com/a/26621853/2030549
        } else {
          req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nameFile);
        }

        DownloadDataDebug(url, userAgent, contentDisposition, mimeType, nameFile);
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

        activity.registerReceiver(broadcastReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        broadcastReceiver = new BroadcastReceiver() {
          public void onReceive(Context context, Intent intent) {
            Downloaded(urlFile, nameFile);
          }
        };

        dm.enqueue(req);
        Toast.makeText(context.getApplicationContext(), "Downloading....", Toast.LENGTH_SHORT).show();
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
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType(fileType);
    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
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
        String selectedUri = String.valueOf(results[0]);
        AfterPicking(getFileName(Uri.parse(dataString)), selectedUri);
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

  @SimpleFunction(description = "Set true or false for ZoomControls in the webviewer - true by default.")
  public void ZoomControls(boolean enabled) {
    settings.setBuiltInZoomControls(enabled);
    settings.setDisplayZoomControls(enabled);
    settings.setSupportZoom(enabled);
  }

  @SimpleFunction(description = "Deletes original file after uploading it to ASD - to be replaced with owned file")
  public void DeleteFile(String safUri) throws SecurityException, FileNotFoundException {
    try {
      DocumentsContract.deleteDocument(context.getContentResolver(), Uri.parse(safUri));
      Toast.makeText(context, "File Deleted", Toast.LENGTH_SHORT).show();
    }
    catch(Exception e) {
      e.printStackTrace();
    }
  }

  @SimpleFunction(description = "Set true or false for WideViewPort in the webviewer, that is, enable support for the viewport HTML meta tag or should use a wide viewport - true by default.")
  public void WideViewportMode(boolean enabled) {
    settings.setUseWideViewPort(enabled);
  }

  @SimpleFunction(description = "Set true or false for LoadWithOverview in the webviewer, that is, zooms out the content to fit on screen by width - true by default.")
  public void LoadWithOverviewMode(boolean enabled) {
    settings.setLoadWithOverviewMode(enabled);
  }

  @SimpleFunction(description = "Set true to download to ASD, or false to use Download directory.  - false by default.")
  public void DownloadToASD(boolean enabled) {
    downloadDirectory = enabled;
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
  public void AfterPicking(String nameFile, String results) {
    EventDispatcher.dispatchEvent(this, "AfterPicking", nameFile, results);
  }

  @SimpleEvent(description = "Returns download data, intended for developer use")
  public void DownloadDataDebug(String url, String userAgent, String contentDisposition, String mimeType, String filename) {
    EventDispatcher.dispatchEvent(this, "DownloadDataDebug", url, userAgent, contentDisposition, mimeType, filename);
  }

  @SimpleEvent(description = "Returns url and filename AFTER download")
  public void Downloaded(String url, String filename) {
    EventDispatcher.dispatchEvent(this, "Downloaded", url, filename);
  }

  @SimpleEvent(description = "got Files in ASD")
  public void GotASDFiles(String files) {
    EventDispatcher.dispatchEvent(this, "GotASDFiles", files);
  }

  //EVENTS END//

  //JSI FUNCTION

  @SimpleFunction
  public void GetBase64FromBlobData(String base64Data, String filename) throws IOException {
    jsInterface.GetBase64FromBlobData(base64Data, filename);
  }


  //##############################################
  //# JAVASCRIPT INTERFACE AND BLOB URL DOWNLOAD #
  //##############################################

  public class JavaScriptInterface {
    private String fileMimeType;
    private final Context context;

    public JavaScriptInterface(Context context) {
      this.context = context;
    }

    @JavascriptInterface
    public void GetBase64FromBlobData(String base64Data, String filename) throws IOException {
      ConvertBase64StringToFileAndStoreIt(base64Data, filename);
    }

    public String GetBase64StringFromBlobUrl(String blobUrl, String mimeType) {
      if (blobUrl.startsWith("blob")) {
        fileMimeType = mimeType;
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
                "            Android.getBase64FromBlobData(base64data);" +
                "        }" +
                "    }" +
                "};" +
                "xhr.send();";
      }
      return "javascript: console.log('It is not a Blob URL');";
    }

    private void ConvertBase64StringToFileAndStoreIt(String base64String, String filename) throws IOException {
      final int notificationId = 1;
      String extension = filename.substring(filename.lastIndexOf('.'));
      final File dwldsPath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + filename);
      byte[] fileAsBytes;
      fileAsBytes = Base64.getDecoder().decode(base64String);
      try {
        FileOutputStream os = new FileOutputStream(dwldsPath);
        os.write(fileAsBytes);
        os.flush();
        os.close();
        Toast.makeText(context, "FILE DOWNLOADED!", Toast.LENGTH_SHORT).show();
      } catch (Exception e) {
        Toast.makeText(context, "FAILED TO DOWNLOAD THE FILE!", Toast.LENGTH_SHORT).show();
        e.printStackTrace();
      }
//      if (dwldsPath.exists()) {
//        Intent intent = new Intent();
//        intent.setAction(Intent.ACTION_VIEW);
//        Uri apkURI = FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".provider", dwldsPath);
//        intent.setDataAndType(apkURI, MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension));
//        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//        PendingIntent pendingIntent = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE);
//        String CHANNEL_ID = "MYCHANNEL";
//        final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
//        NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, "name", NotificationManager.IMPORTANCE_LOW);
//        Notification notification = new Notification.Builder(context, CHANNEL_ID)
//                .setContentText("You have got something new!")
//                .setContentTitle("File downloaded")
//                .setContentIntent(pendingIntent)
//                .setChannelId(CHANNEL_ID)
//                .setSmallIcon(android.R.drawable.stat_sys_download_done)
//                .build();
//        if (notificationManager != null) {
//          notificationManager.createNotificationChannel(notificationChannel);
//          notificationManager.notify(notificationId, notification);
//        }
//      }

    }
  }
}