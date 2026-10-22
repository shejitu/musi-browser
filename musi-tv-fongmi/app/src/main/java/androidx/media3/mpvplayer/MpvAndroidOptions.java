package androidx.media3.mpvplayer;
import java.io.File;
public final class MpvAndroidOptions {
    public static final class Builder {
        public Builder setShaderCacheDirectory(File dir) { return this; }
        public Builder setAudioPassthroughEnabled(boolean v) { return this; }
        public Builder setDolbyVisionOutputPolicy(int v) { return this; }
        public Builder setVulkanEnabled(boolean v) { return this; }
        public Builder setGpuNextEnabled(boolean v) { return this; }
        public MpvAndroidOptions build() { return new MpvAndroidOptions(); }
    }
}