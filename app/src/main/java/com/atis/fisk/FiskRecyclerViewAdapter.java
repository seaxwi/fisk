package com.atis.fisk;

import android.content.Context;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.widget.ImageViewCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class FiskRecyclerViewAdapter extends RecyclerView.Adapter<FiskRecyclerViewAdapter.ViewHolder> {
    // https://stackoverflow.com/questions/40587168/simple-android-grid-example-using-recyclerview-with-gridlayoutmanager-like-the/40587169#40587169

    private FishEntry[] fishEntries;
    private LayoutInflater mInflater;
    private ItemClickListener mClickListener;

    // data is passed into the constructor
    FiskRecyclerViewAdapter(Context context, FishEntry[] fishEntries) {
        this.mInflater = LayoutInflater.from(context);
        this.fishEntries = fishEntries;
    }

    // inflates the cell layout from xml when needed
    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.fish_card, parent, false);
        return new ViewHolder(view);
    }

    // binds the data to the TextView in each cell
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Context context = holder.fishCardImage.getContext();
        holder.fishCardImage.setImageDrawable(
                context.getResources().getDrawable(fishEntries[position].getResourceID()));
        if(fishEntries[position].getCount() > 0) {
            ImageViewCompat.setImageTintList( holder.fishCardImage,null);
            holder.fishCardTitle.setText(fishEntries[position].getName());
        }
        holder.fishCardCaught.setText(
                context.getString(R.string.caught_number, fishEntries[position].getCount()));

    }

    // total number of cells
    @Override
    public int getItemCount() {
        return fishEntries.length;
    }


    // stores and recycles views as they are scrolled off screen
    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        ImageView fishCardImage;
        TextView fishCardTitle;
        TextView fishCardCaught;

        ViewHolder(View fishCard) {
            super(fishCard);
            fishCardImage = fishCard.findViewById(R.id.fish_card_image);
            fishCardTitle = fishCard.findViewById(R.id.fish_card_title);
            fishCardCaught = fishCard.findViewById(R.id.fish_card_count);
            fishCard.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (mClickListener != null) mClickListener.onItemClick(view, getAdapterPosition());
        }
    }

    // convenience method for getting data at click position
    FishEntry getItem(int id) {
        return fishEntries[id];
    }

    // allows clicks events to be caught
    void setClickListener(ItemClickListener itemClickListener) {
        this.mClickListener = itemClickListener;
    }

    // parent activity will implement this method to respond to click events
    public interface ItemClickListener {
        void onItemClick(View view, int position);
    }
}