# Add any ProGuard configurations specific to this
# extension here.

-keep public class uk.co.metricrat.webviewextra.WebViewExtra {
    public *;
 }
-keeppackagenames gnu.kawa**, gnu.expr**

-optimizationpasses 4
-allowaccessmodification
-mergeinterfacesaggressively

-repackageclasses 'uk/co/metricrat/webviewextra/repack'
-flattenpackagehierarchy
-dontpreverify
