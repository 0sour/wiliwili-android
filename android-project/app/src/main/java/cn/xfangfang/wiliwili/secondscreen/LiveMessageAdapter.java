package cn.xfangfang.wiliwili.secondscreen;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

import cn.xfangfang.wiliwili.R;

public class LiveMessageAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<SecondScreenManager.LiveMsg> data;

    public LiveMessageAdapter(Context context, List<SecondScreenManager.LiveMsg> data) {
        this.inflater = LayoutInflater.from(context);
        this.data = data;
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public SecondScreenManager.LiveMsg getItem(int position) {
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
            view = inflater.inflate(R.layout.item_second_screen_live, parent, false);
            Holder holder = new Holder();
            holder.user = view.findViewById(R.id.live_user);
            holder.medal = view.findViewById(R.id.live_medal);
            holder.price = view.findViewById(R.id.live_price);
            holder.text = view.findViewById(R.id.live_text);
            view.setTag(holder);
        }

        Holder holder = (Holder) view.getTag();
        SecondScreenManager.LiveMsg message = data.get(position);
        holder.user.setText(message.user);
        holder.text.setText(message.text);

        boolean superChat = message.type == 1;
        if (superChat) {
            view.setBackgroundColor(safeColor(message.bg, SecondScreenTheme.accent));
            holder.user.setTextColor(Color.WHITE);
            holder.text.setTextColor(Color.WHITE);
            holder.medal.setTextColor(0xFFFFF0B0);
            holder.price.setTextColor(Color.WHITE);
            holder.price.setText("¥" + message.price);
            holder.price.setVisibility(View.VISIBLE);
        } else {
            view.setBackgroundColor(Color.TRANSPARENT);
            holder.user.setTextColor(SecondScreenTheme.name);
            // 侧边栏风格：正文用主题文字色，保证浅色背景下可读
            holder.text.setTextColor(SecondScreenTheme.text);
            holder.medal.setTextColor(SecondScreenTheme.accent);
            holder.price.setVisibility(View.GONE);
        }

        if (message.medal.isEmpty()) {
            holder.medal.setVisibility(View.GONE);
        } else {
            holder.medal.setText(message.medal);
            holder.medal.setVisibility(View.VISIBLE);
        }
        return view;
    }

    private static int safeColor(String value, int fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return Color.parseColor(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static class Holder {
        TextView user;
        TextView medal;
        TextView price;
        TextView text;
    }
}
