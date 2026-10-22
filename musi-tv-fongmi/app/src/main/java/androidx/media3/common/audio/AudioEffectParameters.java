package androidx.media3.common.audio;
public final class AudioEffectParameters {
    public static final AudioEffectParameters DEFAULT = new AudioEffectParameters(new int[0], new short[0], 0, 0, 0, 0, 0f, AudioChannelMode.AUTO, false);
    private final int[] frequencies; private final short[] levels; private final int boost; private final int preamp; private final int centerGain; private final int stability; private final float balance; private final int channelMode; private final boolean loudness;
    private AudioEffectParameters(int[] f, short[] l, int boost, int preamp, int centerGain, int stability, float balance, int channelMode, boolean loudness) { this.frequencies = f; this.levels = l; this.boost = boost; this.preamp = preamp; this.centerGain = centerGain; this.stability = stability; this.balance = balance; this.channelMode = channelMode; this.loudness = loudness; }
    public boolean hasEffect() { return false; }
    public boolean hasBands() { return levels.length > 0; }
    public boolean hasCenterGain(int channelCount) { return centerGain != 0; }
    public boolean isLoudnessEnabled() { return loudness; }
    public boolean shouldLimitOutput(int channelCount) { return false; }
    public int getBoostMillibels() { return boost; }
    public int getPreampMillibels() { return preamp; }
    public int getStability() { return stability; }
    public float getBalance() { return balance; }
    public int getChannelMode() { return channelMode; }
    public float getCenterGainFactor() { return centerGain / 1000f; }
    public short[] getEqualizerLevelsMillibels() { return levels; }
    public static boolean isCenterGainAvailable(int channelCount) { return channelCount >= 6; }
    public static final class Builder {
        public Builder setEqualizerBands(int[] frequencies, short[] levels) { return this; }
        public Builder setStability(float v) { return this; }
        public Builder setBoostMillibels(int v) { return this; }
        public Builder setPreampMillibels(int v) { return this; }
        public Builder setCenterGainMillibels(int v) { return this; }
        public Builder setBalance(float v) { return this; }
        public Builder setChannelMode(int v) { return this; }
        public Builder setLoudnessEnabled(boolean v) { return this; }
        public AudioEffectParameters build() { return DEFAULT; }
    }
}