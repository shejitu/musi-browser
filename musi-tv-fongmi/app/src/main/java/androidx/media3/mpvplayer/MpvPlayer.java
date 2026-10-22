package androidx.media3.mpvplayer;
import android.content.Context;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.audio.AudioEffectParameters;
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager;
public final class MpvPlayer extends PlayerBase {
    public static final int VIDEO_EFFECTS_SUPPORTED = 1, VIDEO_EFFECTS_UNSUPPORTED = 0, VIDEO_EFFECTS_UNSUPPORTED_DIRECT_DOLBY_VISION_OUTPUT = 2;
    public static final int AUDIO_EFFECTS_SUPPORTED = 1, AUDIO_EFFECTS_UNSUPPORTED = 0, AUDIO_EFFECTS_UNSUPPORTED_PASSTHROUGH = 2;
    private static final boolean AVAILABLE = false;
    private MpvPlayer(Context c) { super(); }
    public static boolean isAvailable() { return AVAILABLE; }
    public int getVideoEffectsSupport() { return VIDEO_EFFECTS_UNSUPPORTED; }
    public int getAudioEffectsSupport() { return AUDIO_EFFECTS_UNSUPPORTED; }
    public boolean isVideoSharpnessSupported() { return false; }
    public int getAudioChannelCount() { return androidx.media3.common.Format.NO_VALUE; }
    public void setDecode(int decode) {}
    public void setConfig(MpvPlayerConfig config) {}
    public void setSubtitleOptions(MpvSubtitleOptions options) {}
    public void setVideoEqualizer(androidx.media3.mpvplayer.video.MpvVideoEqualizer eq) {}
    public boolean setAudioFilter(androidx.media3.mpvplayer.audio.MpvAudioFilter filter) { return false; }
    public void setAudioOutputListener(Runnable listener) {}
    public void addSubtitle(Object config) {}
    public void setMediaItem(MediaItem item, long positionMs) {}
    public static final class Builder {
        public Builder(Context context) {}
        public Builder setDecode(int decode) { return this; }
        public Builder setConfig(MpvPlayerConfig config) { return this; }
        public MpvPlayer build() { return null; }
    }
}