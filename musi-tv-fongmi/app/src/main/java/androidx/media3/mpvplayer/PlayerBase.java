package androidx.media3.mpvplayer;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.Size;
import java.util.List;

abstract class PlayerBase implements Player {
    public void addListener(Listener l) {}
    public void removeListener(Listener l) {}
    public void prepare() {}
    public void play() {}
    public void pause() {}
    public void stop() {}
    public void release() {}
    public int getPlaybackState() { return STATE_IDLE; }
    public int getPlaybackSuppressionReason() { return PLAYBACK_SUPPRESSION_REASON_NONE; }
    public boolean getPlayWhenReady() { return false; }
    public void setPlayWhenReady(boolean v) {}
    public int getRepeatMode() { return REPEAT_MODE_OFF; }
    public void setRepeatMode(int v) {}
    public boolean getShuffleModeEnabled() { return false; }
    public void setShuffleModeEnabled(boolean v) {}
    public boolean isLoading() { return false; }
    public void seekTo(long ms) {}
    public void seekTo(int index, long ms) {}
    public long getCurrentPosition() { return 0; }
    public long getBufferedPosition() { return 0; }
    public long getDuration() { return C.TIME_UNSET; }
    public long getTotalBufferedDuration() { return 0; }
    public long getCurrentLiveOffset() { return C.TIME_UNSET; }
    public long getContentPosition() { return 0; }
    public long getContentBufferedPosition() { return 0; }
    public long getContentDuration() { return C.TIME_UNSET; }
    public boolean isPlaying() { return false; }
    public PlaybackParameters getPlaybackParameters() { return PlaybackParameters.DEFAULT; }
    public void setPlaybackParameters(PlaybackParameters p) {}
    public void setPlaybackSpeed(float s) {}
    public float getVolume() { return 1f; }
    public void setVolume(float v) {}
    public Timeline getCurrentTimeline() { return Timeline.EMPTY; }
    public MediaItem getCurrentMediaItem() { return null; }
    public int getCurrentMediaItemIndex() { return 0; }
    public int getCurrentPeriodIndex() { return 0; }
    public int getCurrentWindowIndex() { return 0; }
    public int getNextMediaItemIndex() { return 0; }
    public int getPreviousMediaItemIndex() { return 0; }
    public int getNextWindowIndex() { return 0; }
    public int getPreviousWindowIndex() { return 0; }
    public Tracks getCurrentTracks() { return null; }
    public TrackSelectionParameters getTrackSelectionParameters() { return TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT; }
    public void setTrackSelectionParameters(TrackSelectionParameters p) {}
    public MediaMetadata getMediaMetadata() { return MediaMetadata.EMPTY; }
    public MediaMetadata getPlaylistMetadata() { return MediaMetadata.EMPTY; }
    public void setPlaylistMetadata(MediaMetadata m) {}
    public VideoSize getVideoSize() { return VideoSize.UNKNOWN; }
    public CueGroup getCurrentCues() { return new CueGroup(java.util.Collections.emptyList(), 0); }
    public Looper getApplicationLooper() { return Looper.getMainLooper(); }
    public AudioAttributes getAudioAttributes() { return AudioAttributes.DEFAULT; }
    public DeviceInfo getDeviceInfo() { return DeviceInfo.UNKNOWN; }
    public Size getSurfaceSize() { return Size.UNKNOWN; }
    public PlaybackException getPlayerError() { return null; }
    public Object getCurrentManifest() { return null; }
    public Player.Commands getAvailableCommands() { return Player.Commands.EMPTY; }
    public boolean isCommandAvailable(int command) { return false; }
    public boolean canAdvertiseSession() { return false; }
    public boolean isPlayingAd() { return false; }
    public int getCurrentAdGroupIndex() { return -1; }
    public int getCurrentAdIndexInAdGroup() { return -1; }
    public boolean isCurrentMediaItemDynamic() { return false; }
    public boolean isCurrentMediaItemLive() { return false; }
    public boolean isCurrentMediaItemSeekable() { return false; }
    public boolean isCurrentWindowDynamic() { return false; }
    public boolean isCurrentWindowLive() { return false; }
    public boolean isCurrentWindowSeekable() { return false; }
    public int getBufferedPercentage() { return 0; }
    public int getDeviceVolume() { return 0; }
    public boolean isDeviceMuted() { return false; }
    public void setDeviceVolume(int v) {}
    public void setDeviceVolume(int v, int flags) {}
    public void increaseDeviceVolume() {}
    public void increaseDeviceVolume(int flags) {}
    public void decreaseDeviceVolume() {}
    public void decreaseDeviceVolume(int flags) {}
    public void setDeviceMuted(boolean v) {}
    public void setDeviceMuted(boolean v, int flags) {}
    public void mute() {}
    public void unmute() {}
    public long getSeekBackIncrement() { return 0; }
    public long getSeekForwardIncrement() { return 0; }
    public long getMaxSeekToPreviousPosition() { return 0; }
    public void seekBack() {}
    public void seekForward() {}
    public void seekToNext() {}
    public void seekToPrevious() {}
    public void seekToNextMediaItem() {}
    public void seekToPreviousMediaItem() {}
    public void seekToDefaultPosition() {}
    public void seekToDefaultPosition(int index) {}
    public boolean hasPreviousMediaItem() { return false; }
    public boolean hasNextMediaItem() { return false; }
    public void setMediaItem(MediaItem item) {}
    public void setMediaItem(MediaItem item, long positionMs) {}
    public void setMediaItem(MediaItem item, boolean resetPosition) {}
    public void setMediaItems(List<MediaItem> items) {}
    public void setMediaItems(List<MediaItem> items, int index, long positionMs) {}
    public void setMediaItems(List<MediaItem> items, boolean resetPosition) {}
    public void addMediaItem(MediaItem item) {}
    public void addMediaItem(int index, MediaItem item) {}
    public void addMediaItems(List<MediaItem> items) {}
    public void addMediaItems(int index, List<MediaItem> items) {}
    public void moveMediaItem(int from, int to) {}
    public void moveMediaItems(int from, int to, int newFrom) {}
    public void removeMediaItem(int index) {}
    public void removeMediaItems(int from, int to) {}
    public void clearMediaItems() {}
    public void replaceMediaItem(int index, MediaItem item) {}
    public void replaceMediaItems(int from, int to, List<MediaItem> items) {}
    public MediaItem getMediaItemAt(int index) { return null; }
    public int getMediaItemCount() { return 0; }
    public void setAudioAttributes(AudioAttributes a, boolean handleFocus) {}
    public void clearVideoSurface() {}
    public void clearVideoSurface(Surface s) {}
    public void clearVideoSurfaceHolder(SurfaceHolder h) {}
    public void clearVideoSurfaceView(SurfaceView v) {}
    public void clearVideoTextureView(TextureView v) {}
    public void setVideoSurface(Surface s) {}
    public void setVideoSurfaceHolder(SurfaceHolder h) {}
    public void setVideoSurfaceView(SurfaceView v) {}
    public void setVideoTextureView(TextureView v) {}
}
