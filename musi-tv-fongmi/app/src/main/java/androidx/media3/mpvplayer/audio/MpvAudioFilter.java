package androidx.media3.mpvplayer.audio;
public final class MpvAudioFilter {
    public static final MpvAudioFilter EMPTY = new MpvAudioFilter();
    public static final class Builder {
        public Builder addLoudnessNormalization(String id, double a, double b, double c) { return this; }
        public Builder addCompressor(String id, double threshold, double ratio, double makeup, double mix) { return this; }
        public Builder addVolume(String id, double v) { return this; }
        public Builder addEqualizer(String id, int freq, double gain) { return this; }
        public Builder addLimiter(String id, double v) { return this; }
        public Builder addRuntimeChannelMix(String id, float[][] mix) { return this; }
        public MpvAudioFilter build() { return new MpvAudioFilter(); }
    }
}