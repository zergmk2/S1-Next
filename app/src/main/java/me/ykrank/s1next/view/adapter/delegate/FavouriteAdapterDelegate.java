package me.ykrank.s1next.view.adapter.delegate;

import android.content.Context;
import android.databinding.DataBindingUtil;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;

import me.ykrank.s1next.R;
import me.ykrank.s1next.data.api.model.Favourite;
import me.ykrank.s1next.databinding.ItemFavouriteBinding;
import me.ykrank.s1next.viewmodel.FavouriteViewModel;

public final class FavouriteAdapterDelegate extends BaseAdapterDelegate<Favourite, FavouriteAdapterDelegate.BindingViewHolder> {

    public FavouriteAdapterDelegate(Context context, int viewType) {
        super(context, viewType);
    }

    @NonNull
    @Override
    protected Class<Favourite> getTClass() {
        return Favourite.class;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent) {
        ItemFavouriteBinding binding = DataBindingUtil.inflate(mLayoutInflater,
                R.layout.item_favourite, parent, false);
        binding.setFavouriteViewModel(new FavouriteViewModel());

        return new BindingViewHolder(binding);
    }

    @Override
    public void onBindViewHolderData(Favourite favourite, int position, @NonNull BindingViewHolder holder) {
        ItemFavouriteBinding binding = holder.itemFavouriteBinding;
        binding.getFavouriteViewModel().favourite.set(favourite);
        binding.getFavouriteViewModel().setSubscription();
        binding.executePendingBindings();
    }

    static final class BindingViewHolder extends RecyclerView.ViewHolder {

        private final ItemFavouriteBinding itemFavouriteBinding;

        public BindingViewHolder(ItemFavouriteBinding itemFavouriteBinding) {
            super(itemFavouriteBinding.getRoot());

            this.itemFavouriteBinding = itemFavouriteBinding;
        }
    }
}
