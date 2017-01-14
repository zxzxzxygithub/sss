package co.allconnected.lib.stat;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.flurry.android.FlurryAgent;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.umeng.analytics.MobclickAgent;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class StatAgent {

    private static final String TAG = "EVENT";
    private static final boolean DEBUG = false;

    private static Context mContext;


    private static String mFlurryApiKey;
    private static JSONObject mOnlineConfig;
    private static JSONObject mExtraConfig;



    static public void init(Context context) {
        mContext = context;
    }


    static public String getConfigParams(String name) {
        if (mExtraConfig != null && mExtraConfig.has(name)) {
            return mExtraConfig.optString(name, "");
        }

        if (mOnlineConfig != null && mOnlineConfig.has(name)) {
            return mOnlineConfig.optString(name);
        }

        try {
            return MobclickAgent.getConfigParams(mContext, name);
        } catch (Throwable e) {
            e.printStackTrace();
            return "";
        }
    }

    static public void updateOnlineConfig() {
        if (mContext != null) MobclickAgent.updateOnlineConfig(mContext);
    }

    static public void setOnlineConfig(JSONObject config) {
        mOnlineConfig = config;
    }

    static public void setExtraConfig(JSONObject config) {
        mExtraConfig = config;
    }

    static public JSONObject getOnlineJson(String name, boolean includeOfflineJson) {
        if (name == null)
            return mOnlineConfig;

        JSONObject json = getOnlineJsonFromFirebase(name);
        if (json != null)
            return json;

        json = getOnlineJsonFromUmeng(name);
        if (json != null)
            return json;

        json = getOnlineJsonFromCache(name);
        if (json != null)
            return json;

        return includeOfflineJson ? getJsonFromAssets(name) : null;
    }

    static public JSONObject getOnlineJson(String name) {
        return getOnlineJson(name, true);
    }

    static private JSONObject getOnlineJsonFromFirebase(String name) {
        if (mOnlineConfig == null)
            return null;

        String shortName = name.split("\\.")[0];
        if (mExtraConfig != null && mExtraConfig.has(name)) {
            return mExtraConfig.optJSONObject(name);
        }

        if (mExtraConfig != null && mExtraConfig.has(shortName)) {
            return mExtraConfig.optJSONObject(shortName);
        }

        if (mOnlineConfig.has(name)) {
            if (DEBUG) Log.i(TAG, "getOnlineJson:" + name);
            return mOnlineConfig.optJSONObject(name);
        }

        if (mOnlineConfig.has(shortName)) {
            if (DEBUG) Log.i(TAG, "getOnlineJson:" + name);
            return mOnlineConfig.optJSONObject(shortName);
        }

        return null;
    }

    static private JSONObject getOnlineJsonFromUmeng(String name) {
        try {
            String text = MobclickAgent.getConfigParams(mContext, name);
            if (TextUtils.isEmpty(text)) {
                if (DEBUG) Log.w(TAG, "getOnlineJsonFromUmeng:" + name + " failed");
                return null;
            }
            if (DEBUG) Log.w(TAG, "getOnlineJsonFromUmeng:" + name);
            return new JSONObject(text);
        } catch (Throwable e) {
            e.printStackTrace();
            if (DEBUG) Log.w(TAG, "getOnlineJsonFromUmeng:" + name + " failed");
            return null;
        }
    }

    static private JSONObject getOnlineJsonFromCache(String name) {
        try {
            String filename = mContext.getFilesDir().getAbsolutePath() + "/onlinejson/" + name;
            if (DEBUG) Log.w(TAG, "getOnlineJson from cache:" + name);
            return new JSONObject(readFile(filename, "UTF-8"));
        } catch (Throwable e) {
            if (DEBUG) Log.w(TAG, "getOnlineJson from cache:" + name + " failed");
            return null;
        }
    }

    static private JSONObject getJsonFromAssets(String name) {
        try {
            return new JSONObject(readString(mContext.getAssets().open(name)));
        } catch (Throwable e) {
            if (DEBUG) Log.w(TAG, "getJsonFromAssets:" + e.getMessage());
            return null;
        }
    }


    static public void setFlurryApiKey(String key) {
        mFlurryApiKey = key;
        FlurryAgent.setLogEnabled(false);
        FlurryAgent.init(mContext, key);
    }

    static public void onResume(Activity context) {
        MobclickAgent.onResume(context);
    }


    static public void onPause(Activity context) {
        MobclickAgent.onPause(context);
    }

    static public void onEvent(Context context, String eventId) {
        MobclickAgent.onEvent(context, eventId);
        onGaEvent(eventId);
        onFlurryEvent(eventId, "");

        if (DEBUG) Log.i(TAG, eventId);
    }

    static public void onEvent(Context context, String eventId, String params) {
        MobclickAgent.onEvent(context, eventId, params);
        Map<String, String> map = new HashMap<>();
        map.put("param", params);
        onGaEvent(eventId, map);
        onFlurryEvent(eventId, params);

        if (DEBUG) Log.i(TAG, eventId + ":" + params);
    }

    static public void onEvent(Context context, String eventId, String key, String value) {
        Map<String, String> params = new HashMap<>();
        params.put(key, value);
        onEvent(context, eventId, params);
    }

    static public void onEvent(Context context, String eventId, Map<String, String> params) {
        MobclickAgent.onEvent(context, eventId, params);
        onGaEvent(eventId, params);
        onFlurryEvent(eventId, params);

        printEventDetails(eventId, 0, params);
    }

    static public void onEventValue(Context context, String eventId, Map<String, String> params, int value) {
        MobclickAgent.onEventValue(context, eventId, params, value);
        onGaEvent(eventId, params);
        onFlurryEvent(eventId, params);

        printEventDetails(eventId, value, params);
    }

    static public void onFlurryEvent(String eventId, String params) {
        if (TextUtils.isEmpty(mFlurryApiKey))
            return;

        if (TextUtils.isEmpty(params)) {
            FlurryAgent.logEvent(eventId);
        } else {
            Map<String, String> map = new HashMap<>();
            map.put("param", params);
            FlurryAgent.logEvent(eventId, map);
        }
    }

    static public void onFlurryEvent(String eventId, Map<String, String> params) {
        if (TextUtils.isEmpty(mFlurryApiKey))
            return;
        FlurryAgent.logEvent(eventId, params);
    }

    public static void sendScreenEvent(String activityFullName) {
//        Tracker tracker = getTracker(TrackerName.APP_TRACKER);
//        if (tracker == null) return;
//        tracker.setScreenName(activityFullName);
//        tracker.send(new HitBuilders.AppViewBuilder().build());
    }

    private static void onGaEvent(String action) {
        onGaEvent(action, null);
    }

    private static void onGaEvent(String action, Map<String, String> params) {
        Bundle bundle = new Bundle();
        if (params != null && params.size() > 0) {
            Set<String> keys = params.keySet();
            for (String key : keys) {
                bundle.putString(key, params.get(key));
            }
        }
        //去掉stat_1_0_0_这种开头
        action = cutPrefix(action);
        // 如果仍然超过40个字符串则去掉下划线
        action = cutUnderlineIfExceed40(action);

        FirebaseAnalytics.getInstance(mContext).logEvent("f" + action, bundle);

    }

    /**
     * 去掉stat_1_0_0_这种开头
     * @author michael
     * @time 17/1/14 上午11:09
     */
    private static String cutPrefix(String action) {
        return action.replaceAll("(stat)?(_\\d){3}", "");
    }


    /**
     * 如果仍然超过40个字符串则去掉下划线
     *
     * @author michael
     * @time 17/1/14 上午11:08
     */
    private static String cutUnderlineIfExceed40(String action) {
        if (action.length() >= 40) {
            action = action.replaceAll("_", "");
        }
        return action;
    }


    private static void printEventDetails(String eventId, int duration, Map<String, String> params) {
        if (DEBUG) {
            Log.i(TAG, eventId + (duration == 0 ? "" : (":" + duration)));
            if (params != null && params.size() > 0) {
                for (Map.Entry<String, String> e : params.entrySet())
                    Log.i(TAG, "  " + e.getKey() + "=" + e.getValue());
            }
        }
    }

    private static String readString(InputStream is) throws IOException {

        InputStreamReader reader = new InputStreamReader(is, "UTF-8");
        StringBuilder out = new StringBuilder();

        try {
            int len = 0;
            char buffer[] = new char[8 * 1024];

            while ((len = reader.read(buffer, 0, buffer.length)) > 0) {
                out.append(buffer, 0, len);
            }
        } finally {
            reader.close();
        }

        return out.toString();
    }

    /**
     * 将文件全部读出并转为指定编码的字符串返回。当编码参数为null时，使用系统默认编码。
     */
    private static String readFile(String filename, String charset) throws IOException {
        if (charset != null && charset.length() > 0)
            return new String(readFile(filename), charset);
        else
            return new String(readFile(filename), Charset.defaultCharset().toString());
    }

    /**
     * 将文件全部读到byte数组中
     */
    private static byte[] readFile(String filename) throws IOException {
        FileInputStream stream = null;

        try {
            stream = new FileInputStream(new File(filename));
            FileChannel fc = stream.getChannel();
            MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            byte[] data = new byte[(int) fc.size()];
            bb.get(data);
            return data;
        } finally {
            if (stream != null) stream.close();
        }
    }
}
