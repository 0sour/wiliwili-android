package cn.xfangfang.wiliwili.secondscreen;

import android.widget.ImageView;

/**
 * Loads a 16:9 video cover for the related list.
 */
final class CoverLoader {
    private static final String SUFFIX = "@320w_180h";

    private CoverLoader() {
    }

    static void load(String url, ImageView target) {
        final String key = ImageLoader.key(url, SUFFIX);
        if (key.isEmpty()) {
            target.setTag(null);
            target.setImageDrawable(null);
            return;
        }
        target.setTag(key);
        ImageLoader.load(key, (loadedKey, bitmap) -> {
            if (!loadedKey.equals(target.getTag())) {
                return;
            }
            target.setImageBitmap(bitmap);
        });
    }
}
