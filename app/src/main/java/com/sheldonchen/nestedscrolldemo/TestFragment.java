package com.sheldonchen.nestedscrolldemo;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.sheldonchen.itemdecorations.decorations.LinearLayoutDivider;

/**
 * Created by cxd on 2018/6/14
 */

public class TestFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if(getView() == null) return;

        RecyclerView recyclerView = getView().findViewById(R.id.rv);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setNestedScrollingEnabled(false);
        recyclerView.addItemDecoration(new LinearLayoutDivider.Builder()
                .drawFirstDivider(true)
                .drawLastDivider(true)
                .setDividerColor(Color.parseColor("#888888"))
                .setStartPadding(15)
                .setDividerThickness(1)
                .build());
        recyclerView.setAdapter(new MainActivity.InnerAdapter());
    }
}
