package com.example.bruxismdetector;// CalendarPagerAdapter.java
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.bruxismdetector.bruxism_grapher2.SummaryReader;

import java.util.List;

public class CalendarPagerAdapter extends FragmentStateAdapter {
    private final List<SummaryReader.SummaryMonth> months;

    public CalendarPagerAdapter(@NonNull FragmentActivity fa, List<SummaryReader.SummaryMonth> months) {
        super(fa);
        this.months = months;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        SummaryReader.SummaryMonth sm = months.get(position);
        return CalendarMonthFragment.newInstance(sm.getYear(), sm.getMonth());
    }

    @Override
    public int getItemCount() {
        return months.size();
    }
}