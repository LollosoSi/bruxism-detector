package com.example.bruxismdetector;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ChartsPagerAdapter extends FragmentStateAdapter {

    public ChartsPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Log.i("Pos",position+"");
        switch (position) {
            default:
            case 0:
                return new HeatMapFragment();
            case 1:
                return new SummaryChartFragment();
            case 2:
                return new CorrelationsFragment();


        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
