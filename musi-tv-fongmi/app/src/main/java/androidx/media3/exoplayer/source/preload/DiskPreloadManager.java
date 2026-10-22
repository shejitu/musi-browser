package androidx.media3.exoplayer.source.preload;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PriorityTaskManager;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager;
import androidx.media3.database.DatabaseProvider;
import androidx.media3.datasource.cache.CacheDataSource;
public final class DiskPreloadManager {
    public static final class Options {
        public static final class Builder {
            public Builder setDurationMs(long v) { return this; }
            public Builder setMaxThreads(int v) { return this; }
            public Options build() { return new Options(); }
        }
    }
    public static final class Builder {
        public Builder(androidx.media3.datasource.cache.Cache cache, DataSource.Factory factory, RenderersFactory renderers) {}
        public Builder setPriorityTaskManager(PriorityTaskManager p) { return this; }
        public DiskPreloadManager build() { return new DiskPreloadManager(); }
    }
    public void start(Object player, MediaItem item, Options options) {}
    public void release() {}
}