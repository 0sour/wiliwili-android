package cn.xfangfang.wiliwili.secondscreen;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.widget.TextView;

/**
 * Loads a Bilibili user face image and shows it as a circle inside the avatar
 * TextView. The colored initial remains as the fallback.
 */
final class AvatarLoader {
    private static final String SUFFIX = "@96w_96h";

    private AvatarLoader() {
    }

    static void load(String url, TextView target) {
        final String key = ImageLoader.key(url, SUFFIX);
        if (key.isEmpty()) {
            return;
        }
        target.setTag(key);
        ImageLoader.load(key, (loadedKey, bitmap) -> {
            if (!loadedKey.equals(target.getTag())) {
                return;
            }
            target.setText("");
            target.setBackground(new BitmapDrawable(target.getResources(), circle(bitmap)));
        });
    }

    private static Bitmap circle(Bitmap source) {
        int size = Math.min(source.getWidth(), source.getHeight());
        if (size <= 0) {
            return source;
        }
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        float left = (source.getWidth() - size) / 2f;
        float top = (source.getHeight() - size) / 2f;
        canvas.translate(-left, -top);
        canvas.drawCircle(left + size / 2f, top + size / 2f, size / 2f, paint);
        return output;
    }
}
