package androidx.media3.common;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
public final class DolbyVisionOutputPolicy {
    public static final int AUTO = 0, OFF = 1, ASSUME_SUPPORTED = 2, ASSUME_UNSUPPORTED = 3;
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mode {}
}