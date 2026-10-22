package androidx.media3.mpvplayer;
public final class MpvSubtitleOptions {
    public static final class Builder {
        public Builder setPosition(double v) { return this; }
        public Builder setScale(double v) { return this; }
        public Builder setSecondarySubtitle(int id, double pos, boolean forced) { return this; }
        public Builder setCustomStyle(int textColor, int bgColor, int edgeType, int edgeColor, float edgeWidth, float shadow) { return this; }
        public Builder setSystemCaptionStyle() { return this; }
        public MpvSubtitleOptions build() { return new MpvSubtitleOptions(); }
    }
}