package androidx.media3.mpvplayer.video;
public final class MpvVideoEqualizer {
    public static final MpvVideoEqualizer DEFAULT = new MpvVideoEqualizer();
    public static MpvVideoEqualizer create(float brightness, float contrast, float saturation, float gamma, float hue, float sharpness) { return new MpvVideoEqualizer(); }
}