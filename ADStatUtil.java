package co.allconnected.lib.stat;

import android.content.Context;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 专门用于广告的统计
 *
 * @author michael
 * @time 17/1/11 下午2:00
 */
public class ADStatUtil {

    public static final String STEP_THEORY_LOAD = "_ad_theory_load_";
    public static final String STEP_LOAD = "_ad_load_";
    public static final String STEP_LOAD_SUCCEEDED = "_ad_load_succeeded";
    public static final String STEP_LOAD_FAILED = "_ad_load_failed";
    public static final String STEP_THEORY_SHOW = "_ad_theory_show_";
    public static final String STEP_SHOW = "_ad_show_";
    public static final String STEP_CLICK = "_ad_click_";

    /**
     * 广告统计
     *
     * @param extramsg 用于扩展，
     *                 加载失败的时候这个字段传reason
     *                 如果场景是load的时候，这个字段传国家
     *                 由于要按照独立用户统计二级场景，如果当天传过这个值
     *                 下一次传只能传null
     * @author michael
     * @time 17/1/11 下午2:26
     */
    public static void statAd(Context context, String adPositionName, String step, String platformName, boolean isNewUser, String extramsg) {
        Map<String, String> params = new HashMap<>();
        params.put("platform", platformName);
        params.put("newUser", isNewUser + "");
        if (!TextUtils.isEmpty(extramsg)) {
            if (STEP_LOAD_FAILED.equals(step)) {
                params.put("reason", extramsg);
            } else if (!STEP_LOAD.equals(step)) {
                params.put("extramsg", extramsg);
            }
        }
        StatAgent.onEvent(context, adPositionName + step, params);
        if (STEP_LOAD.equals(step) && !TextUtils.isEmpty(extramsg)) {
            params = new HashMap<>();
            params.put("country", extramsg);
            StatAgent.onEvent(context, step, params);
        }
    }


}
