package androidx.media3.mpvplayer;
import java.io.File;
public final class MpvPlayerConfig {
    public static final class Builder {
        public Builder addConfigDirectory(File dir) { return this; }
        public Builder addAndroidFontConfig(File dir, File cache) { return this; }
        public Builder addAndroidDefaults(androidx.media3.mpvplayer.MpvAndroidOptions options) { return this; }
        public Builder addTlsCaFileFromAsset(android.content.Context c, String asset, File target) { return this; }
        public Builder addAndroidSubtitleOptions(android.content.Context c, MpvSubtitleOptions options) { return this; }
        public Builder addDiskCacheOptions(File dir, int seconds) { return this; }
        public MpvPlayerConfig build() { return new MpvPlayerConfig(); }
    }
}