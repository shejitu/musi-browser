package androidx.media3.common.audio;
public final class AudioChannelMode {
    public static final int STEREO = 0, MONO = 1, REVERSE = 2, AUTO = 3;
    public static boolean canBalance(int mode) { return mode == STEREO || mode == MONO || mode == REVERSE; }
    public static boolean isAuto(int mode) { return mode == AUTO; }
    public static boolean isAvailable(int mode) { return true; }
    public static boolean isAvailable(int mode, int channelCount) { return true; }
    public static int resolve(int mode, int channelCount) { return mode; }
    public static int clamp(int mode) { return mode >= 0 && mode <= AUTO ? mode : AUTO; }
}