package androidx.media3.common.audio;
public final class AudioChannelMix {
    public static float[][] createMonoMix(int channelCount) { return identity(channelCount); }
    public static float[][] createStereoMix(int channelCount, boolean reverse) { return identity(channelCount); }
    public static float[][] createFrontCenterGainMix(int channelCount, float gain) { return identity(channelCount); }
    public static float[][] createFrontBalanceMix(int channelCount, float balance) { return identity(channelCount); }
    public static float[][] compose(float[][] a, float[][] b) { return b; }
    private static float[][] identity(int n) { float[][] m = new float[n][n]; for (int i = 0; i < n; i++) m[i][i] = 1.0f; return m; }
}