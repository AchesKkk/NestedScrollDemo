package com.sheldonchen.nestedscrolldemo;

import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.sheldonchen.itemdecorations.decorations.LinearLayoutDivider;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        TabLayout tabLayout = findViewById(R.id.tabs);
//        ViewPager viewPager = findViewById(R.id.viewpager);
//        viewPager.setAdapter(new InnerFragmentPagerAdapter(getSupportFragmentManager()));
//        tabLayout.setupWithViewPager(viewPager);
//        viewPager.setCurrentItem(0);

//        RecyclerView recyclerView = findViewById(R.id.layout_scroll_child);
//        recyclerView.setLayoutManager(new LinearLayoutManager(this));
//        recyclerView.setHasFixedSize(true);
//        recyclerView.setNestedScrollingEnabled(false);
//        recyclerView.addItemDecoration(new LinearLayoutDivider.Builder()
//                .drawFirstDivider(true)
//                .drawLastDivider(true)
//                .setDividerColor(Color.parseColor("#888888"))
//                .setStartPadding(15)
//                .setDividerThickness(1)
//                .build());
//        recyclerView.setAdapter(new InnerAdapter());

//        ListView listView = findViewById(R.id.layout_scroll_child);
//        listView.setAdapter(new ListViewAdapter());

//        WebView webView = findViewById(R.id.layout_scroll_child);
//        webView.loadUrl("https://portal.3g.qq.com/?aid=index&g_ut=3");
//
//        findViewById(R.id.tv).setOnClickListener(new View.OnClickListener() {
//
//            @Override
//            public void onClick(View v) {
//                Toast.makeText(MainActivity.this, "toast!", Toast.LENGTH_SHORT).show();
//            }
//        });

        RecyclerView recyclerView = findViewById(R.id.rv);

        NestedScrollLayout nestedScrollLayout = findViewById(R.id.nested);
        nestedScrollLayout.setOnChildScrollCallback(new NestedScrollLayout.SectionPinnedFlingHelper(recyclerView));

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        recyclerView.setNestedScrollingEnabled(false);
        recyclerView.addItemDecoration(new LinearLayoutDivider.Builder()
                .drawFirstDivider(true)
                .drawLastDivider(true)
                .setDividerColor(Color.parseColor("#888888"))
                .setStartPadding(15)
                .setDividerThickness(1)
                .build());
        recyclerView.setAdapter(new InnerAdapter());
    }

    private static final class InnerFragmentPagerAdapter extends FragmentPagerAdapter {

        private static final String[] PAGE_TITLES = {"知乎日报", "干货"};

        InnerFragmentPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                default:
                    return new TestFragment();
                case 1:
                    return new TestFragment();
            }
        }

        @Override
        public int getCount() {
            return PAGE_TITLES.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if(position >= 0 && position < PAGE_TITLES.length) {
                return PAGE_TITLES[position];
            }
            return null;
        }
    }

    private static class ListViewAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return 50;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if(convertView == null) {
                holder = new ViewHolder();
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item, parent, false);
                holder.tv = convertView.findViewById(R.id.tv);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            holder.tv.setText("item: " + position);
            return convertView;
        }

        private static class ViewHolder {
            TextView tv;
        }

    }


    public static class InnerAdapter extends RecyclerView.Adapter<InnerViewHolder> {

        @NonNull
        @Override
        public InnerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new InnerViewHolder(
                    LayoutInflater.from(parent.getContext()).inflate(R.layout.item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull InnerViewHolder holder, int position) {
            holder.tv.setText("item: " + position);
        }

        @Override
        public int getItemCount() {
            return 50;
        }
    }

    private static class InnerViewHolder extends RecyclerView.ViewHolder {
        TextView tv;

        public InnerViewHolder(View itemView) {
            super(itemView);
            tv = itemView.findViewById(R.id.tv);
        }
    }
}
