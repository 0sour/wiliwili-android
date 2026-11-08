package cn.xfangfang.wiliwili.secondscreen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

import cn.xfangfang.wiliwili.R;

public class CommentAdapter extends BaseAdapter {
    private static final int[] AVATAR_COLORS = {
            0xFF9C88FF, 0xFF7ED6A5, 0xFFF6A5C0, 0xFF6EC6FF,
            0xFFFFC46B, 0xFFB39DDB, 0xFF80CBC4, 0xFFEF9A9A
    };

    private final LayoutInflater inflater;
    private final List<SecondScreenManager.Comment> data;

    public CommentAdapter(Context context, List<SecondScreenManager.Comment> data) {
        this.inflater = LayoutInflater.from(context);
        this.data = data;
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public SecondScreenManager.Comment getItem(int position) {
        return data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = inflater.inflate(R.layout.item_second_screen_comment, parent, false);
            Holder holder = new Holder();
            holder.avatar = view.findViewById(R.id.comment_avatar);
            holder.name = view.findViewById(R.id.comment_name);
            holder.time = view.findViewById(R.id.comment_time);
            holder.like = view.findViewById(R.id.comment_like);
            holder.text = view.findViewById(R.id.comment_text);
            view.setTag(holder);
        }

        Holder holder = (Holder) view.getTag();
        SecondScreenManager.Comment comment = data.get(position);

        String prefix = comment.top ? "置顶 " : (comment.up ? "UP " : "");
        holder.name.setText(prefix + comment.name);
        holder.name.setTextColor(comment.top ? 0xFFF59A23
                : (comment.up ? SecondScreenTheme.accent : SecondScreenTheme.name));
        holder.time.setText(comment.time);
        holder.time.setTextColor(SecondScreenTheme.textSecondary);
        holder.like.setText(comment.like > 0 ? "赞 " + comment.like : "");
        holder.like.setTextColor(SecondScreenTheme.textSecondary);
        holder.text.setText(buildCommentText(comment, holder.text));
        holder.text.setTextColor(SecondScreenTheme.text);

        String initial = "?";
        if (!comment.name.isEmpty()) {
            int codePoint = comment.name.codePointAt(0);
            initial = new String(Character.toChars(codePoint));
        }
        holder.avatar.setText(initial);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        int colorIndex = Math.abs(comment.name.hashCode()) % AVATAR_COLORS.length;
        circle.setColor(AVATAR_COLORS[colorIndex]);
        holder.avatar.setBackground(circle);
        if (!comment.avatar.isEmpty()) {
            AvatarLoader.load(comment.avatar, holder.avatar);
        } else {
            holder.avatar.setTag(null);
        }
        return view;
    }

    private static class Holder {
        TextView avatar;
        TextView name;
        TextView time;
        TextView like;
        TextView text;
    }

    /** Replace "[emote]" tokens with inline images; unknown tokens stay as text. */
    private static CharSequence buildCommentText(SecondScreenManager.Comment comment, TextView target) {
        String message = comment.text;
        if (message.isEmpty() || comment.emotes.isEmpty()) {
            return message;
        }
        SpannableStringBuilder builder = new SpannableStringBuilder();
        int index = 0;
        while (index < message.length()) {
            int open = message.indexOf('[', index);
            if (open < 0) {
                builder.append(message, index, message.length());
                break;
            }
            int close = message.indexOf(']', open + 1);
            if (close < 0) {
                builder.append(message, index, message.length());
                break;
            }
            builder.append(message, index, open);
            String token = message.substring(open, close + 1);
            SecondScreenManager.Emote emote = comment.emotes.get(token);
            if (emote == null) {
                builder.append(token);
            } else {
                int size = Math.round(target.getTextSize() * 1.25f * Math.max(1, emote.size));
                EmoteDrawable drawable = new EmoteDrawable(size);
                int start = builder.length();
                builder.append('\uFFFC');
                builder.setSpan(new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM),
                        start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                final String key = ImageLoader.key(emote.url, null);
                if (!key.isEmpty()) {
                    ImageLoader.load(key, (loadedKey, bitmap) -> {
                        if (loadedKey.equals(key)) {
                            drawable.setBitmap(bitmap);
                            target.invalidate();
                        }
                    });
                }
            }
            index = close + 1;
        }
        return builder;
    }

    private static final class EmoteDrawable extends Drawable {
        private final int size;
        private Bitmap bitmap;

        EmoteDrawable(int size) {
            this.size = size;
        }

        void setBitmap(Bitmap value) {
            bitmap = value;
            invalidateSelf();
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void draw(Canvas canvas) {
            if (bitmap == null) {
                return;
            }
            canvas.drawBitmap(bitmap, null, getBounds(), null);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
