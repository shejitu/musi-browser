package androidx.media3.exoplayer.audio;
import android.content.Context;
import androidx.media3.common.audio.AudioEffectParameters;
import androidx.media3.exoplayer.ExoPlayer;
public final class AudioEqualizerController {
    public AudioEqualizerController(Context context) {}
    public boolean apply(ExoPlayer player, AudioEffectParameters config) { return true; }
    public void release() {}
}