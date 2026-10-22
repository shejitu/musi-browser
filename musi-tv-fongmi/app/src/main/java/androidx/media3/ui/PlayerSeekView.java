package androidx.media3.ui;
import android.content.Context;
import android.util.AttributeSet;
public class PlayerSeekView extends PlayerView {
    public PlayerSeekView(Context context) { super(context); }
    public PlayerSeekView(Context context, AttributeSet attrs) { super(context, attrs); }
    public PlayerSeekView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }
    public TimeBar getTimeBar() {
        android.view.View v = findViewById(R.id.exo_progress);
        return v instanceof TimeBar ? (TimeBar) v : null;
    }
}