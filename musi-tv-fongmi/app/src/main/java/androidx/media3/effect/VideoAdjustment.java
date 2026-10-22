package androidx.media3.effect;
public final class VideoAdjustment {
    public static final class Builder {
        public Builder setSaturation(float v) { return this; }
        public Builder setContrast(float v) { return this; }
        public Builder setBrightness(float v) { return this; }
        public Builder setGamma(float v) { return this; }
        public Builder setHue(float v) { return this; }
        public Builder setRedGain(float v) { return this; }
        public Builder setBlueGain(float v) { return this; }
        public Builder setSharpness(float v) { return this; }
        public Builder setThreshold(float v) { return this; }
        public Builder setShadowLift(float v) { return this; }
        public VideoAdjustment build() { return new VideoAdjustment(); }
    }
}