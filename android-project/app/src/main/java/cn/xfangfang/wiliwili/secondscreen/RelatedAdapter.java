package cn.xfangfang.wiliwili.secondscreen;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

import cn.xfangfang.wiliwili.R;

public class RelatedAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<SecondScreenManager.RelatedItem> data;
    private final float density;

    public RelatedAdapter(Context context, List<SecondScreenManager.RelatedItem> data) {
        this.inflater = LayoutInflater.from(context);
        this.data = data;
        this.density = context.getResources().getDisplayMetrics().density;
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public SecondScreenManager.RelatedItem getItem(int position) {
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
            view = inflater.inflate(R.layout.item_second_screen_related, parent, false);
            Holder holder = new Holder();
            holder.cover = view.findViewById(R.id.related_cover);
            holder.title = view.findViewById(R.id.related_title);
            holder.up = view.findViewById(R.id.related_up);
            holder.meta = view.findViewById(R.id.related_meta);
            holder.cover.setClipToOutline(true);
            view.setTag(holder);
        }
        Holder holder = (Holder) view.getTag();
        SecondScreenManager.RelatedItem item = data.get(position);
        holder.title.setText(item.title);
        holder.title.setTextColor(SecondScreenTheme.text);
        holder.up.setText(item.up);
        holder.up.setTextColor(SecondScreenTheme.name);
        holder.meta.setTextColor(SecondScreenTheme.textSecondary);

        GradientDrawable coverBackground = new GradientDrawable();
        coverBackground.setCornerRadius(6 * density);
        coverBackground.setColor(SecondScreenTheme.buttonBackground);
        holder.cover.setBackground(coverBackground);
        CoverLoader.load(item.cover, holder.cover);
        StringBuilder meta = new StringBuilder();
        if (item.duration > 0) {
            meta.append(formatTime(item.duration));
        }
        if (item.view > 0) {
            if (meta.length() > 0) {
                meta.append(" · ");
            }
            meta.append(formatCount(item.view)).append("播放");
        }
        holder.meta.setText(meta.toString());
        return view;
    }

    private static String formatTime(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, secs);
    }

    private static String formatCount(long value) {
        if (value >= 100000000L) {
            return String.format(Locale.US, "%.1f亿", value / 100000000.0);
        }
        if (value >= 10000L) {
            return String.format(Locale.US, "%.1f万", value / 10000.0);
        }
        return String.valueOf(value);
    }

    private static class Holder {
        ImageView cover;
        TextView title;
        TextView up;
        TextView meta;
    }
}
