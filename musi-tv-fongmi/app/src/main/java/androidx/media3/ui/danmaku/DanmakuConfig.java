package androidx.media3.ui.danmaku;
public final class DanmakuConfig {
    public static final int COLOR_MODE_DEFAULT = 0, COLOR_MODE_COLORFUL = 1, COLOR_MODE_GRADIENT = 2;
    public static final int STYLE_NONE = 0, STYLE_PROJECTION = 1, STYLE_SHADOW = 2, STYLE_STROKE = 3;
    public static final DanmakuConfig DEFAULT = new DanmakuConfig();
    public final float textScale = 1f, transparency = 1f, strokeWidthMultiplier = 1f, projectionTransparency = 1f, shadowTransparency = 1f;
    public final boolean textBold = false;
    public final int styleMode = 0, colorMode = 0;
    public final float projectionOffsetXMultiplier = 0f, projectionOffsetYMultiplier = 0f;
    public final long durationMs = 0, fixedDurationMs = 0, timeOffsetMs = 0;
    public final int maxOnScreen = 0;
    public final float scrollAreaRatio = 0f, scrollGapRatio = 0f, lineSpacing = 0f;
    public final int maxScrollLines = 0, maxTopLines = 0, maxBottomLines = 0;
    public final boolean showScroll = true, showTop = true, showBottom = true, showReverse = true, showPositioned = true, showSubtitle = true, showSpecial = true;
}