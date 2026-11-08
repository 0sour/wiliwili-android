package cn.xfangfang.wiliwili.secondscreen;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

import cn.xfangfang.wiliwili.R;

public class EpisodeAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<SecondScreenManager.EpisodeItem> data;

    public EpisodeAdapter(Context context, List<SecondScreenManager.EpisodeItem> data) {
        this.inflater = LayoutInflater.from(context);
        this.data = data;
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public SecondScreenManager.EpisodeItem getItem(int position) {
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
            view = inflater.inflate(R.layout.item_second_screen_episode, parent, false);
            Holder holder = new Holder();
            holder.title = view.findViewById(R.id.episode_title);
            holder.subtitle = view.findViewById(R.id.episode_subtitle);
            holder.duration = view.findViewById(R.id.episode_duration);
            view.setTag(holder);
        }
        Holder holder = (Holder) view.getTag();
        SecondScreenManager.EpisodeItem item = data.get(position);

        holder.title.setText(item.title);
        if (item.playable) {
            holder.title.setTextColor(SecondScreenTheme.text);
            String subtitle = item.subtitle;
            if (!subtitle.isEmpty()
                    && (subtitle.equals(item.title) || item.title.endsWith(subtitle))) {
                subtitle = "";
            }
            holder.subtitle.setText(subtitle);
            holder.subtitle.setTextColor(SecondScreenTheme.textSecondary);
            holder.subtitle.setVisibility(subtitle.isEmpty() ? View.GONE : View.VISIBLE);
            holder.duration.setText(item.duration > 0 ? formatTime(item.duration) : "");
            holder.duration.setTextColor(SecondScreenTheme.textSecondary);
            holder.duration.setVisibility(item.duration > 0 ? View.VISIBLE : View.GONE);
        } else {
            // Section header (e.g. "正片"), not selectable
            holder.title.setTextColor(SecondScreenTheme.accent);
            holder.subtitle.setVisibility(View.GONE);
            holder.duration.setVisibility(View.GONE);
        }
        return view;
    }

    private static String formatTime(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, secs);
    }

    private static class Holder {
        TextView title;
        TextView subtitle;
        TextView duration;
    }
}
